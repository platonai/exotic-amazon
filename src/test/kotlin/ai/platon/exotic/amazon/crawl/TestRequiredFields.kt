package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.AmazonFeatureCalculator
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.persist.ext.options
import ai.platon.pulsar.crawl.common.url.CompletableListenableHyperlink
import ai.platon.pulsar.dom.FeatureCalculatorFactory
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.features.CombinedFeatureCalculator
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestRequiredFields: TestBase() {

    val defaultArgs = "-i 1d -ignoreFailure -parse"
    val jsVariableNames =
        listOf("parentAsin", "num_total_variations", "dimensionValuesDisplayData", "dimensionsDisplay")

    private val fetchQueue get() = globalCache.urlPool.normalCache.nReentrantQueue

    @Test
    fun `When call AmazonFeatureCalculator than pulsarJsVariables exists`() {
        val calculator = FeatureCalculatorFactory.calculator as? CombinedFeatureCalculator
        calculator?.calculators?.add(AmazonFeatureCalculator())

        val page = session.load(productUrl, defaultArgs)
        assertTrue(page.protocolStatus.toString()) { page.protocolStatus.isSuccess }
        val document = session.parse(page)
        val variables = document.selectFirstOrNull("#pulsarJsVariables")

        if (page.lastBrowser != BrowserType.MOCK_CHROME) {
            assertNotNull(variables)
            jsVariableNames.forEach { name ->
                val value = variables.selectFirstOrNull(".$name")
                assertNotNull(value) { "Variable $name should exist" }
            }
        }
    }

    @Test
    fun `When load with StreamingCrawler than referer exists`() {
        val referrer = "https://www.amazon.com/"
        val url = CompletableListenableHyperlink<WebPage>(productUrl, args = defaultArgs).also {
            it.referer = referrer
            it.eventHandler.loadEventHandler.onAfterLoad.addLast {
                assertEquals(referrer, it.referrer)
            }
        }
        fetchQueue.add(url)
    }

    @Test
    fun `When generate secondary labeled portal link then the arguments are inherited`() {
        val label = PredefinedTask.BEST_SELLERS.label
        val randomIdent = 3872197
        val portalUrl = "https://www.amazon.com/Best-Sellers-Beauty/zgbs/beauty/ref=zg_bs_nav_0?i=$randomIdent"
        val args = "$defaultArgs -label $label"

        val url = CompletableListenableHyperlink<WebPage>(portalUrl, args = args)
        url.eventHandler.loadEventHandler.onAfterHtmlParse.addLast { page, document ->
            assertEquals(PredefinedTask.BEST_SELLERS.label, label)
            collectSecondaryLabeledPortalPage(page, document)
        }
        url.eventHandler.crawlEventHandler.onAfterLoad.addFirst { u, page ->
            url.complete(page)
        }

        fetchQueue.add(url)

        url.get(120, TimeUnit.MINUTES)

        assertTrue { url.isDone }
    }

    private fun collectSecondaryLabeledPortalPage(page: WebPage, document: FeaturedDocument) {
        val label = page.label
        assertEquals(PredefinedTask.BEST_SELLERS.label, label)
        // Collect the hyperlink of the next page
        val url = document.selectFirst("ul.a-pagination li.a-last a[href~=$label]").attr("abs:href")

        val hyperlink = CompletableListenableHyperlink<WebPage>(url, args = page.args, referer = page.url)
        hyperlink.eventHandler.crawlEventHandler.onAfterLoad.addLast { u, page2 ->
            if (page2 == null) {
                return@addLast
            }

            val options = page.options
            val options2 = page2.options

            assertEquals(options.expireAt, options2.expireAt)
            assertEquals(options.label, options2.label)
            assertEquals(options.ignoreFailure, options2.ignoreFailure)

            assertEquals(page.args, page2.args)

            hyperlink.complete(page2)
        }

        fetchQueue.add(hyperlink)
    }
}
