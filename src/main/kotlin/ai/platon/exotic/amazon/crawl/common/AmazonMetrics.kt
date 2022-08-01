package ai.platon.exotic.amazon.crawl.common

import ai.platon.pulsar.common.metrics.AppMetrics

class AmazonMetrics(val ident: String) {
    companion object {
        val loadMetrics = AmazonMetrics("l")
        val extractMetrics = AmazonMetrics("x")
    }

    private val registry = AppMetrics.defaultMetricRegistry
    private fun multiMetric(name: String) = registry.multiMetric(this, ident, name)

    val zgbs = multiMetric("zgbs")
    val szgbs = multiMetric("zgbsS")
    val noszgbs = multiMetric("zgbsNoS")

    val mWishedF = multiMetric("mWishedF")
    val smWishedF = multiMetric("mWishedFS")
    val nosmWishedF = multiMetric("mWishedFNoS")

    val nRelease = multiMetric("nRelease")
    val snRelease = multiMetric("nReleaseS")
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
