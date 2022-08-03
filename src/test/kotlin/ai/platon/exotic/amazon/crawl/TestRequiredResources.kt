package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import org.junit.Test
import kotlin.test.assertTrue

class TestRequiredResources: TestBase() {

    @Test
    fun `Ensure crawl resources exist`() {
        amazonGenerator.periodicalSeedDirectories.map { it.substringBeforeLast("/") }
            .forEach { dir1 -> assertTrue(dir1) { ResourceLoader.exists(dir1) } }

        val sqls = "sites/amazon/crawl/parse/sql"
        assertTrue { ResourceLoader.exists(sqls) }
        assertTrue { ResourceLoader.exists("$sqls/crawl/x-asin.sql") }

        val extractorInstaller = WebDataExtractorInstaller(extractorFactory)
        assertTrue { ResourceLoader.exists(extractorInstaller.jdbcConfig) }
    }

    @Test
    fun `Ensure periodical tasks exist`() {
        amazonGenerator.periodicalSeedDirectories.map { it.substringBeforeLast("/") }
            .forEach { dir -> assertTrue(dir) { ResourceLoader.exists(dir) } }
    }
}
