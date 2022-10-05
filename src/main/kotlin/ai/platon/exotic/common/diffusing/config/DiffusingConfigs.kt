package ai.platon.exotic.common.diffusing.config

data class NavigationProcessorConfig(
        var navigationCss: String = "ul.a-pagination",
        var lastLastPageCss: String = "li.a-normal ~ li.a-disabled",
        var nextPageCss: String = "li.a-last a",
        var minPageSize: Int = 200_000,
        var storeContent: Boolean = false,
        var dbCheck: Boolean = true
)

data class IndexProcessorConfig(
        var args: String = "-i 3d -parse -ignoreFailure",
        var urlPattern: Regex = ".+&i=.+".toRegex(),
        var itemLinkSelector: String = ".s-result-list > div[data-asin] h2 a[href]",
        var minPageSize: Int = 200_000,
        var storeContent: Boolean = false,
        var dbCheck: Boolean = false
)

data class ItemProcessorConfig(
        var urlPattern: Regex = ".+/dp/.+".toRegex(),
        var minPageSize: Int = 450_000,
        var storeContent: Boolean = false,
        var dbCheck: Boolean = false
)

data class DiffusingCrawlerConfig constructor(
        var label: String,

        var portalUrl: String,
        var excludedCategories: String,
        var excludedSearchAlias: MutableList<String> = mutableListOf(),
        var keywords: MutableList<String> = mutableListOf(),

        var portalPageIndexLinkCss: String = "#nav-search select option",

        /**
         * Index page
         * */
        var indexPageArgs: String = "-i 3d -parse -ignoreFailure",
        var indexPageUrlPattern: String = ".+&i=.+",
        var indexPageItemLinkSelector: String = ".s-result-list > div[data-asin] h2 a[href]",

        /**
         * Navigation
         * */
        var navigationCss: String = "ul.a-pagination",
        var lastLastPageCss: String = "li.a-normal ~ li.a-disabled",
        var nextPageCss: String = "li.a-last a",

        /**
         * Item page
         * */
        var itemPageUrlPattern: String = ".+/dp/.+"
) {
    @Transient
    val indexPageUrlRegex = indexPageUrlPattern.toRegex()
    @Transient
    val itemPageUrlRegex = itemPageUrlPattern.toRegex()
}
