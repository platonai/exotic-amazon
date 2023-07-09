package ai.platon.exotic.amazon.tools.common

import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.common.urls.preprocess.AbstractUrlNormalizer
import org.apache.commons.lang3.StringUtils
import java.net.URLEncoder
import java.nio.charset.Charset

object AmazonUrls {
    val charset = Charset.defaultCharset()

    val indexPageUrlContains = arrayOf(
        "/zgbs/", "/bestsellers/",
        "/most-wished-for/", "/new-releases/",
        "/movers-and-shakers/"
    )

    fun isAmazon(url: String): Boolean {
        return url.contains(".amazon.")
    }

    fun isIndexPage(url: String): Boolean {
        return isAmazon(url) && (indexPageUrlContains.any { url.contains(it) })
    }

    fun isItemPage(url: String): Boolean {
        return isAmazon(url) && url.contains("/dp/")
    }

    fun isReviewPage(url: String): Boolean {
        return isAmazon(url) && url.contains("/product-reviews/")
    }

    fun isSearch(url: String): Boolean {
        return isAmazon(url) && url.contains("s?k=")
    }

    fun findAsin(url: String): String? {
        val pos = url.indexOf("/dp/") + "/dp/".length
        if (pos > AppConstants.SHORTEST_VALID_URL_LENGTH) {
            var pos2 = pos
            while (pos2 < url.length && url[pos2].isLetterOrDigit()) {
                ++pos2
            }

            if (pos2 <= url.length) {
                return url.substring(pos, pos2)
            }
        }

        return null
    }

    fun normalizeAsinUrl(url: String) = AsinUrlNormalizer().invoke(url)
}

class AsinUrlNormalizer: AbstractUrlNormalizer() {
    override operator fun invoke(url: String?): String? {
        if (url == null) return null

        if (!AmazonUrls.isAmazon(url)) {
            return url
        }

        val u = UrlUtils.getURLOrNull(url) ?: return null
        val asin = AmazonUrls.findAsin(url) ?: return null
        return u.protocol + "://" + u.host + "/dp/" + asin
    }
}
