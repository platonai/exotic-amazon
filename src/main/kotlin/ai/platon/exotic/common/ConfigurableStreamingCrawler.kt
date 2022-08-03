package ai.platon.exotic.common

import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.context.PulsarContexts
import ai.platon.pulsar.context.support.AbstractPulsarContext
import ai.platon.pulsar.crawl.CrawlLoops
import ai.platon.pulsar.crawl.StreamingCrawlLoop
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.protocol.browser.emulator.BrowserEmulatedFetcher
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.crawl.AbstractRunnableStreamingCrawler

open class ConfigurableStreamingCrawler(
    session: ScentSession,
    globalCacheFactory: GlobalCacheFactory,
    crawlLoops: CrawlLoops,
    scentStatusTracker: ScentStatusTracker,
) : AbstractRunnableStreamingCrawler(session, globalCacheFactory, crawlLoops, scentStatusTracker) {

    override fun start() {
        crawlLoops.loops.filterIsInstance<StreamingCrawlLoop>().forEach { loop ->
            loop.crawlEventHandler.also {
                it.onBeforeLoad.addFirst { url -> onBeforeLoad(url) }
                it.onAfterLoad.addFirst { url, page -> onAfterLoad(url, page) }
            }
        }
        super.start()
    }

    override fun setup() {
        super.setup()

        val context = session.context as AbstractPulsarContext
        requireNotNull(context.getBeanOrNull<BrowserEmulatedFetcher>())
    }

    /**
     * Inject seeds from default locations
     * */
    override fun inject() {

    }

    open fun onFilter(url: UrlAware) {

    }

    open fun onNormalize(url: UrlAware) {

    }

    open fun onBeforeLoad(url: UrlAware) {

    }

    open fun onAfterLoad(url: UrlAware, page: WebPage?) {
    }
}
