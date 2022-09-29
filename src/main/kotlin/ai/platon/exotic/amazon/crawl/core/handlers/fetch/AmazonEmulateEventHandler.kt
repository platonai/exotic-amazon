package ai.platon.exotic.amazon.crawl.core.handlers.fetch

import ai.platon.exotic.amazon.tools.common.AmazonUrls
import ai.platon.pulsar.common.HtmlIntegrity
import ai.platon.pulsar.common.HtmlUtils.isBlankBody
import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.message.MiscMessageWriter
import ai.platon.pulsar.persist.ProtocolStatus
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.metadata.OpenPageCategory
import ai.platon.pulsar.persist.metadata.PageCategory
import ai.platon.pulsar.protocol.browser.driver.WebDriverPoolManager
import ai.platon.pulsar.protocol.browser.emulator.EmulateEventHandler
import ai.platon.pulsar.protocol.browser.emulator.NavigateTask
import kotlin.math.roundToLong

/**
 * The emulate event handler.
 *
 * Created by vincent on 22-08-03.
 */
class AmazonEmulateEventHandler(
    driverPoolManager: WebDriverPoolManager,
    messageWriter: MiscMessageWriter,
    immutableConfig: ImmutableConfig
) : EmulateEventHandler(driverPoolManager, messageWriter, immutableConfig) {
    companion object {
        private const val SMALL_CONTENT_LIMIT = 1_000_000 / 2 // 500KiB
    }

    private val enableDistrictCheck = immutableConfig.getBoolean("amazon.enable.district.check", false)

    private val allowedDistrict = immutableConfig.get("amazon.allowed.district", "New York")

    fun isRelevant(url: String) = AmazonUrls.isAmazon(url)

    fun isRelevant(page: WebPage) = isRelevant(page.url)

    val categories = mapOf(
        "/zgbs/" to OpenPageCategory(PageCategory.INDEX),
        "/most-wished-for/" to OpenPageCategory("INDEX", "IMWF"),
        "/new-releases/" to OpenPageCategory("INDEX", "INR"),
        "/movers-and-shakers/" to OpenPageCategory("INDEX", "IMAS")
    )

    /**
     * Sniff page category. Page category is use only for logging purpose, currently.
     * */
    override fun sniffPageCategory(page: WebPage): OpenPageCategory {
        if (!isRelevant(page)) {
            return OpenPageCategory(PageCategory.UNKNOWN)
        }

        val url = page.url
        val category = categories.entries.firstOrNull { it.key in url }
        if (category != null) {
            return category.value
        }

        return when {
            AmazonUrls.isIndexPage(page.url) -> OpenPageCategory(PageCategory.INDEX)
            AmazonUrls.isItemPage(page.url) -> OpenPageCategory(PageCategory.DETAIL)
            AmazonUrls.isReviewPage(page.url) -> OpenPageCategory(PageCategory.REVIEW)
            AmazonUrls.isSearch(page.url) -> OpenPageCategory(PageCategory.SEARCH)
            else -> super.sniffPageCategory(page)
        }
    }

    /**
     * Check if the html is good for further process.
     * */
    override fun checkHtmlIntegrity(
        pageSource: String,
        page: WebPage,
        status: ProtocolStatus,
        task: NavigateTask
    ): HtmlIntegrity {
        if (!isRelevant(page.url)) {
            return HtmlIntegrity.OK
        }

        if (!AmazonUrls.isAmazon(page.url)) {
            return super.checkHtmlIntegrity(pageSource, page, status, task)
        }

        val length = pageSource.length.toLong()
        val aveLength = page.aveContentBytes
        var integrity = HtmlIntegrity.OK

        if (integrity.isOK && length < SMALL_CONTENT_LIMIT) {
            integrity = when {
                length == 0L -> HtmlIntegrity.EMPTY_0B
                length == 39L -> HtmlIntegrity.EMPTY_39B
                // There is nothing in <body> tag
                // Blank body can be caused by anti-spider
                isBlankBody(pageSource) -> HtmlIntegrity.BLANK_BODY
                // example: https://www.amazon.com/dp/B0BBBBB
                // the page size is 2k
                isNotFound(pageSource, page) -> HtmlIntegrity.NOT_FOUND
                // robot check
                isRobotCheck(pageSource, page) -> HtmlIntegrity.ROBOT_CHECK
                // too small
                isTooSmall(pageSource, page) -> HtmlIntegrity.TOO_SMALL
                else -> integrity
            }
        }

        if (integrity.isOK && isWrongDistrict(pageSource, page)) {
            integrity = HtmlIntegrity.WRONG_DISTRICT
        }

        if (integrity.isOK && page.fetchCount > 0 && aveLength > SMALL_CONTENT_LIMIT && length < 0.1 * aveLength) {
            integrity = HtmlIntegrity.TOO_SMALL_IN_HISTORY
        }

        val stat = task.task.batchStat
        if (integrity.isOK && status.isSuccess && stat != null) {
            val batchAveLength = stat.bytesPerPage.roundToLong()
            if (stat.numTasksSuccess > 3 && batchAveLength > 10_000 && length < batchAveLength / 10) {
                integrity = HtmlIntegrity.TOO_SMALL_IN_BATCH

                if (logger.isInfoEnabled) {
                    val readableLength = Strings.readableBytes(length)
                    val readableAveLength = Strings.readableBytes(aveLength)
                    val readableBatchAveLength = Strings.readableBytes(batchAveLength)
                    val fetchCount = page.fetchCount
                    val message = "retrieved: $readableLength, batch: $readableBatchAveLength" +
                            " history: $readableAveLength/$fetchCount ($integrity)"
                    logger.info(message)
                }
            }
        }

        if (integrity.isOK) {
            integrity = super.checkHtmlIntegrity(pageSource)
        }

        return integrity
    }

    /**
     * Check if the page content is too small
     * */
    private fun isTooSmall(pageSource: String, page: WebPage): Boolean {
        val length = pageSource.length
        return if (AmazonUrls.isItemPage(page.url)) {
            length < SMALL_CONTENT_LIMIT / 2
        } else {
            length < 1000
        }
    }

    /**
     * Check if the district of the webpage is not expected. Amazon shows different content for users from different
     * district, for example, product stock status.
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

    private fun isRobotCheck(pageSource: String, page: WebPage): Boolean {
        return pageSource.length < 150_000 && pageSource.contains("Type the characters you see in this image")
    }

    private fun isNotFound(pageSource: String, page: WebPage): Boolean {
        return pageSource.length < 150_000 && pageSource.contains("Sorry! We couldn't find that page")
    }
}
