package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.core.handlers.crawl.CrawlerBeforeLoadHandler
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.common.ConfigurableStreamingCrawler
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.collect.formatAsTable
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.crawl.CrawlLoops
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.scent.ScentSession
import ai.platon.scent.boot.autoconfigure.component.ScentCrawlLoop
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.rest.api.service.v1.ScrapeServiceV1
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AmazonCrawler(
    globalCacheFactory: GlobalCacheFactory,
    scentStatusTracker: ScentStatusTracker,
    session: ScentSession,
    crawlLoops: CrawlLoops,
    private val crawlLoop: ScentCrawlLoop,
    private val amazonGenerator: AmazonGenerator,
    private val scrapeService: ScrapeServiceV1,
) : ConfigurableStreamingCrawler(session, globalCacheFactory, crawlLoops, scentStatusTracker) {

    private val logger = LoggerFactory.getLogger(AmazonCrawler::class.java)
    private val isDev get() = ClusterTools.isDevInstance()

    override var name = "sites/amazon"

    override fun setup() {
        super.setup()

        val dayOfMonth = LocalDate.now().dayOfMonth
        val mod = dayOfMonth % ClusterTools.crawlerCount
        if (ClusterTools.instancePartition == mod) {
            // do initialization stuff, restore unfinished tasks, etc
            scrapeService.initializeAtStartup()
        }
    }

    /**
     * Generate fetch tasks
     * */
    override fun generate() {
        super.generate()

        // In dev mode, we trigger every kind of tasks immediately.
        if (isDev) {
            PredefinedTask.values().forEach {
                it.ignoreTTL = true
                it.deadTime = { DateTimes.doomsday }
                it.startTime = { DateTimes.startOfDay() }
                it.endTime = { DateTimes.endOfDay() }
            }
        }

        amazonGenerator.generateStartupTasks()

        logger.info("Registered collectors: \n{}", formatAsTable(crawlLoop.collectors))
    }

    override fun onBeforeLoad(url: UrlAware) {
        super.onBeforeLoad(url)
        CrawlerBeforeLoadHandler(statusTracker).invoke(url)
    }
}
