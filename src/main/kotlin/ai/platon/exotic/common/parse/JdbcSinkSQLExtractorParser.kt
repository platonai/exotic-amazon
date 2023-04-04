package ai.platon.exotic.common.parse

import ai.platon.exotic.amazon.crawl.boot.JdbcCommitConfig
import ai.platon.exotic.amazon.crawl.boot.component.common.AbstractSQLExtractor
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.sql.SQLTemplate
import ai.platon.pulsar.crawl.parse.ParseFilter
import ai.platon.scent.crawl.serialize.config.v1.CrawlConfig
import ai.platon.scent.crawl.serialize.config.v1.ExtractRule
import ai.platon.scent.jackson.scentObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory

/**
 * The parser to parse config file and create [ParseFilter]s, the [ParseFilter]s are used
 * to extract data from webpages.
 * */
class JdbcSinkSQLExtractorParser(
    private val extractConfigResource: String,
    /**
     * @Deprecated config AmazonJdbcSinkSQLExtractor.jdbcCommitter programmatically.
     * */
    private val jdbcConfigResource: String,
    private val extractorFactory: (JdbcCommitConfig) -> AbstractSQLExtractor
) {
    private val logger = LoggerFactory.getLogger(JdbcSinkSQLExtractorParser::class.java)

    val crawlConfig = scentObjectMapper().readValue<CrawlConfig>(ResourceLoader.readString(extractConfigResource))

    /**
     * Parse the config file and create [AbstractJdbcSinkSQLExtractor]s.
     * */
    fun parse(): List<ParseFilter> {
        // create all extractors
        val extractors = crawlConfig.extractRules.associate { it.id to createParseFilter(it) }
        // add children if exists, we will support extractor tree later
        crawlConfig.extractRules.filter { it.parentId != 0 }
            .map { extractors[it.id] to extractors[it.parentId] }
            .forEach { (child, parent) -> if (parent != null && child != null) parent.addLast(child) }
        // return only root extractors
        return extractors.values.filter { it.parentId == 0 }
    }

    @Throws(IllegalArgumentException::class)
    private fun createParseFilter(rule: ExtractRule): ParseFilter {
        val commitConfig = JdbcCommitConfig(
            name = rule.name,
            minNumNonBlankFields = rule.minNumNonBlankFields
        )
        val sqlTemplate = createSQLTemplate(rule)

        return extractorFactory(commitConfig).also {
            it.name = rule.name
            it.urlFilter = rule.urlPattern.toRegex()
            it.minContentSize = rule.minContentSize
            it.sqlTemplate = sqlTemplate
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun createSQLTemplate(rule: ExtractRule): SQLTemplate {
        val sqlTemplate = rule.sqlTemplate.takeIf { it.endsWith(".sql") }
            ?.let { SQLTemplate.load("${crawlConfig.sqlResourcePrefix}/$it") }
            ?: SQLTemplate(rule.sqlTemplate)

        if (sqlTemplate.template.isBlank()) {
            throw IllegalArgumentException("Illegal sql template | ${rule.sqlTemplate}")
        }

        return sqlTemplate
    }
}
