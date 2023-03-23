package ai.platon.exotic.amazon.tools.scrapers

import ai.platon.exotic.amazon.crawl.core.handlers.parse.AmazonFeatureCalculator
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.common.sql.SQLTemplate
import ai.platon.pulsar.dom.FeatureCalculatorFactory
import ai.platon.pulsar.dom.features.ChainedFeatureCalculator
import ai.platon.scent.ql.h2.context.ScentSQLContexts

class AsinScraper {
    private val context = ScentSQLContexts.create()
    private val session = context.createSession()
    private val urls = """
        # sold by/Ships from/variations
        https://www.amazon.com/dp/B0823BB4RV
        https://www.amazon.com/dp/B08DJ5B94G
    """.trimIndent().split("\n")
        .map { it.trim() }
        .filter { it.startsWith("http") }
    private val url = urls.first()
    private val sqlTemplate = ResourceLoader.readAllLines("sites/amazon/crawl/parse/sql/crawl/x-asin.sql")
//        .map { it.trim() }
//        .filter { it.startsWith("-- ") }
        .joinToString("\n")

    init {
        val calculator = FeatureCalculatorFactory.calculator as? ChainedFeatureCalculator
        calculator?.calculators?.add(AmazonFeatureCalculator())
    }

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
