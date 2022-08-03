package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.collect.FatLinkExtractor
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.message.LoadStatusFormatter
import ai.platon.pulsar.common.metrics.AppMetrics
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.nodes.count
import ai.platon.pulsar.dom.nodes.node.ext.isAnchor
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.exotic.amazon.tools.category.CategoryProcessor
import ai.platon.exotic.amazon.tools.common.AmazonPageTraitsDetector
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.scent.boot.autoconfigure.persist.WebNodeRepository
import ai.platon.scent.boot.autoconfigure.persist.findByNodeAnchorUrlOrNull
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.dom.web.TreeNodeDocument
import ai.platon.scent.mongo.WebNodePersistable
import ai.platon.scent.parse.html.ExtractCounter
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.util.*

@Component
class LinkCollector(
    private val session: ScentSession,
    private val mainGenerator: MainGenerator,
    private val statusTracker: ScentStatusTracker,
    private val webNodeRepository: WebNodeRepository,
) {
    private val logger = getLogger(this)

    /**
     * Extract hyperlinks from result set
     * */
    private val REVIEW_COLUMN_NAME = "reviewsurl"
    private val productRsHyperlinkColumns = listOf(REVIEW_COLUMN_NAME, "soldby")
    private val categoryProcessor = CategoryProcessor(session)

    private val registry = AppMetrics.defaultMetricRegistry
    private val updatedNodes = registry.counterAndGauge(this, "updatedNodes")
    private val recoveredNodes = registry.counterAndGauge(this, "updatedNodes")

    /**
     * Extract product links from best seller pages
     * */
    val fatLinkExtractor = FatLinkExtractor(session).also {
        it.normalizer.addFirst(AsinUrlNormalizer())
    }

    fun collectAsinLinksFromBestSeller(page: WebPage, document: FeaturedDocument) {
        // a typical option:
        // https://www.amazon.com/Best-Sellers-Video-Games-Xbox/zgbs/videogames/20972814011
        // -authToken vEcl889C-1-ea7a98d6157a8ca002d2599d2abe55f9 -expires PT24H -itemExpires PT720H
        // -label best-sellers-all -outLinkSelector "#zg-ordered-list a[href~=/dp/]"
        val url = page.url
        if (page.label != PredefinedTask.BEST_SELLERS.label) {
            // log.warn("Should has zgbs label, actual <{}> | {}", page.label, page.configuredUrl)
        }

        val args = "-itemExpires PT30D -outLinkSelector \"#zg-ordered-list a[href~=/dp/]\" -l asin"
        val options = session.options(args)
        fatLinkExtractor.parse(page, document, options)
    }

    fun collectReviewLinksFromProductPage(
        page: WebPage, sql: String, rs: ResultSet, queue: Queue<UrlAware>
    ): Hyperlink? {
        if (REVIEW_COLUMN_NAME !in sql) {
            return null
        }

        val processor = mainGenerator.reviewGenerator.indexPageProcessor
        val primaryReviewLink =
            collectHyperlinksFromResultSet(page, rs, listOf(REVIEW_COLUMN_NAME), queue) ?: return null

        return processor.createSecondaryReviewIndexLinks(primaryReviewLink.url, 1).firstOrNull()
    }

    fun collectSecondaryReviewLinks(
        page: WebPage,
        document: FeaturedDocument,
        rs: ResultSet,
        queue: Queue<UrlAware>
    ): Hyperlink? {
        // val newest3PageNumbers = mutableSetOf<String>()
        rs.beforeFirst()
        if (rs.next()) {
            val columnIndex = rs.findColumn("ratingcount")
            if (columnIndex > 0) {
                val totalReviews = rs.getString(columnIndex)
                    .substringAfterLast("|")
                    .substringBefore(" global reviews")
                    .replace(",", "").trim().toIntOrNull()
                if (totalReviews != null) {
                    return mainGenerator.reviewGenerator.indexPageProcessor
                        .createSecondaryReviewIndexLink(page.url, 2)
                        ?.also { queue.add(it) }
                }
            }
        }

        return null
    }

    fun collectSecondaryReviewLinksFromPagination(
        page: WebPage, document: FeaturedDocument, queue: Queue<UrlAware>
    ): Hyperlink? {
        val url = page.url
        val pageProcessor = mainGenerator.reviewGenerator.indexPageProcessor
        val hyperlink = pageProcessor.createSecondaryReviewLinksFromPagination(page, document)
        if (hyperlink == null) {
            logger.info("{}", LoadStatusFormatter(page, prefix = "Last Review"))
            return null
        }

        val nextPage = session.getOrNull(hyperlink.url)
        if (nextPage != null) {
            logger.info("Fetching next review index page | {}", hyperlink)
        } else {
            logger.info("{}", LoadStatusFormatter(page, prefix = "Loading"))
        }

        queue.add(hyperlink)

        statusTracker.messageWriter.reportGeneratedReviewLinks(hyperlink.url)

        return hyperlink
    }

    @Synchronized
    fun updateWebNode(page: WebPage, document: FeaturedDocument, queue: Queue<UrlAware>? = null): WebNodePersistable? {
        val url = page.url
        val label = page.label

        val parser = categoryProcessor.getParser(label) ?: return null
        val nodeDocument = TreeNodeDocument(url, document)
        val validate = nodeDocument.validate()
        if (!validate.isOK) {
            logger.info(validate.message)
            return null
        }

        val actualNode = parser.createWebNode(nodeDocument)

        var expectedNode = webNodeRepository.findByNodeAnchorUrlOrNull(url)
        var created = false
        if (expectedNode == null) {
            created = true
            expectedNode = WebNodePersistable(label, actualNode.plainNode)
        }

        val discoveredAnchors = if (created) {
            actualNode.plainNode.childAnchors
        } else {
            actualNode.plainNode.childAnchors - expectedNode.node.childAnchors
        }

        val updated = discoveredAnchors.isNotEmpty()
        if (created) {
            logger.info("[NEW] {} | {}", actualNode.path, actualNode.url)
        } else if (updated) {
            logger.info("[UPDATED] {} | {}", actualNode.path, actualNode.url)

            expectedNode.node.childAnchors.clear()
            expectedNode.node.childAnchors.addAll(actualNode.plainNode.childAnchors)
        }

        if (discoveredAnchors.isNotEmpty() && queue != null) {
            recoveredNodes.inc(discoveredAnchors.size.toLong())
            discoveredAnchors.mapTo(queue) { Hyperlink(it.url, args = page.args, referer = page.url) }
        }

        val stable = Duration.between(expectedNode.modifiedAt, Instant.now()).toDays() > 7
        when {
            created -> expectedNode.node.symbol = "N"
            updated -> expectedNode.node.symbol = "U"
            stable -> expectedNode.node.symbol = ""
        }

        if (created || updated) {
            updatedNodes.inc()
            webNodeRepository.save(expectedNode)
        }

        return expectedNode
    }

    /**
     * Collect hyperlinks from the labeled portal page
     *
     * There are two kind of hyperlinks:
     * 1. the links of the detail pages
     * 2. the next page
     *
     * Parse and save hyperlinks from portal pages so that we do not need to save the page content
     * which is resource consuming
     * */
    fun collectSecondaryLinksFromLabeledPortal(
        label: String, page: WebPage, document: FeaturedDocument, queue: Queue<UrlAware>
    ): Hyperlink? {
        // Collect the hyperlink of the next page
        val url = document.selectFirstOrNull("ul.a-pagination li.a-last a[href~=$label]")
            ?.attr("abs:href")
            ?.takeIf { UrlUtils.isValidUrl(it) }
        if (url != null) {
            // Notice: very important to inherit the page's load argument
            val nextMidnight = DateTimes.midnight.plusDays(1).toInstant(DateTimes.zoneOffset)
            val args = if (page.args.isBlank()) "-deadTime $nextMidnight" else "${page.args} -deadTime $nextMidnight"
            val hyperlink = Hyperlink(url, args = args, referer = page.url)

            queue.add(hyperlink)

            return hyperlink
        } else {
            if (!AmazonPageTraitsDetector.isSecondaryLabeledPortalPage(page.url)) {
                // report
                val container = document.body.selectFirstOrNull("#zg-ordered-list")
                val itemLinkCount = container?.count {
                    it.isAnchor && AmazonPageTraitsDetector.isProductPage(it.attr("abs:href"))
                } ?: 0
                val report = LoadStatusFormatter(page, withOptions = true)
                statusTracker.messageWriter.reportNoSecondaryPortalPage("$label | $itemLinkCount | $report")
            }
        }

        return null
    }

    fun collectHyperlinksFromResultSet(
        page: WebPage,
        rs: ResultSet,
        columns: List<String>,
        queue: Queue<UrlAware>
    ): Hyperlink? {
        rs.beforeFirst()
        if (rs.next()) {
            columns.forEach { columnName ->
                val columnIndex = rs.findColumn(columnName)
                if (columnIndex > 0) {
                    val value = rs.getString(columnIndex)
                    return collectHyperLinksFromRsValue(page, value, columnName, queue)
                }
            }
        }
        return null
    }

    /**
     * The column might be an ValueArray
     * */
    fun collectHyperLinksFromRsValue(
        page: WebPage,
        text: String,
        columnName: String,
        queue: Queue<UrlAware>
    ): Hyperlink? {
        // TODO: handle review link, do not fetch the primary link, just create one by hand
        text.removeSurrounding("(", ")").split(",")
            .filter { UrlUtils.isValidUrl(it.trim()) }.forEach { url ->
                val label = if (page.label.isBlank()) columnName else page.label
                val args = "-label $label"
                val hyperlink = Hyperlink(url, args = args, referer = page.url)
                if (queue.add(hyperlink)) {
                    collectStatistics(columnName, hyperlink)
                    return hyperlink
                }
            }
        return null
    }

    private fun collectStatistics(columnName: String, hyperlink: Hyperlink) {
        val metrics = statusTracker.metrics
        metrics.inc(ExtractCounter.xLinks)
        when (columnName) {
            "soldby" -> metrics.inc(ExtractCounter.xLinks1)
            "reviewsurl" -> {
                statusTracker.messageWriter.reportGeneratedReviewLinks(hyperlink.url)
                metrics.inc(ExtractCounter.xLinks2)
            }
        }
    }
}
