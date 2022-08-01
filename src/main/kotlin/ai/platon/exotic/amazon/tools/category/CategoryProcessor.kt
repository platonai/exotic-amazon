package ai.platon.exotic.amazon.tools.category

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.scent.ScentSession
import ai.platon.scent.dom.web.WebTreeNodeParser
import ai.platon.scent.dom.web.WebTreeProperties

class CategoryProcessor(
    val session: ScentSession
) {
    val requiredCategoryResourceName = "sites/amazon/category/required-top-categories.txt"
    val requiredTopCategories: MutableList<String> = ResourceLoader.readAllLines(requiredCategoryResourceName)
        .filter { !it.startsWith("#") }
        .toMutableList()

    val bsConf = WebTreeProperties(
        PredefinedTask.BEST_SELLERS.label,
        "sites/amazon/category/best-sellers/best-sellers.txt",
        "https://www.amazon.com/Best-Sellers/zgbs",
        loadArguments = "",
        requiredTopCategories = requiredTopCategories
    )

    val mwfConf = WebTreeProperties(
        PredefinedTask.MOST_WISHED_FOR.label,
        "sites/amazon/category/most-wished-for/most-wished-for.txt",
        "https://www.amazon.com/most-wished-for",
        loadArguments = "",
        requiredTopCategories = requiredTopCategories
    )

    val nrConf = WebTreeProperties(
        PredefinedTask.NEW_RELEASES.label,
        "sites/amazon/category/new-releases/new-releases.txt",
        "https://www.amazon.com/gp/new-releases",
        loadArguments = "",
        requiredTopCategories = requiredTopCategories
    )

    val masConf = WebTreeProperties(
        PredefinedTask.MOVERS_AND_SHAKERS.label,
        "sites/amazon/category/movers-and-shakers/movers-and-shakers.txt",
        "https://www.amazon.com/gp/movers-and-shakers",
        loadArguments = "",
        requiredTopCategories = requiredTopCategories
    )

    val urlNormalizer = CategoryUrlNormalizer()
    val parsers = mutableListOf<WebTreeNodeParser>()

    init {
        listOf(bsConf, mwfConf, nrConf).mapTo(parsers) {
            WebTreeNodeParser(it, urlNormalizer)
        }
    }

    fun getParser(label: String): WebTreeNodeParser? {
        return parsers.firstOrNull { it.props.label == label }
    }

    fun getParser(config: WebTreeProperties) = getParser(config.label)
}
