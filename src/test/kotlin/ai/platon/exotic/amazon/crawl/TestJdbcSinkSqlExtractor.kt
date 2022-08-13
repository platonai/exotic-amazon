package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.common.sql.SQLTemplate
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.pulsar.crawl.parse.html.ParseContext
import ai.platon.pulsar.ql.h2.utils.ResultSetUtils
import ai.platon.exotic.amazon.crawl.crawl.common.SimpleParseFilter
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import ai.platon.exotic.amazon.crawl.boot.component.AmazonJdbcSinkSQLExtractor
import org.junit.Ignore
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Ignore("Make sure JDBC sink is available")
class TestJdbcSinkSqlExtractor: TestBase() {

    private val args = "-i 100d -parse"
    private val resourcePrefix = "sites/amazon/crawl/parse/sql"
    private val sqlSource = "$resourcePrefix/crawl/x-asin.sql"

    @Autowired
    private lateinit var sqlExtractor: AmazonJdbcSinkSQLExtractor

    @Autowired
    private lateinit var parseFilters: ParseFilters

    @Test
    fun `When filter by sql extractor then sql is executed`() {
        assertTrue { !sqlExtractor.hasSink }

        val options = session.options(args)
        options.expires = Duration.ZERO
        val page = session.load(productUrl, options)
        assertTrue { page.protocolStatus.isSuccess }

        val document = session.parse(page)

        sqlExtractor.sqlTemplate = SQLTemplate.load(sqlSource, "asin")
        val parseFilter = SimpleParseFilter().apply { addLast(sqlExtractor) }
        val parseContext = ParseContext(page, document = document)
        val relevant = sqlExtractor.isRelevant(parseContext)
        assertTrue(relevant.message) { relevant.isOK }

        parseFilter.filter(parseContext)
        assertNotNull(sqlExtractor.lastResultSet)
    }

    @Test
    fun `When apply AmazonJdbcSinkSqlExtractor then all relevant SQLs are executed`() {
        WebDataExtractorInstaller(extractorFactory).install(parseFilters)

        val page = session.load(productUrl, args + " -i 0s")
        assertTrue { page.protocolStatus.isSuccess }

        val document = session.parse(page)

        val asinExtractor = parseFilters.parseFilters
            .filterIsInstance<AmazonJdbcSinkSQLExtractor>()
            .first { it.name == "asin" }
        val children = asinExtractor.children.filterIsInstance<AmazonJdbcSinkSQLExtractor>()
        assertEquals(7, children.size)

        children.forEach {
            if (it.meterFitRecords.count > 0) {
                assertNotNull(it.lastRelevantState)
                assertTrue { it.meterChecks.count > 0 }
                assertTrue { it.meterRelevantTasks.count > 0 }
            }

            val report = String.format("%s state: %s checks: %d relevant: %d fit: %d",
                it.name, it.lastRelevantState?.code, it.meterChecks.count, it.meterRelevantTasks.count, it.meterFitRecords.count)
            println(report)

            val rs = it.lastResultSet
            if (rs != null) {
                println("count: " + ResultSetUtils.count(rs))
                rs.beforeFirst()
                println(ResultSetFormatter(rs).toString())
            }
        }
    }
}
