package ai.platon.exotic.amazon.starter

import ai.platon.exotic.amazon.crawl.boot.CrawlerInitializer
import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.exotic.common.ClusterTools
import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.LinkExtractors
import ai.platon.pulsar.common.StartStopRunner
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.crawl.DefaultPulsarEventHandler
import ai.platon.pulsar.crawl.common.url.ListenableHyperlink
import ai.platon.pulsar.dom.select.selectHyperlinks
import ai.platon.pulsar.persist.HadoopUtils
import ai.platon.pulsar.protocol.browser.driver.WebDriverPoolMonitor
import ai.platon.scent.ScentSession
import ai.platon.scent.protocol.browser.emulator.context.BrowserPrivacyContextMonitor
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ImportResource

@SpringBootApplication(
    scanBasePackages = [
        "ai.platon.scent.boot.autoconfigure",
        "ai.platon.scent.rest.api",
        "ai.platon.exotic.amazon.crawl.boot",
    ]
)
@ImportResource("classpath:config/app/app-beans/app-context.xml")
class CrawlApplication(
    private val appMetrics: AppMetrics,
    private val driverPoolMonitor: WebDriverPoolMonitor,
    private val privacyContextMonitor: BrowserPrivacyContextMonitor,
    private val amazonGenerator: AmazonGenerator,
    private val amazonCrawler: AmazonCrawler,
    private val session: ScentSession
) {
    private val logger = getLogger(CrawlApplication::class.java)
    private var submittedProductUrlCount = 0
    private val globalCache = session.globalCacheFactory.globalCache

    @Bean
    fun checkConfiguration() {
        val conf = session.unmodifiedConfig

        logger.info("{}", conf)
        val hadoopConf = HadoopUtils.toHadoopConfiguration(conf)
        logger.info("{}", hadoopConf)
    }

    /**
     * Initialize and start amazon crawler
     * */
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun startAmazonCrawler(): StartStopRunner {
        return StartStopRunner(amazonCrawler)
    }

    /**
     * Initialize and start amazon crawler
     * */
    @Bean
    fun injectSeeds() {
        val eventHandler = DefaultPulsarEventHandler()
        eventHandler.loadEventHandler.onHTMLDocumentParsed.addFirst { page, document ->
            val normalizer = AsinUrlNormalizer()
            val args = "-expires 100d -requireSize 300000 -parse -label asin"
            val urls = document.document.selectHyperlinks(".p13n-gridRow a[href*=/dp/]:has(img)")
                .distinct()
                .map { Hyperlink(normalizer.invoke(it.url)!!, args = args).apply { href = it.url } }

            val queue = globalCache.urlPool.normalCache.nonReentrantQueue
            urls.forEach { queue.add(it) }

            submittedProductUrlCount += urls.size
            logger.info("{}.\tSubmitted {}/{} product links", page.id, urls.size, submittedProductUrlCount)
        }

        val resource = "sites/amazon/crawl/generate/periodical/p7d/best-sellers.txt"
        val urls = LinkExtractors.fromResource(resource).map { "$it -requireSize 300000 -parse" }
        val queue = globalCache.urlPool.normalCache.nonReentrantQueue
        urls.map { ListenableHyperlink(it, eventHandler = eventHandler) }.forEach { queue.add(it) }
    }
}

fun main(args: Array<String>) {
    // Backend storage is detected automatically but not on some OS such as Mac,
    // uncomment the following line to force MongoDB to be used as the backend storage
    System.setProperty(CapabilityTypes.STORAGE_DATA_STORE_CLASS, AppConstants.MONGO_STORE_CLASS)
//    BrowserSettings.privacy(2).maxTabs(8)

    val isDev = ClusterTools.isDevInstance()
    // In dev mode, we trigger every kind of tasks immediately.
    if (isDev) {
        PredefinedTask.values().forEach {
//            it.refresh = true
            it.ignoreTTL = true
            it.deadTime = { DateTimes.doomsday }
            it.startTime = { DateTimes.startOfDay() }
            it.endTime = { DateTimes.endOfDay() }
        }
    }

    val additionalProfiles = mutableListOf("rest", "crawler")
    val prod = System.getenv("ENV")?.lowercase()
    if (prod == "prod") {
        additionalProfiles.add("prod")
    }

    runApplication<CrawlApplication>(*args) {
        setAdditionalProfiles(*additionalProfiles.toTypedArray())
        addInitializers(CrawlerInitializer())
        setRegisterShutdownHook(true)
        setLogStartupInfo(true)
    }
}
