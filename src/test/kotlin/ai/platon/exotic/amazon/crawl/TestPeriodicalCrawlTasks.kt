package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.collect.CollectorHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.PriorityDataCollectorsTableFormatter
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.sleepSeconds
import ai.platon.pulsar.persist.WebDb
import ai.platon.exotic.amazon.crawl.core.handlers.jdbc.JdbcSinkRegistry
import ai.platon.exotic.amazon.crawl.boot.component.MainCrawler
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.scent.boot.autoconfigure.component.LoadingSeedsGenerator
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestPeriodicalCrawlTasks: TestBase() {

    private val logger = getLogger(this::class)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var mainCrawler: MainCrawler

    @Autowired
    private lateinit var urlLoader: ExternalUrlLoader

    @Autowired
    private lateinit var webDb: WebDb

    override var enableCrawlLoop = false

    private val collectorHelper get() = CollectorHelper(crawlLoop.urlFeeder)

    @Before
    override fun setup() {
        JdbcSinkRegistry(applicationContext).register()
        super.setup()
    }

    @Test
    fun `When periodical tasks generated then the args are correct`() {
        val predefinedTasks = listOf(PredefinedTask.BEST_SELLERS, PredefinedTask.NEW_RELEASES, PredefinedTask.MOST_WISHED_FOR)
        val tasks = predefinedTasks.map { it.toResidentTask() }.onEach { it.ignoreTTL = true }.take(10)
        val generator = LoadingSeedsGenerator(tasks, mainCrawler.periodicalSeedDirectories, collectorHelper, urlLoader, webDb)
        val collectors = generator.generate(true).shuffled()
        val now = DateTimes.startOfDay()

        collectors.forEach {
            // for this test
            val args = it.urlCache.nonReentrantQueue.peek().args
            assertNotNull(args)
            assertTrue { args.contains("-parse") }
            assertTrue { args.contains(now.toString()) }
            logger.info("Running periodical crawl task <{} {}>", it.name, args)
        }

        assertTrue { collectors.isNotEmpty() }
        assertTrue { collectors.sumBy { it.size } > 0 }

        collectorHelper.addAll(collectors)
        var i = 10
        while (i-- > 0 && collectors.sumBy { it.size } > 0) {
            sleepSeconds(1)
        }

        println(PriorityDataCollectorsTableFormatter(collectors))
    }
}
