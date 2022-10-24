package ai.platon.exotic.amazon.tools.trial

import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.ql.context.SQLContexts

class TrialCrawler {
    val context = SQLContexts.create()
    val session = context.createSession()

    fun executeQuery() {
        val url = "https://www.amazon.com/dp/B07TWFVDWT -i 1d -ignoreFailure"

        val restrictCss = "div[data-hook=cr-summarization-attributes-list] div[data-hook=cr-summarization-attribute], div[data-hook=cr-summarization-attributes-expanded] div[data-hook=cr-summarization-attribute]"
        val sql = """
select
    dom_first_text(dom, 'div > span:expr(char > 5)'),
    dom_first_text(dom, 'i > span:expr(char <= 3)')
from
    load_and_select('$url', '$restrictCss');
        """.trimIndent()

        println(sql)

        val rs = context.executeQuery(sql)
        println(ResultSetFormatter(rs))
    }
}

fun main() {
    val crawler = TrialCrawler()
    crawler.executeQuery()
}
