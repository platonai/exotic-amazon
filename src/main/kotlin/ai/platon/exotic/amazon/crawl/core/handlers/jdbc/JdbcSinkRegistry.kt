package ai.platon.exotic.amazon.crawl.core.handlers.jdbc

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.crawl.parse.ParseFilter
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.exotic.amazon.crawl.boot.component.JdbcSinkSQLExtractor
import ai.platon.scent.common.ASSEConfig
import ai.platon.scent.crawl.serialize.config.v1.CrawlConfig
import ai.platon.scent.crawl.serialize.config.v1.JdbcConfig
import ai.platon.scent.jackson.scentObjectMapper
import ai.platon.scent.parse.html.CrawlConfigParser
import ai.platon.scent.parse.html.DefaultApiSinkSQLExtractor
import ai.platon.scent.parse.html.JdbcCommitConfig
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext

/**
 * Add a parse filter to sync extracted records to the JDBC sink, for example, a MySQL database.
 * */
class JdbcSinkRegistry(
        val applicationContext: ApplicationContext
) {
    private val log = LoggerFactory.getLogger(JdbcSinkRegistry::class.java)

    val jdbcResource = "config/sites/amazon/db/jdbc/config.json"
    val configResource = "config/sites/amazon/crawl/parse/extract-config.json"

    val parseFilters: ParseFilters get() = applicationContext.getBean()

    val jdbcSinkSqlExtractorFactory get() = { conf: JdbcCommitConfig ->
        applicationContext.getBean<JdbcSinkSQLExtractor>()
    }

    val apiSinkSqlExtractorFactory get() = { _: ASSEConfig ->
        applicationContext.getBean<DefaultApiSinkSQLExtractor>()
    }

    fun register() {
        installExtractors()
    }

    private fun installExtractors() {
        log.info("Initializing amazon extractors, create extractors from config file | {}", configResource)

        ResourceLoader.getResource(configResource) ?: return

        val jdbcConfig = scentObjectMapper().readValue<JdbcConfig>(ResourceLoader.readString(jdbcResource))
        val crawlConfig = scentObjectMapper().readValue<CrawlConfig>(ResourceLoader.readString(configResource))

        val configParser = CrawlConfigParser(crawlConfig, jdbcConfig, null,
            jdbcSinkSqlExtractorFactory, apiSinkSqlExtractorFactory)

        val parsers = configParser.parse()
        parsers.forEach {
            it.initialize()
            parseFilters.addLast(it)
        }

        reportExtractor(parseFilters)
    }

    private fun reportExtractor(parseFilters: ParseFilters) {
        val sb = StringBuilder()
        parseFilters.parseFilters.filterIsInstance<JdbcSinkSQLExtractor>().forEach {
            reportExtractor(it, 0, sb)
        }
        log.info("Installed jdbc sink SQL extractors: \n$sb")
    }

    private fun reportExtractor(filter: ParseFilter, depth: Int, sb: StringBuilder) {
        val padding = if (depth > 0) "  ".repeat(depth) else ""
        sb.appendLine("${padding}$filter")
        filter.children.forEach {
            reportExtractor(it, depth + 1, sb)
        }

        // logger.info("Created committer to $tableName | $jdbcConfig")
    }
}
