package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import ai.platon.pulsar.common.ResourceLoader
import org.junit.Test
import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.toPath
import kotlin.streams.toList
import kotlin.test.assertTrue

class TestRequiredResources: TestBase() {
    override var enableCrawlLoop = false

    @Test
    fun `Ensure crawl resources exist`() {
        amazonGenerator.periodicalSeedDirectories.map { it.toString().substringBeforeLast("/") }
            .forEach { dir1 -> assertTrue(dir1) { ResourceLoader.exists(dir1) } }

        val sqls = "sites/amazon/crawl/parse/sql"
        assertTrue { ResourceLoader.exists(sqls) }
        assertTrue { ResourceLoader.exists("$sqls/crawl/x-asin.sql") }

        val extractorInstaller = WebDataExtractorInstaller(extractorFactory)
        assertTrue { ResourceLoader.exists(extractorInstaller.jdbcConfig) }
    }

    @Test
    fun `Ensure periodical tasks exist`() {
        amazonGenerator.periodicalSeedDirectories.map { it.toString().substringBeforeLast("/") }
            .forEach { dir -> assertTrue(dir) { ResourceLoader.exists(dir) } }
    }
}
