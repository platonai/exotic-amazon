package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.ql.h2.utils.ResultSetUtils
import ai.platon.scent.ql.h2.context.ScentSQLContexts
import org.junit.Test
import java.sql.ResultSet
import kotlin.test.assertTrue

class TestExtractProblems {

    private val context = ScentSQLContexts.create()

    private val url = "https://www.amazon.com/dp/B07TWFVDWT"
    private val args = "-i 1d -ignoreFailure"
    private val configuredUrl = "$url $args"

    private fun executeQuery(sql: String, verbose: Boolean = true): ResultSet {
        val rs = context.executeQuery(sql)

        println(ResultSetFormatter(rs))

        return rs
    }

    @Test
    fun `Extract summarization attributes`() {
        val restrictCss = "div[data-hook=cr-summarization-attributes-list] div[data-hook=cr-summarization-attribute]," +
            " div[data-hook=cr-summarization-attributes-expanded] div[data-hook=cr-summarization-attribute]"
        val sql = """
select
    dom_first_text(dom, 'div span:expr(char > 5)'),
    dom_first_text(dom, 'i > span:expr(char <= 3)')
from
    load_and_select('$configuredUrl', '$restrictCss');
        """.trimIndent()

        val rs = executeQuery(sql)
        // assertTrue { ResultSetUtils.count(rs) >= 3 }
    }
}
