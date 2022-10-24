package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.handlers.parse.AmazonFeatureCalculator
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.persist.ext.options
import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.crawl.common.url.CompletableListenableHyperlink
import ai.platon.pulsar.dom.FeatureCalculatorFactory
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.features.CombinedFeatureCalculator
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.ql.context.SQLContexts
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestExtractProblems: TestBase() {

    @Test
    fun `Extract summarization attributes`() {
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

//        val rs = session.context.executeQuery(sql)
//        println(ResultSetFormatter(rs))
    }
}
