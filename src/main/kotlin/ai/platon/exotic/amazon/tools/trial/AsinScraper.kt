package ai.platon.exotic.amazon.tools.trial

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.common.sql.SQLTemplate
import ai.platon.scent.ql.h2.context.ScentSQLContexts

class AsinScraper {
    private val context = ScentSQLContexts.create()
    private val session = context.createSession()
    private val url = "https://www.amazon.com/dp/B08DJ5B94G"
    private val sqlTemplate = ResourceLoader.readAllLines("sites/amazon/crawl/parse/sql/crawl/x-asin-customer-hui.sql")
//        .map { it.trim() }
//        .filter { it.startsWith("-- ") }
        .joinToString("\n")

    fun scrape() {
        val sql = SQLTemplate(sqlTemplate).createSQL(url)
        val rs = context.executeQuery(sql)
        val formatter = ResultSetFormatter(rs, asList = true, withHeader = true)
        println(formatter)
    }
}

fun main() {
    val crawler = AsinScraper()
    crawler.scrape()
}
