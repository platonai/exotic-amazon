package ai.platon.exotic.amazon.crawl.boot

import ai.platon.pulsar.common.options.LoadOptionDefaults
import ai.platon.pulsar.dom.FeatureCalculatorFactory
import ai.platon.pulsar.dom.features.CombinedFeatureCalculator
import ai.platon.exotic.amazon.crawl.core.handlers.parse.AmazonFeatureCalculator
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.crawl.CrawlLoops
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
                storeContent = false
            }
        }
    }

    private val logger = LoggerFactory.getLogger(CrawlerInitializer::class.java)

    override fun initialize(applicationContext: AbstractApplicationContext) {
        ScentEnvironment().checkEnvironment()

        // set emulate event handler to be AmazonEmulateEventHandler
        System.setProperty(
            CapabilityTypes.BROWSER_EMULATOR_EVENT_HANDLER, "ai.platon.exotic.amazon.crawl.core.handlers.fetch.AmazonEmulateEventHandler")
        System.setProperty(CapabilityTypes.FETCH_MAX_RETRY, "3")

        logger.info("Initializing feature calculator, append AmazonFeatureCalculator")

        val calculator = FeatureCalculatorFactory.calculator as? CombinedFeatureCalculator
        calculator?.calculators?.add(AmazonFeatureCalculator())

        // ignore default crawl loop, ScentCrawlLoop is expected
        CrawlLoops.filters.add { loop -> loop.name != "DefaultCrawlLoop" }
    }
}
