package ai.platon.exotic.common.diffusing

import ai.platon.exotic.common.diffusing.config.DiffusingCrawlerConfig
import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.message.LoadStatusFormatter
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.crawl.common.url.StatefulListenableHyperlink
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.nodes.node.ext.cleanText
import ai.platon.pulsar.dom.select.collectIf
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.metadata.OpenPageCategory
import ai.platon.pulsar.persist.metadata.PageCategory
import ai.platon.scent.ScentSession
import ai.platon.scent.common.WebPages
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListSet

/**
 * Create and process hyperlink for pages by category
 * */
interface HyperlinkProcessor {
    /**
     * The label for urls and tasks created by this processor
     * */
    val label: String
    /**
     * The page category for urls and pages created by this processor
     * */
    val pageCategory: OpenPageCategory
    /**
     * The event handlers
     * */
//    @Deprecated("Set event handlers on ListenableHyperlinks instead")
//    val eventHandler: LoadEventHandler
    /**
     * Whether check the database for the status of a url before fetch
     * */
    var dbCheck: Boolean
    /**
     * Indicate the minimal size of page content, if the previous size is smaller than the minimal,
     * it should be fetched again.
     * */
    var minPageSize: Int
    /**
     * Indicate whether the page's content should be stored into the storage.
     * We suggest that the content of the detail page need not be saved, but the index page can be saved.
     * */
    var storeContent: Boolean
    /**
     * Filter a url if it's a valid url for this processor
     * */
    fun filter(url: String): String?
    /**
     * Normalize a url
     * */
    fun normalize(url: String): String?
    /**
     * Create a hyperlink
     * */
    fun createHyperlink(anchor: Element): Hyperlink?
    /**
     * Create a hyperlink
     * */
    fun createHyperlink(url: String, href: String? = null, referrer: String? = null, deadTime: Instant? = null): Hyperlink?
    /**
     * Collect urls to the sink
     * */
    fun collectTo(document: FeaturedDocument, sink: MutableCollection<UrlAware>)
}

abstract class AbstractHyperlinkProcessor(
    val config: DiffusingCrawlerConfig,
    val session: ScentSession
) : HyperlinkProcessor {

    companion object {
        val globalCreatedUrls = ConcurrentSkipListSet<String>()
    }

    private val logger = getLogger(AbstractHyperlinkProcessor::class)
    private val taskLogger = getLogger(AbstractHyperlinkProcessor::class, ".Task")

    override val label: String get() = config.label
    override val pageCategory: OpenPageCategory = OpenPageCategory(PageCategory.UNKNOWN)
//    override val eventHandler = DefaultLoadEventHandler()
    override var dbCheck: Boolean = false
    override var minPageSize = 1_000
    override var storeContent = false

    protected val indexPageUrlRegex = config.indexPageUrlPattern.toRegex()

    /**
     * Filter an url if it's a valid item page url
     * */
    override fun filter(url: String): String? {
        return url.takeIf { UrlUtils.isValidUrl(url) && url.matches(indexPageUrlRegex) }
    }

    override fun normalize(url: String): String? {
        return url
    }

    /**
     * Create a hyperlink ready to be added to the URL Pool for retrieval.
     * */
    override fun createHyperlink(anchor: Element): Hyperlink? {
        val href = filter(anchor.absUrl("href")) ?: return null
        val url = normalize(href) ?: return null
        return createHyperlink(url, href, referrer = anchor.baseUri())
    }

    /**
     * Create a hyperlink ready to be added to the URL Pool for retrieval.
     * */
    override fun createHyperlink(url: String, href: String?, referrer: String?, deadTime: Instant?): Hyperlink? {
        if (filter(url) == null) {
            return null
        }

        if (url in globalCreatedUrls) {
            return null
        }

        globalCreatedUrls.add(url)
        var isExpired = false
        if (dbCheck) {
            val page = session.getOrNull(url)
            val size = WebPages.getActualContentBytes(page)
            isExpired = size < minPageSize
            if (page == null) {
                logger.debug("Fetching {} new page | {} | {}", pageCategory.symbol, label, href ?: url)
            } else {
                val prefix = if (isExpired) "Fetching" else "Loading"
                logger.debug("{}", LoadStatusFormatter(page, prefix = prefix))
            }
        }

        val expires = if (isExpired) "0s" else "3000d"
        var args = "-i $expires -parse -ignoreFailure -storeContent $storeContent -label $label"
        if (deadTime != null) {
            args += " -deadTime $deadTime"
        }

        return StatefulListenableHyperlink(url, args = args, referrer = referrer, href = href)
    }

    override fun collectTo(document: FeaturedDocument, sink: MutableCollection<UrlAware>) {

    }
}

