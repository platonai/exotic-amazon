package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.generate.DailyAsinGenerator
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.isRunTime
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.PriorityDataCollectorsTableFormatter
import ai.platon.pulsar.common.getLogger
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestResidentTasks: TestBase() {

    @Autowired
    lateinit var urlLoader: ExternalUrlLoader

    private val logger = getLogger(TestResidentTasks::class)

    private val urlFeeder get() = crawlLoop.urlFeeder

    private val openCollectors get() = urlFeeder.openCollectors

    @Before
    override fun setup() {
        DailyAsinGenerator.testMode = true

        assertEquals(1, crawlLoops.loops.size)
        PredefinedTask.values().forEach { it.ignoreTTL = true }
        super.setup()
    }

    @After
    fun tearDown() {
        DailyAsinGenerator.testMode = false
    }

    @Test
    fun ensurePredefinedTask() {
        assertNotNull(PredefinedTask.values().firstOrNull { it.name == "MOST_WISHED_FOR" })
    }

    @Test
    fun ensureAsinRunTimeIsCorrect() {
        val now = LocalDateTime.now()
        if (now.hour in 14..23) {
            assertNotNull(PredefinedTask.ASIN.toResidentTask().isRunTime())
        }

        if (now.hour < 23) {
            val deadTime = PredefinedTask.ASIN.deadTime()
            assertTrue("deadTime: $deadTime") { deadTime > Instant.now() }
        }
    }

    @Test
    fun ensureBestSellerGeneration() {
        val task = PredefinedTask.BEST_SELLERS.apply { ignoreTTL = true }
        val tasks = listOf(task).map { it.toResidentTask() }
        amazonGenerator.generateLoadingTasks(tasks, true)

        assertTrue { urlFeeder.openCollectors.isNotEmpty() }
        assertTrue { urlFeeder.openCollectors.any { tasks[0].name in it.name } }
//        urlFeeder.openCollectors.forEach { logger.info("Registered collector: {}", it) }
    }

    @Test
    fun ensureMaSGeneration() {
        val tasks = PredefinedTask.values()
                .filter { it.taskPeriod == Duration.ofHours(1) }
                .map { it.toResidentTask() }

        amazonGenerator.generateLoadingTasks(tasks, true)

        assertTrue { openCollectors.isNotEmpty() }
        assertTrue { openCollectors.any { tasks[0].name in it.name } }

        val formatter = PriorityDataCollectorsTableFormatter(openCollectors)
        logger.info("Open collectors: \n{}", formatter)
    }
}
