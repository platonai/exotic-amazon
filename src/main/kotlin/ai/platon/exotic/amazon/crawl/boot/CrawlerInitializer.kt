package ai.platon.exotic.amazon.crawl.boot

import ai.platon.exotic.amazon.crawl.core.handlers.parse.AmazonFeatureCalculator
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.options.LoadOptionDefaults
import ai.platon.pulsar.crawl.CrawlLoops
import ai.platon.pulsar.dom.FeatureCalculatorFactory
import ai.platon.pulsar.dom.features.ChainedFeatureCalculator
import ai.platon.pulsar.protocol.browser.emulator.BrowserResponseHandler
import ai.platon.scent.ScentEnvironment
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.AbstractApplicationContext

class CrawlerInitializer: ApplicationContextInitializer<AbstractApplicationContext> {
    companion object {
        init {
            LoadOptionDefaults.apply {
                /**
                 * Do not store the content of webpages for large scale crawling
                 * */
                storeContent = true
            }
        }
    }

    private val logger = LoggerFactory.getLogger(CrawlerInitializer::class.java)

    override fun initialize(applicationContext: AbstractApplicationContext) {
        ScentEnvironment().checkEnvironment()

        mapOf(
//            CapabilityTypes.BROWSER_EMULATOR_EVENT_HANDLER to "ai.platon.exotic.amazon.crawl.core.handlers.fetch.AmazonEmulateEventHandler",
            CapabilityTypes.PROXY_LOADER_CLASS to "ai.platon.exotic.common.proxy.ProxyVendorLoader",
            CapabilityTypes.FETCH_MAX_RETRY to "3"
        ).forEach { (key, value) -> System.setProperty(key, value) }

        logger.info("Initializing feature calculator, append AmazonFeatureCalculator")

        val calculator = FeatureCalculatorFactory.calculator as? ChainedFeatureCalculator
        calculator?.calculators?.add(AmazonFeatureCalculator())

        // ignore the default crawl loop, ScentCrawlLoop is expected
        CrawlLoops.filters.add { loop -> loop.name != "DefaultCrawlLoop" }
    }
}
