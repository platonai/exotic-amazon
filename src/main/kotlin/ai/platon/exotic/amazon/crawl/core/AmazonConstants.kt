package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.AppPaths
import java.nio.file.Path

val ENABLE_ADVANCED_ASIN_GENERATE_STRATEGY = "enable.advanced.asin.generate.strategy"

/**
 * The path of the file to store fetched bestseller urls, for dev mode only
 * */
val PATH_FETCHED_BEST_SELLER_URLS: Path = AppPaths.REPORT_DIR.resolve("fetch/fetched-best-sellers")

/**
 * TODO: load from external config file
 * */
val BESTSELLER_LOAD_ARGUMENTS = "-expires 100d -requireSize 300000 -requireImages 50 -parse" +
        " -ignoreFailure -scrollCount 15 -scrollInterval 2s -label bestsellers"

/**
 * TODO: load from external config file
 * */
val ASIN_LINK_SELECTOR_IN_BS_PAGE = """.p13n-gridRow a[href*="/dp/"]:has(img)"""

/**
 * TODO: load from external config file
 * */
val ASIN_LOAD_ARGUMENTS = "-expires 30d -requireSize 600000 -requireImages 20 -parse -label asin"

/**
 * TODO: load from external config file
 * */
@Deprecated("Selector for next page has been changed")
val SECONDARY_BS_LINK_SELECTOR_IN_BS_PAGE = "ul.a-pagination li.a-last a:contains(Next page)," +
        " ul.a-pagination li.a-last a:contains(Page suivante)," +
        " ul.a-pagination li.a-last a:contains(Nächste Seite)"
