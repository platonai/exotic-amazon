package ai.platon.exotic.amazon.starter

import ai.platon.exotic.amazon.crawl.boot.CrawlerInitializer
import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.*
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
import ai.platon.pulsar.common.urls.PlainUrl
import ai.platon.pulsar.crawl.event.impl.DefaultPageEvent
import ai.platon.pulsar.dom.select.selectHyperlinks
import ai.platon.pulsar.protocol.browser.driver.BrowserMonitor
import ai.platon.pulsar.protocol.browser.driver.WebDriverPoolMonitor
import ai.platon.scent.ScentSession
import ai.platon.scent.protocol.browser.emulator.context.BrowserPrivacyContextMonitor
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ImportResource
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 *
 * Note: Official answer say that scanBasePackages is the upgraded version of ComponentScan.
 * */
@SpringBootApplication(
    scanBasePackages = [
        "ai.platon.scent.boot.autoconfigure",
        "ai.platon.scent.rest.api",
        "ai.platon.exotic.amazon.crawl.boot",
    ]
)
@EnableMongoRepositories("ai.platon.scent.boot.autoconfigure.persist")
@ImportResource("classpath:config/app/app-beans/app-context.xml")
class CrawlApplication(
    private val amazonGenerator: AmazonGenerator,
    private val amazonCrawler: AmazonCrawler,
    private val session: ScentSession,
    private val applicationContext: ApplicationContext,
    /**
     * Activate AppMetrics
     * */
    private val appMetrics: AppMetrics,
    /**
     * Activate WebDriverPoolMonitor
     * */
    private val driverPoolMonitor: WebDriverPoolMonitor,
    /**
     * Activate BrowserMonitor
     * */
    private val browserMonitor: BrowserMonitor,
    /**
     * Activate BrowserPrivacyContextMonitor
     * */
    private val privacyContextMonitor: BrowserPrivacyContextMonitor
) {
    private val logger = getLogger(CrawlApplication::class.java)
    private var submittedProductUrlCount = 0
    private val globalCache = session.globalCacheFactory.globalCache

    /**
     * Initialize and start amazon crawler
     * */
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun startAmazonCrawler(): StartStopRunner {
        return StartStopRunner(amazonCrawler)
    }

    /**
     * A very simple example to start crawling.
     * For real world crawl task generation, see [AmazonCrawler].
     * */
    @Bean
    fun injectExampleSeeds() {
        val args = BESTSELLER_LOAD_ARGUMENTS
        val itemArgs = ASIN_LOAD_ARGUMENTS

        val event = DefaultPageEvent()
        event.loadEvent.onHTMLDocumentParsed.addFirst { page, document ->
            val normalizer = AsinUrlNormalizer()
            val urls = document.document.selectHyperlinks(ASIN_LINK_SELECTOR_IN_BS_PAGE)
                .distinct()
                .map { l -> Hyperlink(normalizer(l.url)!!, args = itemArgs).apply { href = l.url } }

            val queue = globalCache.urlPool.normalCache.nonReentrantQueue
            urls.forEach { queue.add(it) }

            submittedProductUrlCount += urls.size
            logger.info("{}.\tSubmitted {}/{} asin links", page.id, urls.size, submittedProductUrlCount)
        }

        val ident = "com"
        val resource = "sites/amazon/crawl/generate/periodical/p7d/$ident/best-sellers.txt"
        val resource2 = "sites/amazon/crawl/generate/periodical/p7d/$ident/best-sellers.txt"
        val resource3 = PATH_FETCHED_BEST_SELLER_URLS
        val urls1 = LinkExtractors.fromResource(resource).distinct().filter { it.contains(".$ident") }
        val urls2 = LinkExtractors.fromResource(resource2).distinct().filter { it.contains(".$ident") }
        val urls3 = LinkExtractors.fromFile(resource3).distinct().filter { it.contains(".$ident") }
        val urls = (urls1.toMutableSet() + urls2 + urls3).map { "$it $args" }

//        LinkExtractors.fromResource(resource).map { "$it $args" }
        val queue = globalCache.urlPool.normalCache.nonReentrantQueue

        logger.info("Submitted {}({} & {}) bestseller urls at startup | {}, {}",
            urls.size, urls1.size, urls3.size,
            resource, resource2)
//        urls.map { ListenableHyperlink(it, eventHandler = eventHandler) }.forEach { queue.add(it) }
        urls.forEach { queue.add(PlainUrl(it)) }
    }
}

fun main(args: Array<String>) {
    // Backend storage is detected automatically but not on some OS such as Mac,
    // uncomment the following line to force MongoDB to be used as the backend storage
    System.setProperty(CapabilityTypes.STORAGE_DATA_STORE_CLASS, AppConstants.MONGO_STORE_CLASS)

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
        // product environment, the best speed is required
        additionalProfiles.add("prod")
    } else {
        // development environment
        BrowserSettings.privacy(2).maxTabs(8).headed()
    }

    runApplication<CrawlApplication>(*args) {
        setAdditionalProfiles(*additionalProfiles.toTypedArray())
        addInitializers(CrawlerInitializer())
        setRegisterShutdownHook(true)
        setLogStartupInfo(true)
    }
}
