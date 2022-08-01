package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.common.AmazonMetrics
import ai.platon.exotic.amazon.tools.common.AmazonUtils
import ai.platon.exotic.amazon.crawl.core.ClusterTools
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.tools.common.AmazonPageTraitsDetector
import ai.platon.exotic.amazon.tools.common.PageTraits
import ai.platon.pulsar.common.CheckState
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.message.LoadStatusFormatter
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.persist.ext.options
import ai.platon.pulsar.common.urls.sites.amazon.AmazonUrls
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.crawl.parse.html.ParseContext
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.parse.html.AbstractJdbcSinkSQLExtractor
import com.codahale.metrics.Gauge
import org.apache.commons.lang3.StringUtils
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant

@Component
@Scope("prototype")
class JdbcSinkSQLExtractor(
    session: ScentSession,
    scentStatusTracker: ScentStatusTracker,
    globalCacheFactory: GlobalCacheFactory,
    private val mainGenerator: MainGenerator,
    private val hyperlinkCollector: LinkCollectors,
    conf: ImmutableConfig,
) : AbstractJdbcSinkSQLExtractor(session, scentStatusTracker, globalCacheFactory, conf) {
    companion object {
        var lastLang = ""
            private set
        var lastDistrict = ""
            private set

        init {
            mapOf(
                "lastLang" to Gauge { lastLang },
                "lastDistrict" to Gauge { lastDistrict }
            ).let { AppMetrics.reg.registerAll(this, it) }
        }
    }

    private val logger = getLogger(this)

    /**
     * Required field check
     * */
    private val productPageRequiredFields = listOf("asin", "img")

    private val urlPool get() = globalCache.urlPool
    private val reviewFetchCache
        get() = mainGenerator.asinGenerator.reviewCollector?.urlCache ?: urlPool.lower2Cache
    private val reviewQueue get() = reviewFetchCache.nonReentrantQueue
    private val amazonMetrics = AmazonMetrics.extractMetrics

    override fun initialize() {
        if (ClusterTools.isTestInstance()) {
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

    override fun onAfterExtract(page: WebPage, document: FeaturedDocument, rs: ResultSet?): ResultSet? {
        rs ?: return null

        val isTestPage = page.label == ClusterTools.getTestLabel() || ClusterTools.isTestInstance()
        if (isTestPage) {
            committers.values.forEach { it.dryRunSQLs = true }
        }

        pendingResultManager.add(sinkCollection, name, rs, page.options.deadTime)
        page.modelSyncTime = Instant.now()

        val traits = AmazonUtils.detectTraits(page, isAsinExtractor(page), amazonMetrics, statusTracker)

        collectHyperlinks(page, document, rs, traits)

        return rs
    }

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
                    hyperlinkCollector.collectAsinLinksFromBestSeller(page, document)
                }

                // Every primary portal page have a concomitant secondary one, rising the priority
                // should be a reentrant queue since the links are fetched periodically
                val queue2 = urlPool.higher2Cache.reentrantQueue
                if (!url.contains("?")) {
                    // a primary labeled portal, supposed to be loaded from a config file or database
                    hyperlinkCollector.updateWebNode(page, document, queue2)
                }

                val queue3 = urlPool.higher3Cache.reentrantQueue
                val hyperlink = hyperlinkCollector.collectSecondaryLinksFromLabeledPortal(label, page, document, queue3)
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
                hyperlinkCollector.collectReviewLinksFromProductPage(page, sqlTemplate.template, rs, reviewQueue)
            }
            traits.isPrimaryReview -> {
                // collect the all review urls
                // NOTE: actually, primary review is not collected by default
                hyperlinkCollector.collectSecondaryReviewLinks(page, document, rs, reviewQueue)
            }
            traits.isSecondaryReview -> {
                // collect the all the review urls
                hyperlinkCollector.collectSecondaryReviewLinksFromPagination(page, document, reviewQueue)
            }
        }
    }

    private fun isAsinExtractor(page: WebPage): Boolean {
        return isRoot && AmazonPageTraitsDetector.isProductPage(page.url)
    }

    private fun getReviewFetchQueueOrNull() {

    }

    private fun extractSearchKeywords(page: WebPage, document: FeaturedDocument) {
        val url = page.url
        val href = page.href
        val k1 = StringUtils.substringBetween(url, "keywords=", "&") ?: ""
        val k2 = StringUtils.substringBetween(href, "keywords=", "&") ?: ""
        val searchKeywords = setOf(k1, k2)
            .mapNotNull { it.replace('+', ' ').takeIf { it.isNotBlank() } }
            .joinToString(", ")
        if (searchKeywords.isNotBlank()) {
            val metadata = document.document.selectFirstOrNull("#${AppConstants.PULSAR_META_INFORMATION_ID}")
            metadata?.attr("search-keywords", searchKeywords)
        }
    }
}
