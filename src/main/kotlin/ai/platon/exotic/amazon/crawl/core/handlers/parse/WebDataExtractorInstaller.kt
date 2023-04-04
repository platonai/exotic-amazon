package ai.platon.exotic.amazon.crawl.core.handlers.parse

import ai.platon.exotic.amazon.crawl.boot.JdbcCommitConfig
import ai.platon.exotic.amazon.crawl.boot.component.common.AbstractSQLExtractor
import ai.platon.exotic.amazon.crawl.boot.component.AmazonJdbcSinkSQLExtractor
import ai.platon.exotic.common.parse.JdbcSinkSQLExtractorParser
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.crawl.parse.ParseFilter
import ai.platon.pulsar.crawl.parse.ParseFilters
import org.slf4j.LoggerFactory

/**
 * Add a parse filter to sync extracted records to the JDBC sink, for example, a MySQL database.
 * */
class WebDataExtractorInstaller(
        private val extractorFactory: (JdbcCommitConfig) -> AbstractSQLExtractor
) {
    private val logger = LoggerFactory.getLogger(WebDataExtractorInstaller::class.java)

    /**
     * @Deprecated config AmazonJdbcSinkSQLExtractor.jdbcCommitter programmatically.
     * */
    val jdbcConfig = "config/jdbc-sink-config.json"
    val extractConfig = "sites/amazon/crawl/parse/extract-config.json"

    fun install(parseFilters: ParseFilters) {
        ResourceLoader.getResource(extractConfig) ?: return

        logger.info("Initializing extractors, create extractors from config file | {}", extractConfig)

        val configParser = JdbcSinkSQLExtractorParser(extractConfig, jdbcConfig, extractorFactory)

        val parsers = configParser.parse()
        parsers.forEach {
            it.initialize()
            parseFilters.addLast(it)
        }

        reportExtractor(parseFilters)
    }

    private fun reportExtractor(parseFilters: ParseFilters) {
        val sb = StringBuilder()
        parseFilters.parseFilters.filterIsInstance<AmazonJdbcSinkSQLExtractor>().forEach {
            reportExtractor(it, 0, sb)
        }
        logger.info("Installed SQL extractors: \n$sb")
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