open class IndexHyperlinkProcessor(
    config: DiffusingCrawlerConfig,
    session: ScentSession
) : AbstractHyperlinkProcessor(config, session) {
    private val log = LoggerFactory.getLogger(IndexHyperlinkProcessor::class.java)

    override val pageCategory: OpenPageCategory = OpenPageCategory(PageCategory.INDEX)

    /**
     * Filter an url if it's a valid item page url
     * */
    override fun filter(url: String): String? {
        return url.takeIf { UrlUtils.isValidUrl(url) && url.matches(indexPageUrlRegex) }
    }

    override fun normalize(url: String) = url

    /**
     * Make an index url template
     * a typical url:
     * https://www.amazon.com/s?k=sleep&i=subscribe-with-amazon
     * */
    open fun template(url: String) = url

    open fun getPageNo(url: String): Int {
        return Strings.getFirstInteger(url.substringAfter("page="), 0)
    }

    open fun hasPageNo(url: String): Boolean {
        return getPageNo(url) > 0
    }

    open fun createSecondaryIndexHyperlink(anchor: Element): Hyperlink? {
        return createHyperlink(anchor)
    }
}

open class ItemHyperlinkProcessor(
    config: DiffusingCrawlerConfig,
    session: ScentSession
) : AbstractHyperlinkProcessor(config, session) {
    private val log = LoggerFactory.getLogger(ItemHyperlinkProcessor::class.java)

    override val pageCategory: OpenPageCategory = OpenPageCategory(PageCategory.DETAIL)

    /**
     * Filter an url if it's a valid item page url
     * */
    override fun filter(url: String): String? {
        return url.takeIf { UrlUtils.isValidUrl(it) && it.matches(config.itemPageUrlPattern.toRegex()) }
    }

    /**
     * Normalize an item page url
     * */
    override fun normalize(url: String) = url

    /**
     * Collect an url from an anchor element
     * */
    open fun collectTo(anchor: Element, sink: MutableCollection<UrlAware>) {
        createHyperlink(anchor)?.let { sink.add(it) }
    }

    private fun debugItemPage(page: WebPage, document: FeaturedDocument) {
        if (DateTimes.elapsedTime(page.fetchTime).seconds < 60) {
            return
        }

        val title = document.selectFirstOrNull("#title")?.text()
            ?: document.selectFirstOrNull("h1")?.text()
        val price = document.selectFirstOrNull(".a-color-price")?.text() ?: ""
        val buyInfo = document.selectFirstOrNull("#buying-info")?.text() ?: ""
        println("${page.id}. $price $buyInfo | $title | ${page.url}")
    }
}

open class NavigationProcessor(
    config: DiffusingCrawlerConfig,
    val indexPageProcessor: IndexHyperlinkProcessor,
    session: ScentSession
) : AbstractHyperlinkProcessor(config, session) {
    private val log = LoggerFactory.getLogger(NavigationProcessor::class.java)

    override val pageCategory: OpenPageCategory = OpenPageCategory("navigation", "N")

    /**
     * Collect index urls for the prime index page
     * */
    override fun collectTo(document: FeaturedDocument, sink: MutableCollection<UrlAware>) {
        val url = document.baseUri.takeIf { indexPageProcessor.getPageNo(it) <= 1 } ?: return

        // collect navigation pages
        val navigation = document.selectFirstOrNull(config.navigationCss) ?: return
        val pageCount = findPageCount(document, navigation)

        if (pageCount >= 2) {
            val urlTemplate = indexPageProcessor.template(url)
            IntRange(2, pageCount).asSequence()
                .map { "$urlTemplate&page=$it" }
                .mapIndexedNotNullTo(sink) { i, u ->
                    indexPageProcessor.createHyperlink(u, url)
                }
        } else {
            // in case the last page is not available
            navigation.selectFirstOrNull(config.nextPageCss)
                ?.let { indexPageProcessor.createHyperlink(it) }
                ?.let { sink.add(it) }
        }
    }

    open fun findPageCount(document: FeaturedDocument, navigation: Element): Int {
        val url = document.baseUri
        var pageCount = navigation.select(config.lastLastPageCss).text().toIntOrNull() ?: 0
        if (pageCount == 0) {
            pageCount = navigation.collectIf { it is TextNode }
                .map { it.cleanText }.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
        }

        if (pageCount == 0) {
            val path = session.export(document)
            log.info(
                "No next page, selector <${config.lastLastPageCss}>, " +
                        "exported to file://$path"
            )
        } else {
            log.info("There are $pageCount index pages | {}", url)
        }

        return pageCount
    }
}
