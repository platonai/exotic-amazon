package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.metrics.AppMetrics

class AmazonMetrics(val ident: String) {
    companion object {
        val loadMetrics = AmazonMetrics("l")
        val extractMetrics = AmazonMetrics("x")
    }

    private val registry = AppMetrics.defaultMetricRegistry
    private fun multiMetric(name: String) = registry.multiMetric(this, ident, name)

    // primary zgbs pages
    val zgbs = multiMetric("zgbs")
    // secondary zgbs pages
    val szgbs = multiMetric("zgbsS")
    // no secondary zgbs pages
    val noszgbs = multiMetric("zgbsNoS")

    // primary most-wished-for pages
    val mWishedF = multiMetric("mWishedF")
    // secondary most-wished-for pages
    val smWishedF = multiMetric("mWishedFS")
    // no secondary most-wished-for pages
    val nosmWishedF = multiMetric("mWishedFNoS")

    // primary new-release pages
    val nRelease = multiMetric("nRelease")
    // secondary new-release pages
    val snRelease = multiMetric("nReleaseS")
    // no secondary new-release pages
    val nosnRelease = multiMetric("nReleaseNoS")

    /**
     *
     * */
    val mas = multiMetric("mas")
    val smas = multiMetric("smas")

    val asin = multiMetric("asin")

    val pReview = multiMetric("pReview")
    val sReview = multiMetric("sReview")
    val review = multiMetric("review")
    val pages = multiMetric("pages")
}
