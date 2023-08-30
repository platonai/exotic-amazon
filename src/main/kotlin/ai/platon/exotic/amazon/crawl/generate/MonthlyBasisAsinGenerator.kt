package ai.platon.exotic.amazon.crawl.generate

import ai.platon.exotic.amazon.crawl.core.PATH_FETCHED_BEST_SELLER_URLS
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.pulsar.browser.common.BlockRule
import ai.platon.pulsar.browser.common.InteractSettings
import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.collect.UrlFeederHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.UrlFeeder
import ai.platon.pulsar.common.collect.collector.UrlCacheCollector
import ai.platon.pulsar.common.collect.queue.AbstractLoadingQueue
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.context.support.AbstractPulsarContext
import ai.platon.pulsar.crawl.common.URLUtil
import ai.platon.pulsar.crawl.common.url.ListenableHyperlink
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.gora.generated.GWebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.WebDbLongTimeTask
import ai.platon.scent.crawl.SeedProperties
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import java.net.MalformedURLException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.*
import java.util.*
import kotlin.jvm.Throws

/**
 * Generate asin tasks (fetching product pages) every day.
 * The asin links are extracted and updated from bestseller pages every day, and all them will be fetched in a month.
 * */
class MonthlyBasisAsinGenerator(
    val session: ScentSession,
    val monthValue: Int,
    val dayOfMonth: Int
) {
    companion object {
        private val logger = getLogger(MonthlyBasisAsinGenerator::class)

        private val isDev get() = ClusterTools.isDevInstance()

        private var generator: MonthlyBasisAsinGenerator? = null

        private lateinit var urlFeederHelper: UrlFeederHelper

        private lateinit var lastUrlLoader: ExternalUrlLoader

        private var asinCollector: UrlCacheCollector? = null

        private var reviewCollector: UrlCacheCollector? = null

        var testMode = false
        var injectAllAsinUrls = false

        var minAsinTasks = if (isDev) 0 else 200
        var maxAsinTasks = if (isDev) 200 else Int.MAX_VALUE

        /**
         * Should create a new generator every day.
         * */
        @Synchronized
        fun getOrCreate(
            session: ScentSession,
            urlLoader: ExternalUrlLoader,
            urlFeeder: UrlFeeder
        ): MonthlyBasisAsinGenerator {
            val monthValue = MonthDay.now().monthValue
            val dayOfMonth = MonthDay.now().dayOfMonth

            lastUrlLoader = urlLoader
            urlFeederHelper = UrlFeederHelper(urlFeeder)

            val oldGenerator = generator
            val isNewCollector = generator?.dayOfMonth != dayOfMonth || testMode
            if (isNewCollector) {
                asinCollector = null
                reviewCollector = null
                generator = MonthlyBasisAsinGenerator(session, monthValue, dayOfMonth)
            }

            if (isNewCollector) {
                oldGenerator?.dailyAsinTaskPath?.let { Files.deleteIfExists(it) }
                oldGenerator?.externalClear()
            }

            getOrCreateCollectors()

            return generator!!
        }

        /**
         * Check if this host is the supervisor node, only the supervisor node generates the tasks.
         * All nodes take turns serving as the supervisor.
         * */
        fun isSupervisor(): Boolean {
            if (isDev) {
                return true
            }

            val dayOfMonth = LocalDate.now().dayOfMonth
            val mod = dayOfMonth % ClusterTools.crawlerCount
            if (ClusterTools.instancePartition != mod) {
                logger.info("Do not generate asins, supervisor is crawl{}", mod)
                return false
            }

            return true
        }

        private fun getOrCreateCollectors() {
            if (asinCollector == null) {
                asinCollector = createCollector(PredefinedTask.ASIN, lastUrlLoader)
            }

            if (reviewCollector == null) {
                reviewCollector = createCollector(PredefinedTask.REVIEW, lastUrlLoader)
            }
        }

        private fun createCollector(task: PredefinedTask, urlLoader: ExternalUrlLoader): UrlCacheCollector {
            val priority = task.priority.value
            urlFeederHelper.remove(task.name)

            logger.info("Creating collector for {} with url loader {}", task.name, urlLoader::class)

            return urlFeederHelper.create(task.name, priority, urlLoader).also {
                it.labels.add(task.name)
            }
        }
    }

    private val context get() = session.context as AbstractPulsarContext
    private val isActive get() = context.isActive
    private val webDb get() = context.webDb

    // The leaf categories of bestsellers
    private val bestSellerResource = "sites/amazon/crawl/inject/seeds/category/bestsellers/leaf-categories.txt"
    private val propertiesResource = "sites/amazon/crawl/inject/seeds/category/bestsellers/seeds.properties"
    private val normalizer = AsinUrlNormalizer()
    private val blockingRule = BlockRule()
    private val expires = PredefinedTask.ASIN.expires
    private val deadTime = PredefinedTask.ASIN.deadTime()
    private val taskTime = DateTimes.startOfDay()
    private val taskId = DateTimes.startOfDay().toString()

    // A temporary file that holds the generated urls
    private val dailyAsinTaskPath
        get() = AppPaths.REPORT_DIR.resolve("generate/asin/$monthValue/$dayOfMonth.txt")

    // Keep the bestseller pages temporary to extract asin urls later
    private val relevantBestSellers = mutableMapOf<String, WebPage>()

    // The minimal interval to check bestseller pages in the database
    private val zgbsMinimalCheckInterval = Duration.ofMinutes(10)

    // The next time to check bestseller pages in the database
    private var zgbsNextCheckTime = Instant.now()

    private var generatedCount = 0
    private var averageVividLinkCount = 100
    private var collectedCount = 0

    val asinCollector: UrlCacheCollector?
        get() = Companion.asinCollector

    val reviewCollector: UrlCacheCollector?
        get() = Companion.reviewCollector

    fun generate(): Queue<UrlAware> {
        if (!isActive) {
            return LinkedList()
        }

        // before or after supervisor checking?
        getOrCreateCollectors()

        if (!isSupervisor()) {
            return LinkedList()
        }

        val collector = asinCollector
        if (collector == null) {
            logger.warn("Asin collector is not created")
            return LinkedList()
        }

        val queue = collector.urlCache.nonReentrantQueue as AbstractLoadingQueue
        val estimatedSize = queue.size + queue.estimatedExternalSize
        if (estimatedSize > minAsinTasks) {
            logger.info("Still {}(cached: {} + external: {}) asin tasks in seed cache, nothing to generate",
                estimatedSize, queue.size, queue.estimatedExternalSize)
            return queue
        } else {
            logger.info("Less than {} asin tasks, total {}(cached: {} + external: {}), re-generating ...",
                minAsinTasks, estimatedSize, queue.size, queue.estimatedExternalSize)
        }

        val startTime = Instant.now()

        collectedCount = collector.collectedCount
        generateTo(queue)

        val elapsedTime = DateTimes.elapsedTime(startTime)

        val estimatedSize2 = queue.size + queue.estimatedExternalSize
        logger.info("Generated {}/{} asin urls in {} to queue {}",
            queue.size, estimatedSize2, elapsedTime, queue::class)

        return queue
    }

    /**
     * Seed urls, with load arguments are stored in a file, here we read them and parse all configured seed urls into a
     * url queue.
     *
     * We will fetch each seed page, parse it into a document, extract all required hyperlinks from it,
     * keep all the links into WebPage.vividLinks. We do not write the page content to database, which is useless and
     * time-consuming.
     * */
    @Synchronized
    fun generateTo(sink: MutableCollection<UrlAware>) {
        if (!isActive) {
            return
        }

        val propertiesContent = ResourceLoader.readString(propertiesResource)
        val props: SeedProperties = JavaPropsMapper().readValue(propertiesContent)
        val options = session.options(props.loadOptions)

        // val n = if (ClusterTools.isDevInstance()) 10 else Int.MAX_VALUE

        relevantBestSellers.clear()
        generateRelevantBestSellersTo(relevantBestSellers)

        var count = 0
        prepareFiles()
        // Ensure file dailyAsinTaskPath does not exist
        relevantBestSellers.forEach { count += writeAsinTasks(it.value) }
        if (count != 0) {
            logger.info("Written {} asin urls to {}", count, dailyAsinTaskPath)
        }

        if (!Files.exists(dailyAsinTaskPath)) {
            logger.warn("File doesn't exist, no asin urls will be generated | {}", dailyAsinTaskPath)
            return
        }

        val asins = readAsinTasks(options)
        if (asins.isEmpty()) {
            logger.warn("No asin urls in file | {}", dailyAsinTaskPath)
            return
        }

        val now = Instant.now()
        relevantBestSellers.values.onEach { it.prevCrawlTime1 = now }.forEach { session.persist(it) }
        logger.info(
            "Updated {} relevant bestseller pages with prevCrawlTime1 to be now({})",
            relevantBestSellers.size, LocalDateTime.now()
        )

        val asinOptions = session.options("-expires 30d")
        val (fetchedUrls, time) = measureTimedValueJvm {
            WebDbLongTimeTask(webDb, "ASIN")
                .getAll(asins, GWebPage.Field.PREV_FETCH_TIME)
                .filterNot { asinOptions.isExpired(it.prevFetchTime) }
                .mapTo(HashSet()) { it.url }
        }

        val readyLinks = if (testMode) {
            asins
        } else {
            asins.filterNot { it.url in fetchedUrls }.shuffled()
        }

        logger.info(
            "Generated {} asin links from {} bestsellers in {}, with {} ones removed(fetched)",
            readyLinks.size, relevantBestSellers.size, time, fetchedUrls.size
        )

        generatedCount += readyLinks.size
        // shuffle the urls so they have fair chance to run
        if (isDev) {
            readyLinks.take(50).toCollection(sink)
        } else {
            // NOTE: every link is a ListenableHyperlink, which is not persistable
            readyLinks.toCollection(sink)
        }
    }

    /**
     * Load today's asin links from bestseller pages and then write them into the task file.
     * The task files are time unique.
     *
     * The asin links were previously extracted from bestseller page and were written to WebPage.vividLinks.
     * */
    @Synchronized
    fun writeAsinTasks(page: WebPage): Int {
        val referrer = page.url

        val urls = page.vividLinks.keys.mapNotNullTo(HashSet()) { normalizer(it.toString()) }

        try {
            val text = urls.joinToString("\n") { "$it $referrer" }
            Files.writeString(dailyAsinTaskPath, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            Files.writeString(dailyAsinTaskPath, "\n", StandardOpenOption.APPEND)
        } catch (e: IOException) {
            logger.warn("Failed to write vivid links", e)
        }

        return urls.size
    }

    @Synchronized
    fun readAsinTasks(options: LoadOptions): List<Hyperlink> {
        if (!Files.exists(dailyAsinTaskPath)) {
            return listOf()
        }

        val urlRegex = options.outLinkPattern.toRegex()
        val asinLoadArgs = "-i $expires -parse -ignoreFailure -sc 1 -label asin" +
                " -taskId $taskId -taskTime $taskTime -deadTime $deadTime"

        val asins = Files.readAllLines(dailyAsinTaskPath).asSequence()
            .filter { !it.trim().startsWith("#") }
            .map { it.split("\\s+".toRegex()) }
            .filter { it.size == 2 }
            .associate { normalizeOrEmpty(it[0]) to it[1] }
            .filter { it.key.matches(urlRegex) }
            .map { createASINHyperlink(it.key, asinLoadArgs, it.value) }
            .toList()

        logger.info("Read {} asins from file | {}", asins.size, dailyAsinTaskPath)

        return asins
    }

    @Throws(MalformedURLException::class)
    fun createASINHyperlink(asinUrl: String, args: String, referrer: String): ListenableHyperlink {
        val tld = URLUtil.getDomainSuffix(asinUrl)?.domain
            ?: throw MalformedURLException("Failed to get domain suffix | $asinUrl")

        val domain = "amazon.$tld"
        val hyperlink = ListenableHyperlink(asinUrl, args = "$args -parse", referrer = referrer)
        // no scrolling at all
        val interactSettings = InteractSettings(initScrollPositions = "0.2,0.5", scrollCount = 0)

        val le = hyperlink.event.loadEvent
        le.onWillFetch.addLast { page ->
            // val tld = URLUtil.getDomainSuffix(URL(page.url))?.domain
            page.batchId = tld
            tld
        }

        le.onHTMLDocumentParsed.addLast { page, document ->
        }

        val be = hyperlink.event.browseEvent

        be.onBrowserLaunched.addLast { page, driver ->
            val warmUpUrl = "https://www.$domain/"
            logger.info("Browser launched, warm up with url | {}", warmUpUrl)
            driver.navigateTo(warmUpUrl)
        }

        be.onWillNavigate.addLast { page, driver ->
            page.setVar("InteractSettings", interactSettings)

            driver.addBlockedURLs(blockingRule.blockingUrls)

//            driver.addProbabilityBlockedURLs(blockingRule.blockingUrls)

            null
        }

        be.onDocumentActuallyReady.addLast { page, driver ->
            null
        }

        be.onDidScroll.addLast { page, driver ->

        }

        // remove comment to reduce disk space
        be.onDidInteract.addLast { page, driver ->
        }

        return hyperlink
    }

    @Synchronized
    fun generateRelevantBestSellersTo(bestSellerPages: MutableMap<String, WebPage>) {
        if (!isActive) {
            return
        }

        if (zgbsNextCheckTime > Instant.now()) {
            logger.warn("The next best seller check will be at {}", zgbsNextCheckTime)
            return
        }
        zgbsNextCheckTime += zgbsMinimalCheckInterval

        val primaryZgbs = if (isDev) {
            // Load bestseller links just fetched
            LinkExtractors.fromFile(PATH_FETCHED_BEST_SELLER_URLS)
                .filter { ("zgbs" in it || "bestsellers" in it) && "pg=2" !in it }
        } else {
            // Load all required bestseller links
            LinkExtractors.fromResource(bestSellerResource)
        }

        val secondaryZgbs = primaryZgbs.map { "$it/ref=zg_bs_pg_2?_encoding=UTF8&pg=2" }
        var unorderedZgbs = (primaryZgbs + secondaryZgbs).toList()

        if (isDev) {
            unorderedZgbs = unorderedZgbs.take(3000)
            logger.info("Checking {} bestsellers in database (development mode)", unorderedZgbs.size)
        } else {
            logger.info("Checking {} bestsellers in database", unorderedZgbs.size)
        }

        val startTime = Instant.now()
        val bestSellerPrevCrawlTimeAwarePages = WebDbLongTimeTask(webDb, "Relevant BS")
            .getAll(unorderedZgbs.asSequence(), GWebPage.Field.PREV_CRAWL_TIME1)
            .toList()
            .sortedBy { it.prevCrawlTime1 }

        if (bestSellerPrevCrawlTimeAwarePages.isEmpty()) {
            logger.warn("No bestseller page loaded")
            return
        }

        val days = 28
        val linkCountPerPage = averageVividLinkCount.coerceAtLeast(1)
        val collectedPageCount = collectedCount / linkCountPerPage
        // the endTime for ASIN task is 23:30, so minus Duration.ofMinutes(30)
        val remainingSeconds = PredefinedTask.ASIN.endTime().epochSecond - Instant.now().epochSecond
        val estimatedPagesPerSecond = 1.0 * ClusterTools.crawlerCount
        val maxBSPageCount = (estimatedPagesPerSecond * remainingSeconds / linkCountPerPage).toInt()
        var bsPageCount = (bestSellerPrevCrawlTimeAwarePages.size / days - collectedPageCount)
            .coerceAtLeast(1)
            .coerceAtMost(maxBSPageCount)
        // crawl asin between 14:00 ~ 23:30, about 36000 seconds/pages every day
        // every bestseller has less than 50 pages, so we need about 36000 / 50 = 720 bestseller pages
        val linkAwareFields = arrayOf(GWebPage.Field.PREV_CRAWL_TIME1.toString(),
            GWebPage.Field.VIVID_LINKS.toString())

        if (alwaysFalse() && injectAllAsinUrls) {
            logger.warn("All asin urls collected from all bestseller pages will be injected, " +
                    "which is a feature only for debug")
            bsPageCount = bestSellerPrevCrawlTimeAwarePages.size
            bestSellerPrevCrawlTimeAwarePages.parallelStream().forEach {
                val page = session.load(it.url, "-storeContent true -requireSize 1000")
            }
        }

        val bestSellerLinkAwarePages = bestSellerPrevCrawlTimeAwarePages
            .asSequence().distinct().take(bsPageCount)
            .mapNotNull { webDb.getOrNull(it.url, fields = linkAwareFields) }
            .toList()

        if (bestSellerLinkAwarePages.isEmpty()) {
            logger.warn("There is no link aware bestsellers (unexpected)")
        }

        val linkCount = bestSellerLinkAwarePages.sumOf{ it.vividLinks.size }
        averageVividLinkCount = linkCount / bestSellerLinkAwarePages.size
        bestSellerLinkAwarePages.associateByTo(bestSellerPages) { it.url }

        logger.info(
            "Loaded {}/{}/{} bestsellers (max {} ones for today) with {} links in {} | prev crawl time: {} -> {}",
            bestSellerPages.size, bestSellerLinkAwarePages.size, bestSellerPrevCrawlTimeAwarePages.size,
            maxBSPageCount,
            linkCount,
            DateTimes.elapsedTime(startTime),
            bestSellerLinkAwarePages.first().prevCrawlTime1,
            bestSellerLinkAwarePages.last().prevCrawlTime1
        )
    }

    fun clearAll() {
        val collector = asinCollector ?: return
        collector.urlCache.clear()
        externalClear()
    }

    fun externalClear() {
        val collector = asinCollector ?: return

        val queues = collector.urlCache.queues.filterIsInstance<AbstractLoadingQueue>()
        val count = queues.sumOf { it.estimatedExternalSize }
        if (count > 0) {
            logger.info("Clear {} external asin tasks", count)
            queues.forEach { it.externalClear() }
        } else {
            logger.info("No external asin tasks to clear")
        }
    }

    @Synchronized
    private fun prepareFiles() {
        val directory = dailyAsinTaskPath.parent
        if (Files.exists(dailyAsinTaskPath)) {
            val predicate = { path: Path, attr: BasicFileAttributes ->
                attr.isRegularFile && "$dayOfMonth.txt" in path.fileName.toString()
            }
            val count = Files.find(directory, 1, predicate).count()
            if (count > 0) {
                val archiveFilename = "$dayOfMonth.txt.$count"
                Files.move(dailyAsinTaskPath, directory.resolve(archiveFilename))
                logger.info("File has been moved | {}", archiveFilename)
            }
        } else {
            Files.createDirectories(directory)
        }
    }

    private fun normalizeOrEmpty(url: String): String {
        return normalizer(url) ?: ""
    }
}
