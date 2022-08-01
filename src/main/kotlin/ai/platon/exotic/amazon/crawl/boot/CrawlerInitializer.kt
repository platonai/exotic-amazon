package ai.platon.exotic.amazon.crawl.boot

import ai.platon.pulsar.common.options.LoadOptionDefaults
import ai.platon.pulsar.dom.FeatureCalculatorFactory
import ai.platon.pulsar.dom.features.CombinedFeatureCalculator
import ai.platon.exotic.amazon.crawl.common.AmazonFeatureCalculator
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
        installFeatureCalculators()
    }

    private fun installFeatureCalculators() {
        logger.info("Initializing feature calculator, append AmazonFeatureCalculator")

        val calculator = FeatureCalculatorFactory.calculator as? CombinedFeatureCalculator
        calculator?.calculators?.add(AmazonFeatureCalculator())
    }
}
