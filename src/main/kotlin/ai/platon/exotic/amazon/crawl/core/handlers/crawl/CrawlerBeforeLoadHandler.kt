package ai.platon.exotic.amazon.crawl.core.handlers.crawl

import ai.platon.exotic.amazon.crawl.core.AmazonMetrics
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.exotic.amazon.tools.common.AmazonPageTraitsDetector
import ai.platon.exotic.amazon.tools.common.PageTraits
import ai.platon.scent.common.ScentStatusTracker

class CrawlerBeforeLoadHandler(
    private val statusTracker: ScentStatusTracker
): (UrlAware) -> Unit {
    private val amazonMetrics = AmazonMetrics.loadMetrics

    override fun invoke(url: UrlAware) {
        collectStatistics(url.url)
    }

    private fun collectStatistics(url: String): PageTraits {
        val character = PageTraits()

        amazonMetrics.pages.mark()

        val messageWriter = statusTracker.messageWriter
        if (AmazonPageTraitsDetector.isReviewPage(url)) {
            character.isReview = true
            amazonMetrics.review.mark()
        }

        when {
            AmazonPageTraitsDetector.isProductPage(url) -> {
                character.isItem = true
                amazonMetrics.asin.mark()
            }
            AmazonPageTraitsDetector.isPrimaryReviewPage(url) -> {
                character.isPrimaryReview = true
                amazonMetrics.pReview.mark()
                messageWriter.reportFetchedReviewLinks(url)
            }
            AmazonPageTraitsDetector.isSecondaryReviewPage(url) -> {
                character.isSecondaryReview = true
                amazonMetrics.sReview.mark()
                messageWriter.reportFetchedReviewLinks(url)
            }
            AmazonPageTraitsDetector.isLabeledPortalPage(url) -> {
                character.isLabeledPortal = true

                val label = AmazonPageTraitsDetector.getLabelOfPortal(url)
                when (label) {
                    "zgbs" -> amazonMetrics.zgbs.mark()
                    "most-wished-for" -> amazonMetrics.mWishedF.mark()
                    "new-releases" -> amazonMetrics.nRelease.mark()
                    "movers-and-shakers" -> amazonMetrics.mas.mark()
                }

                if (AmazonPageTraitsDetector.isSecondaryLabeledPortalPage(url)) {
                    when (label) {
                        "zgbs" -> amazonMetrics.szgbs.mark()
                        "most-wished-for" -> amazonMetrics.smWishedF.mark()
                        "new-releases" -> amazonMetrics.snRelease.mark()
                        "movers-and-shakers" -> amazonMetrics.smas.mark()
                    }
                }
            }
        }

        return character
    }
}
