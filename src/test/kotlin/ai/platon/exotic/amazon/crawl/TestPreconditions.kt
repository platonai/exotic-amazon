package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.boot.component.AmazonJdbcSinkSQLExtractor
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.collect.LoadingUrlPool
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.crawl.common.url.StatefulListenableHyperlink
import ai.platon.pulsar.crawl.component.BatchFetchComponent
import ai.platon.pulsar.crawl.component.FetchComponent
import ai.platon.pulsar.crawl.component.LoadComponent
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.pulsar.persist.HadoopUtils
import ai.platon.scent.crawl.serialize.config.v1.CrawlConfig
import ai.platon.scent.jackson.scentObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Ignore
import org.junit.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestPreconditions: TestBase() {

    private val logger = LoggerFactory.getLogger(TestPreconditions::class.java)

    @Autowired
    lateinit var fetchComponent: FetchComponent

    @Autowired
    lateinit var loadComponent: LoadComponent

    @Autowired
    private lateinit var parseFilters: ParseFilters

    private val expectedProperties = mapOf(
        CapabilityTypes.LEGACY_CONFIG_PROFILE to "default",
        CapabilityTypes.PROXY_ENABLE_DEFAULT_PROVIDERS to "true",
        CapabilityTypes.PROXY_POOL_MONITOR_CLASS to "ai.platon.pulsar.common.proxy.ProxyPoolManager",
//        CapabilityTypes.PROXY_LOADER_CLASS to "ai.platon.exotic.common.proxy.ProxyVendorLoader",
        CapabilityTypes.PRIVACY_AGENT_GENERATOR_CLASS to "ai.platon.pulsar.crawl.fetch.privacy.SequentialPrivacyContextIdGenerator",
        CapabilityTypes.H2_SESSION_FACTORY_CLASS to "ai.platon.scent.ql.h2.H2SessionFactory"
    )

    @Test
    fun testCrawlConfig() {
        val extractConfigResource = "sites/amazon/crawl/parse/extract-config.json"
        val crawlJsonConfig = ResourceLoader.readString(extractConfigResource)
        val crawlConfig = scentObjectMapper().readValue<CrawlConfig>(crawlJsonConfig)
//        val parser = CrawlConfigParser(crawlConfig, null, null, null)
    }

    @Test
    fun `Ensure logger names are correct`() {
    }

    @Test
    fun `Ensure system properties are correct`() {
        expectedProperties.forEach { (name, value) ->
            val actual = System.getProperty(name)
            // println("$name: $value | $actual")
        }

        expectedProperties.forEach { (name, value) ->
            assertEquals(value, System.getProperty(name), "property name: <$name>")
        }
    }

    @Test
    fun `Ensure config loads correct resources`() {
        assertNotNull(applicationContext.getBean(ImmutableConfig::class.java))
        val conf = session.unmodifiedConfig
        assertTrue("pulsar-task.xml" in conf.toString())
        println(conf.toString())
        val hadoopConf = HadoopUtils.toHadoopConfiguration(conf)
        println(hadoopConf.toString())
        assertTrue { "config/legacy/default/pulsar-task.xml" in hadoopConf.toString() }
        assertEquals("amazon_tmp", hadoopConf["storage.crawl.id"])
    }

    @Ignore("Active profile amazon-prod and then run this test")
    @Test
    fun `Ensure required properties`() {
        val name = "graphite.server"
        val expected = "42.194.241.96"

        assertNotNull(applicationContext.getBean(ImmutableConfig::class.java))
        assertNotNull(session.unmodifiedConfig.environment)
        assertEquals(expected, session.context.unmodifiedConfig[name])
        assertEquals(expected, session.unmodifiedConfig[name])
        assertEquals(expected, session.sessionConfig[name])
        assertEquals(expected, session.sessionConfig.toVolatileConfig()[name])
    }

    @Test
    fun `When AmazonCrawler started then FetchComponent is correct`() {
        assertTrue(fetchComponent is BatchFetchComponent)
        assertNotNull(fetchComponent.coreMetrics)
        assertNotNull(loadComponent.globalCache.urlPool is LoadingUrlPool)
    }

    @Test
    fun `When a page is fetched then it's recorded in CoreMetrics`() {
        val metrics = fetchComponent.coreMetrics
        assertNotNull(metrics)

        val url = "https://www.amazon.com/Best-Sellers-Beauty/zgbs/beauty"
        val hyperlink = StatefulListenableHyperlink(url, args = "-parse -i 0s")
        hyperlink.event.loadEvent.onLoaded.addLast { page ->
            assertTrue { metrics.fetchTasks.count > 0 }
        }

        session.load(hyperlink)
    }

    @Ignore("District restriction is disabled")
    @Test
    fun `Ensure the district is New York`() {
        WebDataExtractorInstaller(extractorFactory).install(parseFilters)

        val asinExtractor = parseFilters.parseFilters
                .filterIsInstance<AmazonJdbcSinkSQLExtractor>()
                .firstOrNull { it.name == "asin" }
        assertNotNull(asinExtractor)

        val page = session.load(productUrl, "-parse -i 0s")

        val district = AmazonJdbcSinkSQLExtractor.lastDistrict
        assertTrue { page.protocolStatus.isSuccess }
        assertTrue { "icp-nav-flag-us" in AmazonJdbcSinkSQLExtractor.lastLang }
        assertTrue("District: <$district>") { district.isNotBlank() }
        // Run ChooseCountry to choose the correct country
        // assertTrue("District: <$district>") { "New York" in district }
        assertTrue { page.htmlIntegrity.isOK }
    }
}
