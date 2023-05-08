package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.boot.component.common.AbstractSinkAwareSQLExtractor
import ai.platon.exotic.amazon.crawl.core.AmazonMetrics
import ai.platon.exotic.amazon.crawl.core.PATH_FETCHED_BEST_SELLER_URLS
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.tools.common.AmazonPageTraitsDetector
import ai.platon.exotic.amazon.tools.common.AmazonUrls
import ai.platon.exotic.amazon.tools.common.AmazonUtils
import ai.platon.exotic.amazon.tools.common.PageTraits
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.common.jdbc.JdbcCommitter
import ai.platon.pulsar.common.AppFiles
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.CheckState
import ai.platon.pulsar.common.collect.UrlCache
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.message.LoadStatusFormatter
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.crawl.parse.ParseResult
import ai.platon.pulsar.crawl.parse.html.ParseContext
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.ql.h2.utils.JdbcUtils
import ai.platon.pulsar.ql.h2.utils.ResultSetUtils
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import com.codahale.metrics.Gauge
import com.google.gson.GsonBuilder
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

/**
 * SQL extractors use SQLs to extract fields from webpages.
 * */
@Component
@Scope("prototype")
class AmazonJdbcSinkSQLExtractor(
    session: ScentSession,
    statusTracker: ScentStatusTracker,
    globalCacheFactory: GlobalCacheFactory,
    private val amazonGenerator: AmazonGenerator,
    private val amazonLinkCollector: AmazonLinkCollector,
    conf: ImmutableConfig,
) : AbstractSinkAwareSQLExtractor(session, statusTracker, globalCacheFactory, conf) {
    companion object {
        /**
         * The language of the site, choose the language in the top-right corner of the webpage
         * */
        var lastLang = ""
            private set
        /**
         * The district to deliver to, choose the district in the top-left corner of the webpage
         * */
        var lastDistrict = ""
            private set

        init {
            // report the language and district to the metrics, so we can check if they are correct.
            mapOf(
                "lastLang" to Gauge { lastLang },
                "lastDistrict" to Gauge { lastDistrict }
            ).let { AppMetrics.reg.registerAll(this, it) }
        }
    }

    private val logger = getLogger(this)

    private val isDev get() = ClusterTools.isDevInstance()

    private var extractCounter = 0

    /**
     * The global url pool, all fetch tasks are added to the pool in some form of Pulsar URLs.
     * */
    private val urlPool get() = globalCache.urlPool

    private val enableReviews = conf.getBoolean("amazon.enable.reviews", true)

    /**
     * The cache for review urls
     * */
    private val reviewFetchCache: UrlCache?
        get() = if (enableReviews) {
            amazonGenerator.asinGenerator.reviewCollector?.urlCache ?: urlPool.lower2Cache
        } else null
    /**
     * The url queue for review urls
     * */
    private val reviewQueue get() = reviewFetchCache?.nonReentrantQueue

    private val amazonMetrics = AmazonMetrics.extractMetrics

    var jdbcCommitter: JdbcCommitter? = null

    /**
     * Initialize the extractor, should be invoked just after the object is created.
     * */
    override fun initialize() {
    }

    /**
     * Check if this extractor is relevant to the current fetched page, if not relevant,
     * this extractor will be skipped.
     *
     * If the extractor is not skipped, the execution flow shows below:
     * isRelevant (true) -> onBeforeFilter -> onBeforeExtract -> extract -> onAfterExtract -> onAfterFilter
     * */
    override fun isRelevant(parseContext: ParseContext): CheckState {
        val page = parseContext.page

        var state = super.isRelevant(parseContext)
        if (!state.isOK && state.code == 60) {
            // Loaded page, we need to extract loaded pages in this project
            state = CheckState()
        }

        if (state.isOK) {
            state = when {
                !AmazonUrls.isAmazon(page.url) -> CheckState(1010, "not amazon")
                parseContext.parseResult.isFailed -> {
                    logger.warn("Parse failure, ignore this extractor ({}) ... | {}", this.javaClass.simpleName, page.url)
                    CheckState(1020, "parse failure")
                }
                parseContext.document == null -> {
                    logger.warn("Document is null, ignore this extractor ({}) ... | {}", this.javaClass.simpleName, page.url)
                    CheckState(1021, "invalid document")
                }
                else -> state
            }
        }

        lastRelevantState = state
        if (!state.isOK && state.code >= 40 && state.code !in listOf(0, 60, 1601)) {
            val report = LoadStatusFormatter(page, withOptions = true).toString()
            logger.info("Irrelevant page({}) in extractor <{}> | {}", state.message, name, report)
        }

//        if (urlFilter.toString().contains("zgbs")) {
//            println("CheckState " + urlFilter + " " + state.message + " | " + page.url)
//        }

        return state
    }

    /**
     * The event handler before filter.
     *
     * If the extractor is not skipped, the execution flow shows below:
     * isRelevant (true) -> onBeforeFilter -> onBeforeExtract -> extract -> onAfterExtract -> onAfterFilter
     */
    override fun onBeforeFilter(page: WebPage, document: FeaturedDocument) {
        super.onBeforeFilter(page, document)

        lastLang = document.selectFirstOrNull("#nav-tools .icp-nav-flag")?.attr("class") ?: ""
        lastDistrict = document.selectFirstOrNull("#glow-ingress-block")?.text() ?: ""
    }

    /**
     * The event handler after extraction.
     *
     * Once the page is extracted, we may want to use the extract result, save the result to some destination,
     * and collect further hyperlinks to fetch later.
     *
     * If the extractor is not skipped, the execution flow shows below:
     * isRelevant (true) -> onBeforeFilter -> onBeforeExtract -> extract -> onAfterExtract -> onAfterFilter
     * */
    override fun onAfterExtract(page: WebPage, document: FeaturedDocument, rs: ResultSet?): ResultSet? {
        rs ?: return null

        ++extractCounter

        // commit the result set to the destination if you have set the JDBC committer
        val committer = jdbcCommitter
        if (committer != null) {
            committer.commit(rs)
            if (extractCounter < 20000) {
                exportWebData(page, rs)
            }
        } else {
            exportWebData(page, rs)
        }

        /////////////////////////////////////////////////////////////////////////
        // Write your own code to save extract result to any destination as your wish


        //
        /////


        /////
        // collect hyperlinks which will be fetched in the future
        val traits = AmazonUtils.detectTraits(page, isAsinExtractor(page), amazonMetrics, statusTracker)
        collectHyperlinks(page, document, rs, traits)

        return rs
    }

    /**
     * Check if all fields match the requirement, for example, some fields are required to be not null or not blank
     * */
    override fun checkFieldRequirement(url: String, page: WebPage, onlyRecordRs: ResultSet) {
        if (!isAsinExtractor(page)) {
            return
        }

        // ensureDistrictBeCorrect(onlyRecordRs)

        val nullColumns = collectNullFields(onlyRecordRs)
        val shouldReport = when {
            "asin" in nullColumns -> true
            nullColumns.count { it in arrayOf("price", "soldby", "shipsfrom") } in 1..2 -> true
            else -> false
        }

        if (shouldReport) {
            statusTracker.messageWriter.reportExtractedNullFields("$nullColumns | $url")
        }
    }

    /**
     * Fix: ERROR a.p.pulsar.crawl.parse.PageParser - java.lang.StackOverflowError [#4](https://github.com/platonai/exotic-amazon/issues/4)
     * */
    override fun onAfterFilter(page: WebPage, document: FeaturedDocument, parseResult: ParseResult) {
        super.onAfterFilter(page, document, parseResult)
    }

    /**
     * Collect hyperlinks after extraction, so the links can be collected from the page content, the HTML document,
     * and the result set.
     * */
    private fun collectHyperlinks(page: WebPage, document: FeaturedDocument, rs: ResultSet, traits: PageTraits) {
        val url = page.url

        when {
            traits.isLabeledPortal -> {
                val label = AmazonPageTraitsDetector.getLabelOfPortal(url)

                // archive fetched bestseller link to a file
                archiveFetchedBestSellerLink(page, document)

                // Every primary portal page have a concomitant secondary one, rising the priority
                // Should be a reentrant queue since the links are fetched periodically.
                val queue2 = urlPool.higher2Cache.reentrantQueue
                if (!url.contains("?")) {
                    // this is a primary labeled portal url, it's supposed to be loaded from a config file or database,
                    // for example, best-seller url, new-release url, etc.
                    // TODO: update the web node
                    // amazonLinkCollector.updateWebNode(page, document, queue2)
                }

                // generate ASIN tasks immediately
                collectAndSubmitASINLinks(label, page, document, queue2)

                val queue3 = urlPool.higher3Cache.reentrantQueue
                val hyperlink = amazonLinkCollector.collectSecondaryLinksFromLabeledPortal(label, page, document, queue3)
                val isPrimary = AmazonPageTraitsDetector.isPrimaryLabeledPortalPage(page.url)
                if (isPrimary && hyperlink == null) {
                    when (label) {
                        "zgbs", "bestsellers" -> amazonMetrics.noszgbs.mark()
                        "most-wished-for" -> amazonMetrics.nosmWishedF.mark()
                        "new-releases" -> amazonMetrics.nosnRelease.mark()
                    }
                }
            }
            traits.isItem && isAsinExtractor(page) -> {
                // collect prime review pages (resultset.reviewsurl)
                reviewQueue?.let { queue ->
                    amazonLinkCollector.collectReviewLinksFromProductPage(page, sqlTemplate.template, rs, queue)
                }
            }
            traits.isPrimaryReview -> {
                // collect the all review urls
                // NOTE: actually, primary review is not collected by default
                reviewQueue?.let { queue ->
                    amazonLinkCollector.collectSecondaryReviewLinks(page, document, rs, queue)
                }
            }
            traits.isSecondaryReview -> {
                // collect the all the review urls
                reviewQueue?.let { queue ->
                    amazonLinkCollector.collectSecondaryReviewLinksFromPagination(page, document, queue)
                }
            }
        }
    }

    private fun collectAndSubmitASINLinks(
        label: String, page: WebPage, document: FeaturedDocument, queue: Queue<UrlAware>
    ) {
        // some site uses "bestsellers" in the url
        // https://www.amazon.fr/gp/bestsellers/hpc/3160863031/ref=zg_bs_nav_hpc_2_3160836031
        if (label != PredefinedTask.BEST_SELLERS.label && label != "bestsellers") {
            // logger.warn("Not bestseller | {}", page.url)
            // return
        }

        // a typical option:
        // https://www.amazon.com/Best-Sellers-Video-Games-Xbox/zgbs/videogames/20972814011
        // -authToken vEcl889C-1-ea7a98d6157a8ca002d2599d2abe55f9 -expires PT24H -itemExpires PT720H
        // -label best-sellers-all -outLinkSelector "#zg-ordered-list a[href~=/dp/]"
        val links = amazonLinkCollector.collectAsinLinksFromBestSeller(page, document)
        // generate ASIN tasks immediately
        links.forEach { queue.add(it) }
    }

    private fun archiveFetchedBestSellerLink(page: WebPage, document: FeaturedDocument) {
        if (page.label != PredefinedTask.BEST_SELLERS.label) {
            // log.warn("Should has zgbs label, actual <{}> | {}", page.label, page.configuredUrl)
        }

        val url = page.url

        Files.createDirectories(PATH_FETCHED_BEST_SELLER_URLS.parent)
        Files.writeString(
            PATH_FETCHED_BEST_SELLER_URLS, "$url\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun isAsinExtractor(page: WebPage): Boolean {
        return isRoot && AmazonPageTraitsDetector.isProductPage(page.url)
    }

    private fun exportWebData(page: WebPage, rs: ResultSet) {
        val entities = ResultSetUtils.getTextEntitiesFromResultSet(rs)
        val json = GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(entities)
        val label = page.label.takeIf { it.isNotBlank() } ?: "other"
        val filename = AppPaths.fromUri(page.url,"", ".json")
        val path = AppPaths.DOC_EXPORT_DIR
            .resolve("amazon")
            .resolve("json")
            .resolve(label)
            .resolve(filename)
        AppFiles.saveTo(json, path, true)
    }
}
