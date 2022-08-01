package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.crawl.AbstractCrawlLoop
import ai.platon.pulsar.crawl.CrawlLoops
import ai.platon.pulsar.crawl.StreamingCrawlLoop
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.crawl.AbstractRunnableStreamingCrawler

open class ConfigurableStreamingCrawler(
    session: ScentSession,
    globalCacheFactory: GlobalCacheFactory,
    crawlLoops: CrawlLoops,
    scentStatusTracker: ScentStatusTracker,
) : AbstractRunnableStreamingCrawler(session, globalCacheFactory, crawlLoops, scentStatusTracker) {

    companion object {

        const val sequentialSeedDirectoryTemplate = "config/sites/{project}/crawl/inject/seeds"

        const val periodicalSeedResourceDirectoryTemplate = "config/sites/{project}/crawl/generate/periodical/{period}"
    }

    override var name = "default"

    val sequentialSeedDirectory get() = sequentialSeedDirectoryTemplate.replace("{project}", name)

    val periodicalSeedDirectories get() = listOf("pt30m", "pt1h", "pt12h", "pt24h")
        .map { buildPeriodicalSeedDirectory(name, it) }

    private val defaultLoadOptions get() = LoadOptions.create(session.sessionConfig)

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
        crawlLoops.loops.filterIsInstance<AbstractCrawlLoop>().forEach { it.defaultOptions = defaultLoadOptions }
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

    private fun buildPeriodicalSeedDirectory(projectName: String, duration: String): String {
        return periodicalSeedResourceDirectoryTemplate
            .replace("{project}", projectName)
            .replace("{period}", duration)
    }
}
