package ai.platon.exotic.amazon.tools.common

data class PageTraits(
        var isLabeledPortal: Boolean = false,

        var isIndex: Boolean = false,
        var isPrimaryIndex: Boolean = false,
        var isSecondaryIndex: Boolean = false,

        var isItem: Boolean = false,

        var isReview: Boolean = false,
        var isPrimaryReview: Boolean = false,
        var isSecondaryReview: Boolean = false,

        var isSearchResult: Boolean = false,
        var isPrimeSearchResult: Boolean = false,
        var isSecondarySearchResult: Boolean = false
)

object AmazonPageTraitsDetector {

    /**
     * The labeled portals:
     * https://www.amazon.com/Best-Sellers/zgbs
     * https://www.amazon.com/gp/new-releases
     * https://www.amazon.com/gp/movers-and-shakers
     * https://www.amazon.com/gp/most-wished-for
     * */
    val portalLabels = arrayOf(
        "zgbs", "bestsellers", "most-wished-for", "new-releases", "movers-and-shakers",
    )

    fun getLabelOfPortalOrNull(url: String): String? {
        return portalLabels.firstOrNull { url.contains("/$it/", ignoreCase = true) }
    }

    fun getLabelOfPortal(url: String): String {
        return portalLabels.first { url.contains("/$it/", ignoreCase = true) }
    }

    fun isLabeledPortalPage(url: String): Boolean {
        return portalLabels.any { url.contains("/$it/", ignoreCase = true) }
    }

    /**
     * NOTICE: a primary index page might not be the first secondary page whose page number is 1
     * */
    fun isPrimaryLabeledPortalPage(url: String): Boolean {
        return isLabeledPortalPage(url) && !url.contains("&pg=")
    }

    fun isSecondaryLabeledPortalPage(url: String): Boolean {
        return isLabeledPortalPage(url) && url.contains("&pg=")
    }

    fun isProductPage(url: String): Boolean {
        return url.contains("/dp/")
    }

    fun isReviewPage(url: String): Boolean {
        return url.contains("/product-reviews/")
    }

    fun isPrimaryReviewPage(url: String): Boolean {
        return isReviewPage(url) && !url.contains("sortBy")
    }

    fun isSecondaryReviewPage(url: String): Boolean {
        return isReviewPage(url) && url.contains("sortBy=recent")
                && url.contains("pageNumber=")
    }

    fun isPrimeSearchResultPage(url: String): Boolean {
        return url.contains("&k=") && url.contains("&i=") && !url.contains("&page=")
    }

    fun isSearchResultPage(url: String): Boolean {
        return url.contains("&k=") && url.contains("&i=")
    }
}
