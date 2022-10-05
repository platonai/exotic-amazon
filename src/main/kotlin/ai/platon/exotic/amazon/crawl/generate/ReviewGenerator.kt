package ai.platon.exotic.amazon.crawl.generate

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.collect.queue.AbstractLoadingQueue
import ai.platon.pulsar.common.message.LoadStatusFormatter
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.context.support.AbstractPulsarContext
import ai.platon.pulsar.crawl.DefaultLoadEventHandler
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.crawl.common.url.CompletableListenableHyperlink
import ai.platon.pulsar.crawl.common.url.ListenableHyperlink
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.exotic.amazon.tools.common.AmazonUtils
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.amazon.tools.common.AmazonItemPageProcessor
import ai.platon.exotic.amazon.tools.common.AmazonNavigationProcessor
import ai.platon.exotic.amazon.tools.common.AmazonReviewIndexPageProcessor
import ai.platon.scent.boot.autoconfigure.persist.TrackedUrlRepository
import ai.platon.scent.common.message.ScentMiscMessageWriter
import ai.platon.exotic.common.diffusing.AbstractDiffusingCrawler
import ai.platon.exotic.common.diffusing.config.DiffusingCrawlerConfig
import ai.platon.scent.mongo.v1.TrackedUrl
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.concurrent.ConcurrentSkipListSet

class SequentialFatLink(
    val templateUrl: String,
    val numTotalPages: Int,
    val numFetchedPages: Int
) {
    fun toTrackedUrl(): TrackedUrl {
        return TrackedUrl(url = templateUrl).also {
            it.numTotalTailLinks = numTotalPages
            it.numFetchedTailLinks = numFetchedPages
        }
    }

    override fun toString(): String {
        return "$numFetchedPages | $numTotalPages | $templateUrl"
    }

    companion object {
        fun parse(s: String): SequentialFatLink? {
            val parts = s.split("|").takeIf { it.size == 4 } ?: return null

            val numFetchedPages = parts[0].trim().toIntOrNull() ?: return null
            val numTotalPages = parts[1].trim().toIntOrNull() ?: return null
            val url = parts[2].trim().takeIf { UrlUtils.isValidUrl(it) } ?: return null

            return SequentialFatLink(url, numTotalPages, numFetchedPages)
        }
    }
}

