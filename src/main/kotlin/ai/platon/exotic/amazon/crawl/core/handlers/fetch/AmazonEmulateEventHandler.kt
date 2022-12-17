package ai.platon.exotic.amazon.crawl.core.handlers.fetch

import ai.platon.exotic.amazon.tools.common.AmazonUrls
import ai.platon.pulsar.common.HtmlIntegrity
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.persist.PageDatum
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.metadata.OpenPageCategory
import ai.platon.pulsar.persist.metadata.PageCategory
import ai.platon.pulsar.protocol.browser.emulator.HtmlIntegrityChecker
import ai.platon.pulsar.protocol.browser.emulator.PageCategorySniffer

class AmazonDetailPageHtmlChecker(
    private val conf: ImmutableConfig
): HtmlIntegrityChecker {
    companion object {
        const val SMALL_CONTENT_LIMIT = 500_000
    }

    private val logger = getLogger(AmazonDetailPageHtmlChecker::class)

    private val enableDistrictCheck = conf.getBoolean("amazon.enable.district.check", false)

    private val allowedDistrict = conf.get("amazon.allowed.district", "New York")

    override fun isRelevant(url: String): Boolean {
        return AmazonUrls.isAmazon(url)
    }

    // Since we need to check the html integrity of the page, we need active dom urls,
    // which is calculated in javascript.
    override fun invoke(pageSource: String, pageDatum: PageDatum): HtmlIntegrity {
        val length = pageSource.length.toLong()
        var integrity = HtmlIntegrity.OK

        if (integrity.isOK && length < SMALL_CONTENT_LIMIT) {
            integrity = when {
                // example: https://www.amazon.com/dp/B0BBBBB
                // the page size is 2k
                isNotFound(pageSource, pageDatum) -> HtmlIntegrity.NOT_FOUND
                // robot check
                isRobotCheck(pageSource, pageDatum) -> HtmlIntegrity.ROBOT_CHECK
                // too small
                isTooSmall(pageSource, pageDatum) -> HtmlIntegrity.TOO_SMALL
                else -> integrity
            }
        }

        return integrity
    }

    /**
     * Check if the page content is too small
     * */
    private fun isTooSmall(pageSource: String, pageDatum: PageDatum): Boolean {
        val length = pageSource.length
        return if (AmazonUrls.isItemPage(pageDatum.url)) {
            length < SMALL_CONTENT_LIMIT / 2
        } else {
            length < 1000
        }
    }

    /**
     * Check if the district setting of the webpage is as expected.
     * Amazon shows different content for users from different district, for example, product stock status.
     * */
    private fun isWrongDistrict(pageSource: String, page: WebPage): Boolean {
        if (!enableDistrictCheck) {
            return false
        }

        if (!AmazonUrls.isItemPage(page.url) && !AmazonUrls.isIndexPage(page.url)) {
            return false
        }

        var pos = pageSource.indexOf("glow-ingress-block")
        if (pos != -1) {
            pos = pageSource.indexOf("Deliver to", pos)
            if (pos != -1) {
                pos = pageSource.indexOf(allowedDistrict, pos)
                if (pos == -1) {
                    // when the destination to deliver is not expected, the district is wrong
                    return true
                }
            }
        }

        return false
    }

    /**
     * Check if this page is redirected to a robot-check page
     * */
    private fun isRobotCheck(pageSource: String, pageDatum: PageDatum): Boolean {
        return pageSource.length < 150_000 && pageSource.contains("Type the characters you see in this image")
    }

    /**
     * Check if this page is redirected to a not-found page
     * */
    private fun isNotFound(pageSource: String, pageDatum: PageDatum): Boolean {
        return pageSource.length < 150_000 && pageSource.contains("Sorry! We couldn't find that page")
    }
}

open class AmazonPageCategorySniffer(
    val conf: ImmutableConfig
): PageCategorySniffer {

    val categories = mapOf(
        "/zgbs/" to OpenPageCategory(PageCategory.INDEX),
        "/most-wished-for/" to OpenPageCategory("INDEX", "IMWF"),
        "/new-releases/" to OpenPageCategory("INDEX", "INR"),
        "/movers-and-shakers/" to OpenPageCategory("INDEX", "IMAS")
    )

    fun isRelevant(url: String) = AmazonUrls.isAmazon(url)

    fun isRelevant(pageDatum: PageDatum) = isRelevant(pageDatum.url)

    override fun invoke(pageDatum: PageDatum): OpenPageCategory {
        if (!isRelevant(pageDatum)) {
            return OpenPageCategory(PageCategory.UNKNOWN)
        }

        val url = pageDatum.url
        val category = categories.entries.firstOrNull { it.key in url }
        if (category != null) {
            return category.value
        }

        return when {
            AmazonUrls.isIndexPage(url) -> OpenPageCategory(PageCategory.INDEX)
            AmazonUrls.isItemPage(url) -> OpenPageCategory(PageCategory.DETAIL)
            AmazonUrls.isReviewPage(url) -> OpenPageCategory(PageCategory.REVIEW)
            AmazonUrls.isSearch(url) -> OpenPageCategory(PageCategory.SEARCH)
            else -> OpenPageCategory(PageCategory.UNKNOWN)
        }
    }
}
