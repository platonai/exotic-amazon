package ai.platon.exotic.amazon.tools.scrapers

import ai.platon.pulsar.common.brief
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.common.sql.SQLTemplate
import ai.platon.pulsar.common.stringify
import ai.platon.pulsar.crawl.common.URLUtil
import ai.platon.pulsar.crawl.common.url.ListenableHyperlink
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.ql.context.SQLContext
import ai.platon.pulsar.ql.context.SQLContexts
import ai.platon.scent.ScentEnvironment
import org.apache.commons.lang3.StringUtils
import java.sql.ResultSet

open class AdvancedAsinScraper(
    val asinLoadArgs: String,
    val context: SQLContext = SQLContexts.create(),
) : AutoCloseable {
    private val logger = getLogger(AdvancedAsinScraper::class)

    private val session = context.createSession()

    private val asinSQLResource = "sites/amazon/crawl/parse/sql/crawl/x-asin.sql"
    private val asinSQLTemplate = SQLTemplate.load(asinSQLResource)

    private val districtSelector = "#glow-ingress-block, .nav-global-location-slot"

    /**
     * Given ASIN url list, scrape all the ASIN pages and relative seller pages.
     * After clicking `New seller` button, the seller url will be displayed.
     * */
    @Throws(Exception::class)
    fun scrape(url: String) {
        val link = createASINHyperlink(url)
        session.load(link)
    }

    fun createASINHyperlink(url: String): ListenableHyperlink {
        val domain = URLUtil.getDomainName(url) ?: throw IllegalArgumentException("Illegal domain name | $url")
        return createASINHyperlink(domain, url)
    }

    fun createASINHyperlink(domain: String, asinUrl: String): ListenableHyperlink {
        val hyperlink = ListenableHyperlink(asinUrl, args = "$asinLoadArgs -parse")
        val be = hyperlink.event.browseEvent

        be.onBrowserLaunched.addLast { page, driver ->
            // visit the home page to avoid being blocked
            val warmUpUrl = "https://www.$domain/"
            logger.info("Browser launched, warm up with url | {}", warmUpUrl)
            driver.navigateTo(warmUpUrl)
        }

        be.onWillNavigate.addLast { page, driver ->
            // block images to accelerate the page loading
            driver.addBlockedURLs(listOf("*.jpg", "*.png", "*.gif"))
            null
        }

        be.onDocumentActuallyReady.addLast { page, driver ->
            val district = StringUtils.normalizeSpace(driver.firstText(districtSelector))

            if (district.isNullOrBlank()) {
                // page not actually ready
                logger.warn("District is null or blank <$district>.")
                // you can cancel the page here since it's not the district we needed.
                return@addLast null
            }

            null
        }

        be.onDidScroll.addLast { page, driver ->
            // key scroll point: review mentions
            val mentionSelector =
                "#cr-dp-lighthut, div[data-hook=lighthut-widget], h3:contains(Read reviews that mention)"
            driver.scrollTo(mentionSelector)
        }

        val le = hyperlink.event.loadEvent
        le.onHTMLDocumentParsed.addLast { page, document ->
            // when the document is ready, we can extract fields from it
            scrapeAsin(page, document)
        }

        return hyperlink
    }

    private fun scrapeAsin(page: WebPage, document: FeaturedDocument) {
        if (!context.isActive) {
            return
        }

        kotlin.runCatching { scrapeAsin0(page, document) }.onFailure { logger.warn(it.brief()) }
    }

    private fun scrapeAsin0(page: WebPage, document: FeaturedDocument) {
        val asinUrl = page.url
        val sql = asinSQLTemplate.createSQL(asinUrl)
        context.executeQuery(sql).use { rs ->
            val formatter = ResultSetFormatter(rs, asList = true, textOnly = true, maxColumnLength = 200)
            logger.info("\n{}", formatter)

            kotlin.runCatching { verifyFieldIntegrity(page, rs) }.onFailure { logger.warn(it.stringify()) }

            val queue = session.globalCache.urlPool.higher2Cache.nonReentrantQueue
            // collect more links and crawl them
            // kotlin.runCatching { submitSellerLinks(page, document, rs, queue) }.onFailure { logger.warn(it.stringify()) }
        }
    }

    fun verifyFieldIntegrity(page: WebPage, rs: ResultSet) {
        verifyImageFields(rs)
    }

    private fun verifyImageFields(rs: ResultSet) {
    }

    override fun close() {
    }
}

fun main() {
    val url = "https://www.amazon.co.uk/dp/B06Y1JCB9T -i 1s"

    ScentEnvironment().checkEnvironment()

    val scraper = AdvancedAsinScraper("-i 100d -requireSize 800000")
    scraper.scrape(url)
}
