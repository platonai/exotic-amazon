package ai.platon.exotic.common.diffusing

import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.collect.UrlCache
import ai.platon.pulsar.common.urls.PlainUrl
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.exotic.common.diffusing.config.DiffusingCrawlerConfig
import org.slf4j.LoggerFactory
import java.net.URL

interface DiffusingCrawler {
    val label: String
    val home: String
    val globalCacheFactory: GlobalCacheFactory
    val defaultFetchCache: UrlCache

    val navigationProcessor: NavigationProcessor
    val indexPageProcessor: IndexHyperlinkProcessor
    val itemPageProcessor: ItemHyperlinkProcessor

    fun generate(): Set<UrlAware>
    fun generateTo(sink: MutableCollection<UrlAware>)

    @Deprecated("Use LoadEventHandler instead")
    fun onAfterHtmlParse(page: WebPage, document: FeaturedDocument)
}

/**
 * Diffusing from an portal url
 * */
abstract class AbstractDiffusingCrawler(
    val config: DiffusingCrawlerConfig,
    val session: ScentSession,
    override val globalCacheFactory: GlobalCacheFactory
) : DiffusingCrawler {
    private val log = LoggerFactory.getLogger(NavigationProcessor::class.java)

    override val label: String get() = config.label
    override val home = URL(config.portalUrl).let { it.protocol + "://" + it.host }
    val globalCache get() = globalCacheFactory.globalCache
    override val defaultFetchCache get() = globalCache.urlPool.normalCache

    override fun generate(): Set<UrlAware> {
        val sink = mutableSetOf<UrlAware>()
        generateTo(sink)
        return sink
    }

    override fun onAfterHtmlParse(page: WebPage, document: FeaturedDocument) {
        val cache = globalCache.urlPool.normalCache
        document.select(config.indexPageItemLinkSelector).forEach {
            itemPageProcessor.collectTo(it, cache.nonReentrantQueue)
        }

        navigationProcessor.collectTo(document, cache.nonReentrantQueue)
    }

    fun report(page: WebPage, document: FeaturedDocument) {
        val pageId = page.id
        val url = page.url
        val href = page.href
        val reviewsUrl = document.selectFirstOrNull("#reviews-medley-footer a")?.attr("abs:href")
        val cssSelector =
            "#acrCustomerReviewText, #reviewsMedley div[data-hook=total-review-count] span, #reviewsMedley div[data-hook] span:contains(global ratings), #reviewsMedley span:contains(global reviews)"
        val reviewString = document.selectFirstOrNull(cssSelector)?.text() ?: "0"
        val reviewNumber = Strings.getLastInteger(reviewString, 0)
        // messageWriter.reportLoadedItemLinks("$pageId | $reviewNumber | $reviewString | $url | $href | $reviewsUrl")
    }
}

class DefaultDiffusingCrawler(
    config: DiffusingCrawlerConfig,
    session: ScentSession,
    globalCacheFactory: GlobalCacheFactory
) : AbstractDiffusingCrawler(config, session, globalCacheFactory) {

    override val label: String get() = config.label
    override val indexPageProcessor = IndexHyperlinkProcessor(config, session)
//        .apply { eventHandler.onAfterHtmlParse.addLast { page, document -> onAfterHtmlParse(page, document) } }
    override val itemPageProcessor = ItemHyperlinkProcessor(config, session)
    override val navigationProcessor = NavigationProcessor(config, indexPageProcessor, session)

    override fun generateTo(sink: MutableCollection<UrlAware>) {
        session.loadDocument(config.portalUrl, "-i 1s -ignoreFailure")
            .select(config.portalPageIndexLinkCss)
            .map { it.attr("abs:href") }
            .mapTo(sink) { PlainUrl(it) }
    }
}
