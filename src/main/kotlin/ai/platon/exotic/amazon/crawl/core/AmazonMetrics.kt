package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.metrics.AppMetrics

class AmazonMetrics(val ident: String) {
    companion object {
        /**
         * Metrics for load phase
         * */
        val loadMetrics = AmazonMetrics("l")
        /**
         * Metrics for extract phase
         * */
        val extractMetrics = AmazonMetrics("x")
    }

    private val registry = AppMetrics.defaultMetricRegistry
    /**
     * A multi metric records total, daily and hourly metrics at the same time
     * */
    private fun multiMetric(name: String) = registry.multiMetric(this, ident, name)

    // number of primary best-seller (zgbs) pages (the first page)
    val zgbs = multiMetric("zgbs")
    // number of secondary best-seller (zgbs) pages
    val szgbs = multiMetric("zgbsS")
    // number of primary zgbs pages that have no secondary ones
    val noszgbs = multiMetric("zgbsNoS")

    // number of primary most-wished-for pages (the first page)
    val mWishedF = multiMetric("mWishedF")
    // number of secondary most-wished-for pages
    val smWishedF = multiMetric("mWishedFS")
    // number of primary most-wished-for pages that have no secondary ones
    val nosmWishedF = multiMetric("mWishedFNoS")

    // number of primary new-release pages (the first page)
    val nRelease = multiMetric("nRelease")
    // number of secondary new-release pages
    val snRelease = multiMetric("nReleaseS")
    // number of primary new-release pages that have no secondary ones
    val nosnRelease = multiMetric("nReleaseNoS")

    /**
     * number of primary movers-and-shakers pages (the first page)
     * */
    val mas = multiMetric("mas")
    /**
     * number of secondary movers-and-shakers pages
     * */
    val smas = multiMetric("smas")

    /**
     * product (ASIN) pages
     * */
    val asin = multiMetric("asin")

    /**
     * number of primary review pages (the first page)
     * */
    val pReview = multiMetric("pReview")
    /**
     * number of secondary review pages
     * */
    val sReview = multiMetric("sReview")
    /**
     * number of total review pages
     * */
    val review = multiMetric("review")
    /**
     * number of total pages
     * */
    val pages = multiMetric("pages")
}
