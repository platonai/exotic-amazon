package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.AppPaths
import java.nio.file.Path

const val VAR_FILTER_DEPTH = "FilterDepth"

/**
 * The path of the file to store fetched best-seller urls, for dev mode only
 * */
val PATH_FETCHED_BEST_SELLER_URLS: Path = AppPaths.REPORT_DIR.resolve("fetch/fetched-best-sellers")

val ASIN_LINK_SELECTOR_IN_BS_PAGE = ".p13n-gridRow a[href*=/dp/]:has(img)"

val SECONDARY_BS_LINK_SELECTOR_IN_BS_PAGE = "ul.a-pagination li.a-last a:contains(Next page)"
