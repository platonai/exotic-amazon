package ai.platon.exotic.amazon.tools.common

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.common.diffusing.IndexHyperlinkProcessor
import ai.platon.exotic.common.diffusing.ItemHyperlinkProcessor
import ai.platon.exotic.common.diffusing.NavigationProcessor
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.exotic.common.diffusing.config.DiffusingCrawlerConfig
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.LoggerFactory
import java.text.ParseException
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet

class AmazonIndexHyperlinkProcessor(
    config: DiffusingCrawlerConfig,
    session: ScentSession
) : IndexHyperlinkProcessor(config, session) {

    private val logger = LoggerFactory.getLogger(AmazonIndexHyperlinkProcessor::class.java)

    override fun filter(url: String): String? {
        return super.filter(url)?.takeIf { !it.contains("&i=aps") }
    }

    override fun normalize(url: String): String {
        return UrlUtils.keepQueryParameters(url, "k", "i", "page")
    }

    /**
     * Normalize an index page url, only k, i query parameters are kept
     * a typical url:
     * https://www.amazon.com/s?k=sleep&i=subscribe-with-amazon
     * */
    override fun template(url: String): String {
        return UrlUtils.keepQueryParameters(url, "k", "i")
    }

    /**
     * The primary index hyperlink, e.g.
     * https://www.amazon.com/s?k=insomnia&i=instant-video
     * */
    fun createPrimaryIndexHyperlink(url: String, referrer: String? = null): Hyperlink? {
        return createHyperlink(url, null, referrer)
    }
}

class AmazonItemHyperlinkProcessor(
    config: DiffusingCrawlerConfig,
    session: ScentSession
) : ItemHyperlinkProcessor(config, session) {
    private val logger = LoggerFactory.getLogger(AmazonItemHyperlinkProcessor::class.java)

    val knownAsins = ConcurrentSkipListSet<String>()

    /**
     * Filter an url if it's a valid item page url
     * */
    override fun filter(url: String): String? {
        url.takeIf { UrlUtils.isValidUrl(it) && it.matches(config.itemPageUrlPattern.toRegex()) } ?: return null

        val asin = AmazonUrls.findAsin(url) ?: return null

        return url.takeUnless { knownAsins.contains(asin) }?.also { knownAsins.add(it) }
    }

    /**
     * Normalize an item page url, all query parameters are removed
     * a typical url:
     * https://www.amazon.com/Sleep-Hypnosis-Weight-Loss-Affirmations/dp/B08KSBWK58/ref=sr_1_27?dchild=1&keywords=sleep&qid=1609395087&s=audible&sr=1-27
     * */
    override fun normalize(url: String): String {
        return url.substringBefore("/ref=")
    }
}

open class AmazonNavigationProcessor(
    config: DiffusingCrawlerConfig,
    indexPageProcessor: IndexHyperlinkProcessor,
    session: ScentSession
) : NavigationProcessor(config, indexPageProcessor, session) {
    private val log = LoggerFactory.getLogger(AmazonNavigationProcessor::class.java)

    /**
     * Collect index urls for the prime index page
     * */
    override fun collectTo(document: FeaturedDocument, sink: MutableCollection<UrlAware>) {
        val url = document.baseUri.takeIf { indexPageProcessor.getPageNo(it) <= 1 } ?: return

        val navigation = document.selectFirstOrNull(config.navigationCss) ?: return
        val pageCount = findPageCount(document, navigation)

        if (pageCount >= 2) {
            val urlTemplate = indexPageProcessor.template(url)
            log.info("Generating $pageCount secondary index hyperlinks from url | $url")
            IntRange(2, pageCount).asSequence()
                .map { "$urlTemplate&page=$it" }
                .mapIndexedNotNullTo(sink) { i, u -> indexPageProcessor.createHyperlink(u, referrer = url) }
        } else {
            // in case the last page is not available
            navigation.selectFirstOrNull(config.nextPageCss)
                ?.let { indexPageProcessor.createSecondaryIndexHyperlink(it) }
                ?.let { sink.add(it) }
        }
    }
}

