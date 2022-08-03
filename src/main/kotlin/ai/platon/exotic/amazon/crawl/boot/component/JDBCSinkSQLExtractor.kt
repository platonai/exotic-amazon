package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.core.AmazonMetrics
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.tools.common.AmazonPageTraitsDetector
import ai.platon.exotic.amazon.tools.common.AmazonUrls
import ai.platon.exotic.amazon.tools.common.AmazonUtils
import ai.platon.exotic.amazon.tools.common.PageTraits
import ai.platon.pulsar.common.CheckState
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.message.LoadStatusFormatter
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.persist.ext.options
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.crawl.parse.html.ParseContext
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.parse.html.AbstractJdbcSinkSQLExtractor
import com.codahale.metrics.Gauge
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant

/**
 * A JDBC sink SQL extractor is an extractor using SQL to extract fields from webpages and then sync the extract result
 * to a JDBC sink, such as MySQL database.
 * */
@Component
@Scope("prototype")
class JDBCSinkSQLExtractor(
    session: ScentSession,
    scentStatusTracker: ScentStatusTracker,
    globalCacheFactory: GlobalCacheFactory,
    private val amazonGenerator: AmazonGenerator,
    private val amazonLinkCollector: AmazonLinkCollector,
    conf: ImmutableConfig,
) : AbstractJdbcSinkSQLExtractor(session, scentStatusTracker, globalCacheFactory, conf) {
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

    private val urlPool get() = globalCache.urlPool
    private val reviewFetchCache
        get() = amazonGenerator.asinGenerator.reviewCollector?.urlCache ?: urlPool.lower2Cache
    private val reviewQueue get() = reviewFetchCache.nonReentrantQueue
    private val amazonMetrics = AmazonMetrics.extractMetrics

    override fun initialize() {
        if (ClusterTools.isDevInstance() || ClusterTools.isTestInstance()) {
            commitConfig?.syncBatchSize = 10
        }
    }

    override fun isRelevant(parseContext: ParseContext): CheckState {
        val page = parseContext.page
        val state = if (!AmazonUrls.isAmazon(page.url)) {
            CheckState(1010, "not amazon")
        } else {
            super.isRelevant(parseContext)
        }

        lastRelevantState = state
        if (!state.isOK && state.code >= 40 && state.code !in listOf(60, 1601)) {
            val report = LoadStatusFormatter(page, withOptions = true).toString()
            irrLogger.info("Irrelevant page({}) in extractor <{}> | {}", state.message, name, report)
        }

        return state
    }

    override fun onBeforeFilter(page: WebPage, document: FeaturedDocument) {
        super.onBeforeFilter(page, document)

        pendingResultManager.syncBatchSize = if (meterResults.count > 100) syncBatchSize else 10

        lastLang = document.selectFirstOrNull("#nav-tools .icp-nav-flag")?.attr("class") ?: ""
        lastDistrict = document.selectFirstOrNull("#glow-ingress-block")?.text() ?: ""
    }

    /**
     * The event handler after extraction.
     * */
    override fun onAfterExtract(page: WebPage, document: FeaturedDocument, rs: ResultSet?): ResultSet? {
        rs ?: return null

        // do not really sync the extract result for test pages
        val isTestPage = page.label == ClusterTools.getTestLabel() || ClusterTools.isTestInstance()
        if (isTestPage) {
            committers.values.forEach { it.dryRunSQLs = true }
        }

        // add the extract result to the pending result manager, who will sync all the extract results to the sink later
        pendingResultManager.add(sinkCollection, name, rs, page.options.deadTime)
        page.modelSyncTime = Instant.now()

        val traits = AmazonUtils.detectTraits(page, isAsinExtractor(page), amazonMetrics, statusTracker)

        // collect hyperlinks which will be fetched in the future
        collectHyperlinks(page, document, rs, traits)

        return rs
    }

    /**
     * Check if all fields are match the requirement, for example, some fields are required to be not null or not blank
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
     * Collect further hyperlinks after extraction.
     * */
    private fun collectHyperlinks(page: WebPage, document: FeaturedDocument, rs: ResultSet, traits: PageTraits) {
        val url = page.url

        when {
            traits.isLabeledPortal -> {
                val label = AmazonPageTraitsDetector.getLabelOfPortal(url)
                // a typical option:
                // https://www.amazon.com/Best-Sellers-Video-Games-Xbox/zgbs/videogames/20972814011
                // -authToken vEcl889C-1-ea7a98d6157a8ca002d2599d2abe55f9 -expires PT24H -itemExpires PT720H
                // -label best-sellers-all -outLinkSelector "#zg-ordered-list a[href~=/dp/]"
                if (label == PredefinedTask.BEST_SELLERS.label) {
                    amazonLinkCollector.collectAsinLinksFromBestSeller(page, document)
                }

                // Every primary portal page have a concomitant secondary one, rising the priority
                // should be a reentrant queue since the links are fetched periodically
                val queue2 = urlPool.higher2Cache.reentrantQueue
                if (!url.contains("?")) {
                    // a primary labeled portal, supposed to be loaded from a config file or database
                    amazonLinkCollector.updateWebNode(page, document, queue2)
                }

                val queue3 = urlPool.higher3Cache.reentrantQueue
                val hyperlink = amazonLinkCollector.collectSecondaryLinksFromLabeledPortal(label, page, document, queue3)
                val isPrimary = AmazonPageTraitsDetector.isPrimaryLabeledPortalPage(page.url)
                if (isPrimary && hyperlink == null) {
                    when (label) {
                        "zgbs" -> amazonMetrics.noszgbs.mark()
                        "most-wished-for" -> amazonMetrics.nosmWishedF.mark()
                        "new-releases" -> amazonMetrics.nosnRelease.mark()
                    }
                }
            }
            traits.isItem && isAsinExtractor(page) -> {
                // collect prime review pages (resultset.reviewsurl)
                amazonLinkCollector.collectReviewLinksFromProductPage(page, sqlTemplate.template, rs, reviewQueue)
            }
            traits.isPrimaryReview -> {
                // collect the all review urls
                // NOTE: actually, primary review is not collected by default
                amazonLinkCollector.collectSecondaryReviewLinks(page, document, rs, reviewQueue)
            }
            traits.isSecondaryReview -> {
                // collect the all the review urls
                amazonLinkCollector.collectSecondaryReviewLinksFromPagination(page, document, reviewQueue)
            }
        }
    }

    private fun isAsinExtractor(page: WebPage): Boolean {
        return isRoot && AmazonPageTraitsDetector.isProductPage(page.url)
    }
}