class ReviewGenerator(
    config: DiffusingCrawlerConfig,
    session: ScentSession,
    globalCacheFactory: GlobalCacheFactory,
    private val trackedUrlRepository: TrackedUrlRepository
) : AbstractDiffusingCrawler(config, session, globalCacheFactory) {

    private val log = LoggerFactory.getLogger(ReviewGenerator::class.java)

    private val context get() = session.context as AbstractPulsarContext
    private val messageWriter = context.getBean<ScentMiscMessageWriter>()
    private val loadedUrlDirectory = AppPaths.DATA_DIR.resolve("loaded")
    private val fetchedUrls = ConcurrentSkipListSet<TrackedUrl>()
    private val completedPrimaryUrls = ConcurrentSkipListSet<TrackedUrl>()
    private var numNoNavigations = 0

    private val caPrimaryReviewUrls = ConcurrentSkipListSet<TrackedUrl>()
    private val usPrimaryReviewUrls = ConcurrentSkipListSet<TrackedUrl>()
    private val primaryReviewUrls = ConcurrentSkipListSet<TrackedUrl>()

    override val label: String get() = config.label

    override val indexPageProcessor = AmazonReviewIndexPageProcessor(config, session).apply {
        dbCheck = true
        storeContent = false
        minPageSize = 0
    }
    override val navigationProcessor = AmazonNavigationProcessor(config, indexPageProcessor, session)
    override val itemPageProcessor = AmazonItemPageProcessor(config, session)

    private var currentSink: MutableCollection<UrlAware>? = null

    init {
        if (Files.exists(loadedUrlDirectory)) {
            Files.list(loadedUrlDirectory).forEach {
                Files.readAllLines(it).asSequence().filter { it.startsWith("http") }
                    .mapTo(fetchedUrls) { TrackedUrl(it) }
            }
        }
    }

    override fun generateTo(sink: MutableCollection<UrlAware>) {
        currentSink = sink

        generateMissingSecondaryReviewIndexLinksTo(primaryReviewUrls, sink)

        trackedUrlRepository.saveAll(completedPrimaryUrls.filter { it.id == null })
    }

    override fun onAfterHtmlParse(page: WebPage, document: FeaturedDocument) {
        // checkIfReviewLinksAreComplete(page, document)
    }

    private fun filterNotComplete(url: String): String? {
        return url.takeIf { TrackedUrl(url.substringBefore("/ref=")) !in completedPrimaryUrls }
    }

    private fun updatePrimaryReviewIndexLinks(sink: MutableCollection<UrlAware>) {
        val fetchUrls = ClusterTools.partition(caPrimaryReviewUrls + usPrimaryReviewUrls)
        require(indexPageProcessor.dbCheck)
        require(indexPageProcessor.storeContent)
        fetchUrls.asSequence()
            .mapNotNull { url ->
                indexPageProcessor.createHyperlink(url.url)
                    ?.apply { args = session.options("$args ${url.args}").toString() }
            }
            .filterIsInstance<ListenableHyperlink>()
            .onEach {
                it.eventHandler.loadEventHandler.onAfterHtmlParse.addLast { page, document ->
                    calculateAndReportPrimaryReviewLink(page, document)
                }
            }
            .toCollection(sink)
    }

    private fun updatePrimaryReviewIndexLinks2() {
        var count = 0
        val options = session.options().apply { nJitRetry = 3; ignoreFailure = true }
        caPrimaryReviewUrls.toList().parallelStream().forEach {
            count += calculateAndReportPrimaryReviewLink(it, options)
        }
        log.info("Updated total $count primary review indexes for ca")

        count = 0
        usPrimaryReviewUrls.toList().parallelStream().forEach {
            count += calculateAndReportPrimaryReviewLink(it, options)
        }
        log.info("Updated total $count primary review indexes for us")
    }

    private fun calculateAndReportPrimaryReviewLink(url: TrackedUrl, options: LoadOptions): Int {
        var page = session.load(url.url, options)
        var document: FeaturedDocument

        val reviewRatingCountSelector = indexPageProcessor.reviewRatingCountSelector
        var maxTry = 1
        var reviewString: String?
        do {
            document = session.parse(page)
            reviewString = document.selectFirstOrNull(reviewRatingCountSelector)?.text()
            if (page.content == null || !page.protocolStatus.isSuccess) {
                page = session.load(url.url, session.options("-i 1s -storeContent true -parse -ignoreFailure"))
            }
        } while (reviewString == null && --maxTry > 0)

        return calculateAndReportPrimaryReviewLink(page, document)
    }

    private fun calculateAndReportPrimaryReviewLink(page: WebPage, document: FeaturedDocument): Int {
        val reviewRatingCountSelector = indexPageProcessor.reviewRatingCountSelector
        val reviewString = document.selectFirstOrNull(reviewRatingCountSelector)?.text()
        val url = page.url
        var args = ""
        if (reviewString != null) {
            val totalReviews = Strings.getLastInteger(reviewString, 0)
            if (totalReviews > 0) {
                args = " -totalReviews $totalReviews"
            } else {
                log.warn("{}", LoadStatusFormatter(page, prefix = "No total reviews"))
            }
        } else {
            log.warn("{}", LoadStatusFormatter(page, prefix = "No reviews string"))
        }

        args = args.trim()
        val updated = !page.args.contains(args)
        if (updated) {
            args += " -updated"
        }

        val ident = if (url.contains(".ca")) "ca" else "us"
        messageWriter.reportPrimaryReviewLink("$url $args", ident)

        return if (updated) 1 else 0
    }

    private fun generateSecondaryReviewIndexLinksTo(sink: MutableCollection<UrlAware>) {
        generateSecondaryReviewIndexLinksTo(usPrimaryReviewUrls, sink)
        generateSecondaryReviewIndexLinksTo(caPrimaryReviewUrls, sink)
    }

    private fun generateSecondaryReviewIndexLinksTo(links: Collection<TrackedUrl>, sink: MutableCollection<UrlAware>) {
        val partitionedUrls = links.filter { it !in completedPrimaryUrls }.let { ClusterTools.partition(it) }

        if (ClusterTools.isSingleInstanceMode()) {
            log.info("Currently running on single instance mode, processing all primary review urls without partition")
            if (partitionedUrls.size != links.size) {
                log.warn("Partition should be disabled")
            }
        }

        require(indexPageProcessor.dbCheck)
        require(indexPageProcessor.storeContent)
        // check all the generated secondary urls from this link are fetched
        // checkIfReviewLinksAreComplete(partitionedUrls)

        val unfilteredUrls = partitionedUrls.flatMap { url ->
            val totalPages = AmazonUtils.calculateTotalPages(url.args)

            /**
             * create a secondary review index link
             * */
//            indexPageProcessor.createSecondaryReviewIndexLinks(url.url, totalPages)
            val pageNumbers = IntRange(3, totalPages)
            indexPageProcessor.createSecondaryReviewIndexLinks(url.url, pageNumbers)
        }
        val filteredUrls = unfilteredUrls.filterNot { TrackedUrl(it.url) in fetchedUrls }

        filteredUrls.filterIsInstance<ListenableHyperlink>().forEach {
            val eventHandler = it.eventHandler.loadEventHandler as DefaultLoadEventHandler
            eventHandler.onFilter.addLast { filterNotComplete(it) }
            eventHandler.onAfterHtmlParse.addLast { page, document -> checkIfFatLinkIsComplete(page, document) }
        }

        filteredUrls.sortedBy { it.order }.groupBy { it.order }.forEach { (_, urls) ->
            sink.addAll(urls.shuffled())
        }

        log.info(
            "Partitioned urls: {}, unfiltered urls: {}, filtered urls: {}, sink size: {}, " +
                    "completed: {}, fetched: {}",
            partitionedUrls.size, unfilteredUrls.size, filteredUrls.size, sink.size,
            completedPrimaryUrls.size, fetchedUrls.size
        )
    }

    private fun generateMissingSecondaryReviewIndexLinksTo(
        links: Collection<TrackedUrl>,
        sink: MutableCollection<UrlAware>
    ) {
        indexPageProcessor.dbCheck = true
        indexPageProcessor.storeContent = true
        indexPageProcessor.minPageSize = 2_000_000

        ClusterTools.partition(links).flatMap { url ->
            val a = url.numFetchedTailLinks.coerceAtLeast(1)
            val b = url.numTotalTailLinks.coerceAtMost(1)
            indexPageProcessor.createSecondaryReviewIndexLinks(url.url, IntRange(a, b))
        }.filterIsInstance<CompletableListenableHyperlink<WebPage>>().onEach {
            val loadEventHandler = it.eventHandler.loadEventHandler as DefaultLoadEventHandler
            loadEventHandler.onAfterHtmlParse.addLast { page, document -> checkIfFatLinkIsComplete(page, document) }
        }.sortedBy { it.order }.groupBy { it.order }.forEach { (_, urls) ->
            urls.shuffled().toCollection(sink)
        }
    }

    private fun checkIfFatLinkIsComplete(urls: List<TrackedUrl>) {
        urls.filter { it !in completedPrimaryUrls }.parallelStream().forEach urlLoop@{ url ->
            val lastPageNo = AmazonUtils.calculateTotalPages(url.args)
            if (lastPageNo == 1) {
                // The primary review page
                trackCompletedPrimaryUrls(url, "Only primary page")
                return@urlLoop
            }

            var isEmptyReviewPage = false
            var emptyPageNo = 0
            listOf(10, 20, 30, 50, 100, lastPageNo).sorted().forEach { i ->
                if (!isEmptyReviewPage) {
                    isEmptyReviewPage = checkIfReviewPageIsEmpty(url, i, lastPageNo)
                    if (isEmptyReviewPage) {
                        emptyPageNo = i
                    }
                }
            }

            if (isEmptyReviewPage) {
                log.info("Completed at $emptyPageNo/$lastPageNo | {} -emptyPageNo $emptyPageNo", url.url)
            } else {
                log.info("Not complete $lastPageNo | {}", url)
            }
        }
    }

    private fun checkIfReviewPageIsEmpty(url: TrackedUrl, pageNo: Int, lastPageNo: Int): Boolean {
        val hyperlink = indexPageProcessor.createSecondaryReviewIndexLink(url.url, pageNo)
        if (hyperlink == null) {
            log.warn("Can not create hyperlink from url with page $pageNo | {}", url.url)
            return false
        }

        var isEmptyReviewPage = false
        // the last page is already fetched
        val page = session.getOrNull(hyperlink.url)
        if (page != null) {
            if (pageNo == lastPageNo) {
                // the last page is fetched
                isEmptyReviewPage = true
                if (pageNo > 2) {
                    val last2ndPage = indexPageProcessor.createSecondaryReviewIndexLink(url.url, pageNo - 1)
                    if (last2ndPage != null && session.exists(last2ndPage.url)) {
                        trackCompletedPrimaryUrls(url, "Last page fetched")
                    } else {
                        indexPageProcessor.createSecondaryReviewIndexLinks(url.url, pageNo).forEach {
                            messageWriter.reportGeneratedReviewLinks(it.url)
                        }
                        // trackCompletedPrimaryUrls(url, "Suspect")
                    }
                }
            } else {
                isEmptyReviewPage = checkIfFatLinkIsComplete(page, session.parse(page))
            }
        }

        return isEmptyReviewPage
    }

    private fun checkIfFatLinkIsComplete(page: WebPage, document: FeaturedDocument): Boolean {
        // ensure this is a secondary review link
        if (!page.url.contains("pageNumber=")) {
            return false
        }

        val path = loadedUrlDirectory.resolve("loaded_review_index_links.txt")
        messageWriter.reportLoadedReviewIndexLinks(document.baseUri, path)
        var completed = false
        val navigation = indexPageProcessor.createSecondaryReviewLinksFromPagination(page, document)
        if (navigation == null) {
            // no navigation in the page, all review pages before this page are already fetched
            completed = true
            val normalizedPrimaryUrl = page.url.substringBefore("/ref=")
            (currentSink as? AbstractLoadingQueue)?.removeIf { it.url.startsWith(normalizedPrimaryUrl) }
            trackCompletedPrimaryUrls(TrackedUrl(page.url), "No navigation")
        }
        return completed
    }

    private fun trackCompletedPrimaryUrls(url: TrackedUrl, message: String) {
        if (completedPrimaryUrls.add(url)) {
            log.info("{}. Completed | {} | {}", numNoNavigations, message, url.url)
            if (numNoNavigations++ % 100 == 0) {
                val newItems = completedPrimaryUrls.filter { it.id == null }
                if (newItems.isNotEmpty()) {
                    log.info("Saving {}/{} completed primary urls", newItems.size, completedPrimaryUrls.size)
                    trackedUrlRepository.saveAll(newItems)
                }
            }
        }
    }
}
