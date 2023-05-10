package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.generate.DailyAsinGenerator
import ai.platon.exotic.common.ResourceWalker
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTests: TestBase() {

    @Autowired
    lateinit var generator: AmazonGenerator

    @Before
    override fun setup() {
        DailyAsinGenerator.testMode = true

        assertEquals(1, crawlLoops.loops.size)
        PredefinedTask.values().forEach { it.ignoreTTL = true }
        super.setup()
    }

    @Test
    fun testPeriodicalSeedDirectories() {
        val seedDirectories = ResourceWalker().list(AmazonGenerator.PERIODICAL_SEED_RESOURCE_BASE)
            .filter { runCatching { Duration.parse(it.fileName.toString()) }.getOrNull() != null }
            .toList()
        seedDirectories.forEach { println(it) }
        val s = seedDirectories.joinToString()
        assertTrue("pt1h" in s)
        assertTrue("pt24h" in s)
    }
}
