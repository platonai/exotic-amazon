package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.crawl.CrawlLoops
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.scent.ScentSession
import ai.platon.scent.boot.autoconfigure.component.ScentCrawlLoop
import ai.platon.scent.boot.test.ScentBootTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@ScentBootTest
open class TestBase {
    val productUrl = "https://www.amazon.com/dp/B07V2CLJLV"

    @Autowired
    lateinit var session: ScentSession

    @Autowired
    lateinit var globalCacheFactory: GlobalCacheFactory

    @Autowired
    lateinit var crawlLoops: CrawlLoops

    @Autowired
    lateinit var crawlLoop: ScentCrawlLoop

    var enableCrawlLoop = true

    val globalCache get() = globalCacheFactory.globalCache

    @Before
    fun setup() {
        assertEquals("ScentCrawlLoop", crawlLoop.name)

        assertTrue { crawlLoops.loops.isNotEmpty() }
        assertEquals(crawlLoop, crawlLoops.first())

        // assertTrue { crawlLoop.enableDefaultCollectors }
//        assertTrue { crawlLoop.isRunning }

        val collectorCount = crawlLoop.urlFeeder.openCollectors.size
        assertTrue { crawlLoop.urlFeeder.openCollectors.isNotEmpty() }

        // remove all assigned items
        crawlLoop.urlFeeder.clear()
        // ensure the clear removes fetch items, not collectors
        assertTrue { crawlLoop.urlFeeder.openCollectors.size == collectorCount }

        if (enableCrawlLoop) {
            crawlLoops.restart()
        } else {
            crawlLoops.stop()
        }
    }

    @Test
    fun smoke() {

    }
}
