package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.AppPaths
import java.nio.file.Path

/**
 * The path of the file to store fetched best-seller urls, for dev mode only
 * */
val PATH_FETCHED_BEST_SELLER_URLS: Path = AppPaths.REPORT_DIR.resolve("fetch/fetched-best-sellers")
