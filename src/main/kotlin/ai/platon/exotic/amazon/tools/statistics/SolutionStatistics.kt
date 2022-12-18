package ai.platon.exotic.amazon.tools.statistics

import ai.platon.exotic.amazon.crawl.core.ASIN_LINK_SELECTOR_IN_BS_PAGE
import ai.platon.exotic.amazon.crawl.core.PATH_FETCHED_BEST_SELLER_URLS
import ai.platon.exotic.amazon.crawl.core.SECONDARY_BS_LINK_SELECTOR_IN_BS_PAGE
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.pulsar.common.LinkExtractors
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.ql.context.SQLContexts

private class SolutionStatistics {
    val context = SQLContexts.create()
    val session = context.createSession()

    val predefinedPrimaryBestSellerUrls = LinkExtractors.fromResource("sites/amazon/crawl/generate/periodical/p7d/best-sellers.txt")
    val exportedBestSellerUrls = LinkExtractors.fromFile(PATH_FETCHED_BEST_SELLER_URLS)
    val exportedPrimaryBestSellerUrls = exportedBestSellerUrls.filter { !it.contains("pg=2") }
    val exportedSecondaryBestSellerUrls = exportedBestSellerUrls.filter { it.contains("pg=2") }
    val extractedSecondaryBestSellerUrls = mutableSetOf<String>()
    val nonExportedPrimaryBestSellerUrls = exportedBestSellerUrls - exportedPrimaryBestSellerUrls

    var secondaryLinkCount = 0

    var asinLinkCount = 0
    val asinUrlNormalizer = AsinUrlNormalizer()
    val asinLinksInAllBestSellerPages = mutableSetOf<Hyperlink>()

    fun analyse() {
        // How many primary bestseller urls who do not exported to PATH_FETCHED_BEST_SELLER_URLS?
        println("PATH_FETCHED_BEST_SELLER_URLS: file://$PATH_FETCHED_BEST_SELLER_URLS")
        println("nonExportedPrimaryBestSellerUrls: " + nonExportedPrimaryBestSellerUrls.size)

        predefinedPrimaryBestSellerUrls.forEach {  bsUrl ->
            val bsPage = session.getOrNull(bsUrl)
            if (bsPage != null) {
                val bsDocument = session.parse(bsPage)

                val asinElements = bsDocument.select(ASIN_LINK_SELECTOR_IN_BS_PAGE)
                if (asinElements.isNotEmpty()) {
                    val asinLinks = asinElements.map { it.attr("abs:href") }
                        .filter { UrlUtils.isValidUrl(it) }
                    asinLinks.mapNotNullTo(asinLinksInAllBestSellerPages) { href ->
                        asinUrlNormalizer(href)?.let { Hyperlink(it, href = href) } }
                    asinLinkCount = asinLinks.size
                } else {
                    println("No ASIN link in best seller page | $bsUrl")
                }

                val nextPageElement = bsDocument.selectFirstOrNull(SECONDARY_BS_LINK_SELECTOR_IN_BS_PAGE)
                if (nextPageElement != null) {
                    val nextPageLink = nextPageElement.attr("abs:href")
                    extractedSecondaryBestSellerUrls.add(nextPageLink)
                } else {
                    println("No secondary best seller page | $bsUrl")
                }
            }
        }

        println("predefinedPrimaryBestSellerUrls: " + predefinedPrimaryBestSellerUrls.size)
        println("exportedPrimaryBestSellerUrls: " + exportedPrimaryBestSellerUrls.size)
        println("exportedSecondaryBestSellerUrls: " + exportedSecondaryBestSellerUrls.size)
        println("extractedSecondaryBestSellerUrls: " + extractedSecondaryBestSellerUrls.size)
        println("asinLinksInAllBestSellerPages: " + asinLinksInAllBestSellerPages.size)
    }
}

fun main() {
    val stat = SolutionStatistics()
    stat.analyse()
}
