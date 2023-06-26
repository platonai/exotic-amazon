package ai.platon.exotic.amazon.starter

import ai.platon.exotic.amazon.crawl.boot.CrawlerInitializer
import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.*
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.exotic.common.ClusterTools
import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.crawl.common.url.ListenableHyperlink
import ai.platon.pulsar.crawl.event.impl.DefaultPageEvent
import ai.platon.pulsar.crawl.fetch.privacy.PrivacyContextMonitor
import ai.platon.pulsar.dom.select.selectHyperlinks
import ai.platon.pulsar.protocol.browser.driver.BrowserMonitor
import ai.platon.pulsar.protocol.browser.driver.WebDriverPoolMonitor
import ai.platon.scent.ScentSession
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
    private val privacyContextMonitor: PrivacyContextMonitor,
) {
    private val logger = getLogger(CrawlApplication::class.java)
    private var submittedProductUrlCount = 0
    private val globalCache = session.globalCacheFactory.globalCache
    private val isDev get() = ClusterTools.isDevInstance()

    @Bean
    fun overrideGoraConfig() {
        val conf = session.context.unmodifiedConfig

        if (NetUtil.testNetwork("127.0.0.1", 28018)) {
            // TODO: find out why the settings in application.properties do not work
            conf.unbox().set("gora.mongodb.override_hadoop_configuration", "false")
            conf.unbox().set("gora.mongodb.servers", "127.0.0.1:28018")
        }
    }

    /**
     * Initialize and start amazon crawler.
     * */
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun startAmazonCrawler(): StartStopRunner {
        return StartStopRunner(amazonCrawler)
    }

    /**
     * A very simple example to start crawling.
     *
     * For real world crawl task generation, see [AmazonCrawler]
     * and [ai.platon.exotic.amazon.crawl.boot.CrawlScheduler].
     * */
    @Bean
    fun injectExampleSeeds() {
        if (!isDev) {
            return
        }

        val bsLoadArgs = BESTSELLER_LOAD_ARGUMENTS
        val asinLoadArgs = ASIN_LOAD_ARGUMENTS

        val event = DefaultPageEvent()
        event.loadEvent.onHTMLDocumentParsed.addFirst { page, document ->
            val normalizer = AsinUrlNormalizer()
            val asinLinks = document.document.selectHyperlinks(ASIN_LINK_SELECTOR_IN_BS_PAGE)
                .distinct()
                .map { l -> Hyperlink(normalizer(l.url)!!, args = asinLoadArgs).apply { href = l.url } }

            val queue = globalCache.urlPool.higher4Cache.nonReentrantQueue
            asinLinks.forEach { queue.add(it) }

            submittedProductUrlCount += asinLinks.size
            logger.info("{}.\t[DEV DEMO] Submitted {}/{} asin links | {}",
                page.id, asinLinks.size, submittedProductUrlCount,
                page.url
            )
        }

        val tld = "com"
        val resource = "sites/amazon/crawl/generate/demo/$tld/best-sellers.txt"
        val resource2 = "sites/amazon/crawl/generate/periodical/pt24h/best-sellers.txt"
        val resource3 = PATH_FETCHED_BEST_SELLER_URLS
        val urls1 = LinkExtractors.fromResource(resource).filter { it.contains(".$tld/") }.distinct()
        val urls2 = LinkExtractors.fromResource(resource2).filter { it.contains(".$tld/") }.distinct()
        val urls3 = LinkExtractors.fromFile(resource3).filter { it.contains(".$tld/") }.distinct()

        val urls = (urls1 + urls2 + urls3).map { "$it $bsLoadArgs" }

        val queue = globalCache.urlPool.higherCache.nonReentrantQueue

        logger.info("[DEV DEMO] Submitted {}({} & {}) bestseller urls at startup | {}, {}",
            urls.size, urls1.size, urls3.size, resource, resource2)
        urls.map { ListenableHyperlink(it, event = event) }.forEach { queue.add(it) }
    }
}

fun main(args: Array<String>) {
    var headless = false
    var privacyCount = 2
    var maxTabs = 8

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--headless" -> headless = true
            "-pc",
            "--privacy" -> privacyCount = args[++i].toIntOrNull() ?: privacyCount
            "-mt",
            "--maxTabs" -> maxTabs = args[++i].toIntOrNull() ?: maxTabs
            else -> println("CrawlStater: [options]")
        }
        ++i
    }

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
    }

    // Ways to use headless chrome:
    // 1. set browser.display.mode=HEADLESS in spring config files
    // 2. System.setProperty("browser.display.mode", "HEADLESS") or other spring compatible config approaches
    // 3. BrowserSettings.headless()
    if (headless) {
        BrowserSettings.headless()
    }

    // Also we can use BrowserSettings for more control
    BrowserSettings.privacy(privacyCount).maxTabs(maxTabs)

    runApplication<CrawlApplication>(*args) {
        setAdditionalProfiles(*additionalProfiles.toTypedArray())
        addInitializers(CrawlerInitializer())
        setRegisterShutdownHook(true)
        setLogStartupInfo(true)
    }
}
