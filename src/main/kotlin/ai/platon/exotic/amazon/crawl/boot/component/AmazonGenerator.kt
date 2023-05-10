package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.ResidentTask
import ai.platon.exotic.amazon.crawl.core.isRunTime
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.exotic.amazon.crawl.generate.DailyAsinGenerator
import ai.platon.exotic.amazon.crawl.generate.PeriodicalSeedsGenerator
import ai.platon.exotic.amazon.crawl.generate.ReviewGenerator
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.common.ResourceWalker
import ai.platon.exotic.common.diffusing.config.DiffusingCrawlerConfig
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.UrlFeederHelper
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.persist.WebDb
import ai.platon.scent.ScentSession
import ai.platon.scent.boot.autoconfigure.component.ScentCrawlLoop
import ai.platon.scent.boot.autoconfigure.persist.TrackedUrlRepository
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Generate fetch tasks. All fetch tasks are some form of Pulsar URLs.
 * All urls are added to the global url pool, which is an instance of [ai.platon.pulsar.common.collect.UrlPool].
 * */
@Component
class AmazonGenerator(
    private val session: ScentSession,
    private val globalCacheFactory: GlobalCacheFactory,
    private val crawlLoop: ScentCrawlLoop,
    private val urlLoader: ExternalUrlLoader,
    private val trackedUrlRepository: TrackedUrlRepository,
    private val webDb: WebDb,
) {
    companion object {
        const val PERIODICAL_SEED_RESOURCE_BASE = "sites/amazon/crawl/generate/periodical"
    }

    private val logger = getLogger(AmazonGenerator::class)
    private val charset = Charset.defaultCharset()
    private val urlFeederHelper get() = UrlFeederHelper(crawlLoop.urlFeeder)
    private val isDev get() = ClusterTools.isDevInstance()

    val name = "amazon"
    val label = "20220801"

    val periodicalSeedDirectories: List<Path>
        get() {
            return ResourceWalker().list(PERIODICAL_SEED_RESOURCE_BASE)
                .filter { runCatching { Duration.parse(it.fileName.toString()) }.getOrNull() != null }
                .toList()
        }

    // create a new instant every day for that day
    val asinGenerator get() = DailyAsinGenerator.getOrCreate(session, urlLoader, crawlLoop.urlFeeder)
    val confusingConfig = createConfusionConfig(label)
    val reviewGenerator = ReviewGenerator(confusingConfig, session, globalCacheFactory, trackedUrlRepository)

    /**
     * Generate tasks at startup.
     * */
    fun generateStartupTasks() {
        val tasks = listOf(
            PredefinedTask.MOVERS_AND_SHAKERS,
            PredefinedTask.BEST_SELLERS,
            PredefinedTask.MOST_WISHED_FOR,
            PredefinedTask.NEW_RELEASES
        )
            .map { it.toResidentTask() }
            .filter { it.isRunTime() }

        logger.info("Generating startup tasks ...")
        generateLoadingTasks(tasks, true)
    }

    /**
     * Generate tasks at the specified time point.
     * */
    fun generateLoadingTasksAtTimePoint(truncateUnit: ChronoUnit) {
        val now = Instant.now().truncatedTo(truncateUnit)
        val tasks = PredefinedTask.values()
            .map { it.toResidentTask() }
            .filter { it.taskPeriod == Duration.ofDays(1) }
            .filter { it.startTime() == now }
            .filter { it.fileName != null }

        logger.info("Generating loading tasks at time point $now ...")

        generateLoadingTasks(tasks, true)
    }

    /**
     * Generate tasks from a resource file.
     * */
    fun generateLoadingTasks(residentTasks: List<ResidentTask>, refresh: Boolean) {
        try {
            val generator = PeriodicalSeedsGenerator(
                residentTasks,
                periodicalSeedDirectories, urlFeederHelper, urlLoader, session, webDb
            )

            generator.generate(refresh)
        } catch (t: Throwable) {
            logger.warn("Unexpected exception", t)
        }
    }

    fun generateAsinTasks() {
        val task = PredefinedTask.ASIN.toResidentTask()
        if (!task.isRunTime()) {
            return
        }

        asinGenerator.generate()
    }

    fun clearPredefinedTasksIfNotInRunTime() {
        val tasks = PredefinedTask.values()
            .map { it.toResidentTask() }
            .filterNot { it.isRunTime() }

        tasks.forEach { urlFeederHelper.removeAllLike(it.name) }
    }

    private fun createConfusionConfig(label: String): DiffusingCrawlerConfig {
        val keywords = when (label) {
            "insomnia" -> arrayOf("insomnia")
            "cups" -> arrayOf("cups", "mug", "demitasse", "beaker")
            else -> arrayOf("cups")
        }.map { URLEncoder.encode(it, charset) }

        val config = DiffusingCrawlerConfig(
            label = label,
            // portalUrl = "https://www.amazon.com",
            portalUrl = "https://www.amazon.ca/",
            excludedCategories = "Kindle Store, Digital Music, Prime Video, Movies & TV, Books，CDs & Vinyl, Audible Books & Originals",
        ).also {
            it.excludedSearchAlias.add("aps")
            it.keywords.addAll(keywords)
        }

        return config
    }
}
