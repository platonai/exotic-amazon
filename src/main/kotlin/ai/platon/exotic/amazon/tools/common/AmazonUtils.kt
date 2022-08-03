package ai.platon.exotic.amazon.tools.common

import ai.platon.exotic.amazon.crawl.core.AmazonMetrics
import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.parse.html.ExtractCounter

object AmazonUtils {

    fun calculateTotalPages(args: String?): Int {
        return calculateTotalReviews(args) / 10 + 1
    }

    fun calculateTotalReviews(args: String?): Int {
        if (args == null) return 0

        var totalReviews = args.substringAfter("-totalReviews ", "0")
                .let { Strings.getFirstInteger(it, 0) }

//        totalReviews = when {
//            totalReviews == 0 -> 50
//            totalReviews > 500 -> 500
//            else -> totalReviews
//        }

        return totalReviews
    }

    fun detectTraits(page: WebPage,
                     isAsin: Boolean, amazonMetrics: AmazonMetrics, statusTracker: ScentStatusTracker
    ): PageTraits {
        val url = page.url
        val character = PageTraits()

        val messageWriter = statusTracker.messageWriter

        val metrics = statusTracker.metrics
        metrics.inc(ExtractCounter.xFitPages)
        amazonMetrics.pages.mark()

        if (AmazonPageTraitsDetector.isReviewPage(url)) {
            character.isReview = true
            amazonMetrics.review.mark()
        }

        when {
            isAsin -> {
                character.isItem = true
                amazonMetrics.asin.mark()
            }
            AmazonPageTraitsDetector.isPrimaryReviewPage(url) -> {
                character.isPrimaryReview = true
                amazonMetrics.pReview.mark()
                messageWriter.reportFetchedReviewLinks(page.url)
            }
            AmazonPageTraitsDetector.isSecondaryReviewPage(url) -> {
                character.isSecondaryReview = true
                amazonMetrics.sReview.mark()
                messageWriter.reportFetchedReviewLinks(page.url)
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
