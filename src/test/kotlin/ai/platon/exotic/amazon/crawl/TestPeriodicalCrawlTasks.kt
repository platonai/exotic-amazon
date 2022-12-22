package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.collect.UrlFeederHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.PriorityDataCollectorsTableFormatter
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.sleepSeconds
import ai.platon.pulsar.persist.WebDb
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.scent.boot.autoconfigure.component.LoadingSeedsGenerator
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestPeriodicalCrawlTasks: TestBase() {

    private val logger = getLogger(this::class)

    @Autowired
    private lateinit var urlLoader: ExternalUrlLoader

    @Autowired
    private lateinit var parseFilters: ParseFilters

    override var enableCrawlLoop = false

    private val urlFeederHelper get() = UrlFeederHelper(crawlLoop.urlFeeder)

    @Before
    override fun setup() {
        WebDataExtractorInstaller(extractorFactory).install(parseFilters)
        super.setup()
    }
}
