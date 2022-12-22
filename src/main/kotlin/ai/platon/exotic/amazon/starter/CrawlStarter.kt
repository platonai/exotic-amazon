package ai.platon.exotic.amazon.starter

import ai.platon.exotic.amazon.crawl.boot.CrawlerInitializer
import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.*
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.exotic.common.ClusterTools
import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.config.CapabilityTypes.APP_ID_STR
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.PlainUrl
import ai.platon.pulsar.crawl.DefaultPulsarEventHandler
import ai.platon.pulsar.dom.select.selectHyperlinks
import ai.platon.pulsar.protocol.browser.driver.WebDriverPoolMonitor
import ai.platon.scent.ScentSession
import ai.platon.scent.protocol.browser.emulator.context.BrowserPrivacyContextMonitor
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
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
    private val session: ScentSession,
    private val applicationContext: ApplicationContext
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
     * Initialize and start amazon crawler
     * */
    @Bean
    fun injectBestsellerSeeds() {
        // Top level domain
        val ident = AppContext.APP_IDENT
        val args = BESTSELLER_LOAD_ARGUMENTS
        val itemArgs = ASIN_LOAD_ARGUMENTS

        val eventHandler = DefaultPulsarEventHandler()
        eventHandler.loadEventHandler.onHTMLDocumentParsed.addFirst { page, document ->
            val normalizer = AsinUrlNormalizer()
            val urls = document.document.selectHyperlinks(ASIN_LINK_SELECTOR_IN_BS_PAGE)
                .distinct()
                .map { l -> Hyperlink(normalizer(l.url)!!, args = itemArgs).apply { href = l.url } }

            val queue = globalCache.urlPool.normalCache.nonReentrantQueue
            urls.forEach { queue.add(it) }

            submittedProductUrlCount += urls.size
            logger.info("{}.\tSubmitted {}/{} asin links", page.id, urls.size, submittedProductUrlCount)
        }

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
    System.setProperty(APP_ID_STR, "com")
//    BrowserSettings.privacy(2).maxTabs(4)

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
