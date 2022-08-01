package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.exotic.amazon.crawl.core.handlers.jdbc.JdbcSinkRegistry
import ai.platon.exotic.amazon.crawl.boot.component.MainCrawler
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertTrue

class TestRequiredResources: TestBase() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var crawler: MainCrawler

    @Test
    fun `Ensure crawl resources exist`() {
        val dir = crawler.sequentialSeedDirectory
        assertTrue(dir) { ResourceLoader.exists(dir) }
        crawler.periodicalSeedDirectories.map { it.substringBeforeLast("/") }
            .forEach { dir1 -> assertTrue(dir1) { ResourceLoader.exists(dir1) } }

        val sqls = "config/sites/amazon/crawl/parse/sql"
        assertTrue { ResourceLoader.exists(sqls) }
        assertTrue { ResourceLoader.exists("$sqls/crawl/x-asin.sql") }

        val initializer = JdbcSinkRegistry(applicationContext)
        assertTrue { ResourceLoader.exists(initializer.jdbcResource) }
    }

    @Test
    fun `Ensure periodical tasks exist`() {
        crawler.periodicalSeedDirectories.map { it.substringBeforeLast("/") }
            .forEach { dir -> assertTrue(dir) { ResourceLoader.exists(dir) } }
    }
}
