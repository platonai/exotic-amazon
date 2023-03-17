package ai.platon.exotic.amazon.starter.statistics

import ai.platon.exotic.amazon.crawl.boot.CrawlerInitializer
import ai.platon.exotic.amazon.crawl.core.ASIN_LINK_SELECTOR_IN_BS_PAGE
import ai.platon.exotic.amazon.crawl.core.PATH_FETCHED_BEST_SELLER_URLS
import ai.platon.exotic.amazon.crawl.core.SECONDARY_BS_LINK_SELECTOR_IN_BS_PAGE
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.pulsar.common.AppContext
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.LinkExtractors
import ai.platon.pulsar.common.config.CapabilityTypes.APP_ID_KEY
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.scent.ScentSession
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ImportResource
import kotlin.io.path.listDirectoryEntries

@SpringBootApplication(
    scanBasePackages = [
        "ai.platon.scent.boot.autoconfigure",
    ]
)
@ImportResource("classpath:config/app/app-beans/app-context.xml")
class SolutionStatistics(
    val session: ScentSession
) {
    private final val ident = AppContext.APP_IDENT
    private val asinToJsonExportDir = AppPaths.DOC_EXPORT_DIR
        .resolve("amazon")
        .resolve("json")
        .resolve("asin-customer-hui")
    private final val providedPrimaryBestSellerUrls = LinkExtractors.fromResource(
        "sites/amazon/crawl/generate/periodical/p7d/$ident/best-sellers.txt").distinct()
    private final val exportedBestSellerUrls = LinkExtractors.fromFile(PATH_FETCHED_BEST_SELLER_URLS).distinct()
    private final val exportedPrimaryBestSellerUrls = exportedBestSellerUrls.filter { !it.contains("pg=2") }
    private final val exportedSecondaryBestSellerUrls = exportedBestSellerUrls.filter { it.contains("pg=2") }
    private final val extractedSecondaryBestSellerUrls = mutableSetOf<String>()
    private final val nonExportedPrimaryBestSellerUrls = providedPrimaryBestSellerUrls - exportedPrimaryBestSellerUrls

    var secondaryLinkCount = 0

    var asinLinkCount = 0
    val asinUrlNormalizer = AsinUrlNormalizer()
    val asinLinksExtractedFromAllBestSellerPages = mutableSetOf<Hyperlink>()
    var extractedASINPageCount = 0

    @Bean
    fun analyse() {
        // How many primary bestseller urls who do not exported to PATH_FETCHED_BEST_SELLER_URLS?

        providedPrimaryBestSellerUrls.forEach { bsUrl ->
            val bsPage = session.getOrNull(bsUrl)
            if (bsPage != null) {
                val bsDocument = session.parse(bsPage)

                val nextPageElement = bsDocument.selectFirstOrNull(SECONDARY_BS_LINK_SELECTOR_IN_BS_PAGE)
                if (nextPageElement != null) {
                    val nextPageLink = nextPageElement.attr("abs:href")
                    ++secondaryLinkCount
                    extractedSecondaryBestSellerUrls.add(nextPageLink)
                } else {
                    println("No secondary best seller page | $bsUrl")
                }
            } else {
                println("No best seller page | $bsUrl")
            }
        }

        val allBestSellerUrls = (providedPrimaryBestSellerUrls + exportedPrimaryBestSellerUrls +
                nonExportedPrimaryBestSellerUrls + extractedSecondaryBestSellerUrls + exportedSecondaryBestSellerUrls).distinct()

        allBestSellerUrls.forEach { bsUrl ->
            val bsPage = session.getOrNull(bsUrl)
            if (bsPage != null) {
                val bsDocument = session.parse(bsPage)

                val asinElements = bsDocument.select(ASIN_LINK_SELECTOR_IN_BS_PAGE)
                if (asinElements.isNotEmpty()) {
                    val asinLinks = asinElements.map { it.attr("abs:href") }
                        .filter { UrlUtils.isValidUrl(it) }
                    asinLinks.mapNotNullTo(asinLinksExtractedFromAllBestSellerPages) { href ->
                        asinUrlNormalizer(href)?.let { Hyperlink(it, href = href) } }
                    asinLinkCount = asinLinks.size
                } else {
                    println("No ASIN link in best seller page | $bsUrl")
                }
            }
        }

        extractedASINPageCount = asinToJsonExportDir.listDirectoryEntries("*.json").count()

        println("PATH_FETCHED_BEST_SELLER_URLS: file://$PATH_FETCHED_BEST_SELLER_URLS")
        println("providedPrimaryBestSellerUrls: " + providedPrimaryBestSellerUrls.size)
        println("exportedPrimaryBestSellerUrls: " + exportedPrimaryBestSellerUrls.size)
        println("nonExportedPrimaryBestSellerUrls: " + nonExportedPrimaryBestSellerUrls.size)
        println("extractedSecondaryBestSellerUrls: " + extractedSecondaryBestSellerUrls.size)
        println("exportedSecondaryBestSellerUrls: " + exportedSecondaryBestSellerUrls.size)
        println("asinLinksInAllBestSellerPages: " + asinLinksExtractedFromAllBestSellerPages.size)
        println("extractedASINPages: " + extractedASINPageCount)
    }

    private fun analyseAsinPages() {
        var successASINCount = 0
        var failedASINCount = 0
        asinLinksExtractedFromAllBestSellerPages.forEach { asinLink ->
            val asinUrl = asinLink.url
            val asinHref = asinLink.href
            val asinPage = session.getOrNull(asinUrl)
            if (asinPage != null) {
                if (asinPage.protocolStatus.isSuccess) {
                    ++successASINCount
                } else {
                    ++failedASINCount
                    println("ASIN page is failed | $asinUrl")
                }
            } else {
                println("ASIN page is not fetched | $asinUrl")
            }
        }
    }
}

fun main(args: Array<String>) {
    System.setProperty(APP_ID_KEY, "com")
    runApplication<SolutionStatistics>(*args) {
        addInitializers(CrawlerInitializer())
        setRegisterShutdownHook(true)
    }
}
