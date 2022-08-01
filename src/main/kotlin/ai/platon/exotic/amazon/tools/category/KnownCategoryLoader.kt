package ai.platon.exotic.amazon.tools.category

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.sleepSeconds
import ai.platon.pulsar.common.urls.PlainUrl
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.session.AbstractPulsarSession
import ai.platon.pulsar.session.PulsarSession
import ai.platon.scent.common.ClusterTools
import ai.platon.scent.dom.web.SlimWebNode
import ai.platon.scent.dom.web.WebTreeProperties
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentSkipListSet

class KnownCategoryLoader(
    val config: WebTreeProperties,
    val session: PulsarSession
) {
    private val log = LoggerFactory.getLogger(CategoryTreeCrawler::class.java)

    val loadOptions = session.options(config.loadArguments)
    private val urlNormalizer = CategoryUrlNormalizer()

    private val abstractSession get() = session as AbstractPulsarSession
    private val globalCache get() = abstractSession.context.globalCacheFactory.globalCache
    private val urlPool get() = globalCache.urlPool
    private val crawlLoop get() = session.context.crawlLoops.first()

    fun load(resource: String, urlIdent: String) {
        val categoryUrls = ClusterTools.partition(ResourceLoader.readAllLines(resource))
            .asSequence()
//            .filter { line -> config.requiredTopCategories.isEmpty() || config.requiredTopCategories.any { it in line } }
            .map { SlimWebNode.parse(it) }
            .filter { it.numChildren != 0 }
            .mapNotNull { UrlUtils.normalizeOrNull(it.url) }
            .map { PlainUrl(it, args = config.loadArguments) }
            .toMutableList()

        log.info("Loading known categories from resource with {} links: ", categoryUrls.size)
        urlPool.normalCache.nReentrantQueue.addAll(categoryUrls)
        var i = 0
        while (crawlLoop.urlFeeder.iterator().hasNext()) {
            if (i++ % 6 == 0) {
                log.info(crawlLoop.abstract)
            }
            sleepSeconds(10)
        }

        log.info("Collecting categories from known category pages")
        val collectedCategoryUrls = ConcurrentSkipListSet<PlainUrl>()
        categoryUrls.parallelStream().forEach {
            val document = session.loadDocument(it.url, loadOptions)
            document.select("ul a[href~=/$urlIdent/]")
                .map { it.attr("abs:href") }
                .mapTo(collectedCategoryUrls) {
                    PlainUrl(urlNormalizer.normalize(it), args = config.loadArguments)
                }
        }
        categoryUrls.clear()

        log.info("Loading categories from known category pages with {} links: ", collectedCategoryUrls.size)
        urlPool.normalCache.nReentrantQueue.addAll(collectedCategoryUrls)
        i = 0
        while (crawlLoop.urlFeeder.iterator().hasNext()) {
            if (i++ % 6 == 0) {
                log.info(crawlLoop.abstract)
            }
            sleepSeconds(10)
        }
    }
}