class AmazonReviewIndexHyperlinkProcessor(
    config: DiffusingCrawlerConfig,
    session: ScentSession
) : IndexHyperlinkProcessor(config, session) {

    private val log = LoggerFactory.getLogger(AmazonReviewIndexHyperlinkProcessor::class.java)

    val reviewRatingCountSelector =
        "#filter-info-section div[data-hook=cr-filter-info-review-rating-count], #filter-info-section"

    val primaryReviewUrlExample = "https://www.amazon.ca/Weighted-Blanket-G1-60x80-CA/product-reviews/B07T5K4S7P" +
            "/ref=cm_cr_dp_d_show_all_btm?ie=UTF8&reviewerType=all_reviews"
    val secondaryReviewUrlExamples = arrayOf(
        "https://www.amazon.ca/Weighted-Blanket-G1-60x80-CA/product-reviews/B07T5K4S7P" +
                "/ref=cm_cr_arp_d_paging_btm_next_2?ie=UTF8&reviewerType=all_reviews&pageNumber=2",
        "https://www.amazon.com/Bedsure-Satin-Pillowcase-Hair-2-Pack/product-reviews/B07MRC5WBN" +
                "/ref=cm_cr_arp_d_paging_btm_next_2?ie=UTF8&reviewerType=all_reviews&pageNumber=2"
    )
    val secondaryReviewUrlNextRefTemplates = secondaryReviewUrlExamples
        .mapNotNull { buildSecondaryReviewUrlNextRefTemplate(it) }.associate { it.first to it.second }

    override fun filter(url: String) = url

    override fun normalize(url: String) = url

    /**
     * Create an item link from a selected anchor of item link
     * */
    fun createPrimaryReviewIndexLink(primaryReviewUrl: String, referrer: String? = null): Hyperlink? {
        val deadTime = PredefinedTask.REVIEW.deadTime()
        return createHyperlink(primaryReviewUrl, referrer = referrer, deadTime = deadTime)
    }

    /**
     * Create a secondary review index link
     * */
    fun createSecondaryReviewIndexLink(primaryReviewUrl: String, pageNo: Int): Hyperlink? {
        val host = UrlUtils.getURLOrNull(primaryReviewUrl)?.host ?: return null
        val secondaryReviewUrlNextRefTemplate = secondaryReviewUrlNextRefTemplates[host] ?: return null
        val ref = "/ref=$secondaryReviewUrlNextRefTemplate$pageNo\\?"
        val prefix = primaryReviewUrl.replace("/ref=.+\\?".toRegex(), ref)
        val url = "$prefix&sortBy=recent&pageNumber=$pageNo"
        val deadTime = PredefinedTask.REVIEW.deadTime()
        return createHyperlink(url, referrer = primaryReviewUrl, deadTime = deadTime)?.apply { order = pageNo }
    }

    /**
     * Create an item link from a selected anchor of item link
     * */
    fun createSecondaryReviewIndexLinks(
        primaryReviewUrl: String,
        totalPages: Int,
        allReviews: Boolean = false
    ): List<Hyperlink> {
        return when {
            totalPages <= 0 -> listOf()
            allReviews -> createSecondaryReviewIndexLinks(primaryReviewUrl, totalPages)
            else -> createEndSecondaryReviewIndexLinks(primaryReviewUrl, totalPages)
        }
    }

    /**
     * */
    fun createEndSecondaryReviewIndexLinks(primaryReviewUrl: String, totalPages: Int): List<Hyperlink> {
        log.info("Generating newest 3 and earliest 3 review pages for primary review page | {}", primaryReviewUrl)

        // crawl the newest 2 pages and the earliest 3 pages
        val isSorted = primaryReviewUrl.contains("sortBy=recent")
        val start = if (isSorted) 2 else 1
        val n = totalPages
        return listOf(start, 3, n - 2, n - 1, n).filter { it in start..totalPages }
            .mapNotNull { createSecondaryReviewIndexLink(primaryReviewUrl, it) }
    }

    fun createSecondaryReviewIndexLinks(primaryReviewUrl: String, totalPages: Int): List<Hyperlink> {
        // log.info("Generating all {} review pages for primary review page | {}", totalPages, primaryReviewUrl)
        // crawl all the review pages
        // for sleep project only
        val isSorted = primaryReviewUrl.contains("sortBy=recent")
        val start = if (isSorted) 2 else 1
        return createSecondaryReviewIndexLinks(primaryReviewUrl, IntRange(start, totalPages))
    }

    fun createSecondaryReviewIndexLinks(primaryReviewUrl: String, pageNumbers: IntRange): List<Hyperlink> {
        if (pageNumbers.last < pageNumbers.first) {
            return listOf()
        }

        log.debug("Generating {} review urls from primary url | {}", pageNumbers, primaryReviewUrl)
        // crawl all the review pages
        // for sleep project only
        return pageNumbers.mapNotNull { i -> createSecondaryReviewIndexLink(primaryReviewUrl, i) }
    }

    fun createSecondaryReviewIndexLinks(primaryReviewUrl: String, vararg pageNumbers: Int): List<Hyperlink> {
        log.debug("Generating review pages for primary review page | {} | {}", pageNumbers, primaryReviewUrl)
        // crawl all the review pages
        // for sleep project only
        return pageNumbers.toList().mapNotNull { i -> createSecondaryReviewIndexLink(primaryReviewUrl, i) }
    }

    fun createSecondaryReviewLinksFromPagination(page: WebPage, document: FeaturedDocument): Hyperlink? {
        // example text: Reviewed in the United States on November 28, 2020
        val cssSelector = "#cm_cr-review_list span.review-date"
        val pattern = "MMMMMMMMM dd, yyyy"

        val latestReviewDateInThisPage = if (page.url.contains("sortBy=recent")) {
            try {
                document.selectFirstOrNull(cssSelector)
                    ?.text()?.substringAfterLast(" on ")
                    ?.let { DateUtils.parseDate(it.trim(), Locale.US, pattern).toInstant() }
                    ?: Instant.EPOCH
            } catch (e: ParseException) {
                Instant.EPOCH
            }
        } else {
            Instant.now()
        }

//        val isSearchResult = AmazonPageCharacters.isSearchResultPage(page.referrer)
        val isSearchResult = true
        val now = Instant.now()
        val monthBefore = if (isSearchResult) 12 else 6
        val earliestReviewTime = now.minus(Duration.ofDays(30L * monthBefore))

        // only collect reviews in the latest 6 months
        if (latestReviewDateInThisPage.isAfter(earliestReviewTime)) {
            val deadTime = PredefinedTask.REVIEW.deadTime()
            val urlLabel = "/product-reviews/"
            val args = "-label ${page.label} -deadTime $deadTime"
            return document.selectFirstOrNull("ul.a-pagination li.a-last a[href~=$urlLabel]")
                ?.attr("abs:href")?.trim()
                ?.let { UrlUtils.getURLOrNull(it)?.toString() }
                ?.let { Hyperlink(it, args = args, referrer = page.url) }
        }

        return null
    }

    private fun buildSecondaryReviewUrlNextRefTemplate(url: String): Pair<String, String>? {
        val host = UrlUtils.getURLOrNull(url)?.host ?: return null
        val template = StringUtils.substringBetween(url, "/ref=", "?").trimEnd { it.isDigit() }
        return host to template
    }
}
