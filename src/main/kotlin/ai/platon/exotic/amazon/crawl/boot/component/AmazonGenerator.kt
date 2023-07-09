package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.core.*
import ai.platon.exotic.amazon.crawl.generate.AsinGenerator
import ai.platon.exotic.amazon.crawl.generate.PeriodicalSeedsGenerator
import ai.platon.exotic.amazon.crawl.generate.ReviewGenerator
import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.common.ResourceWalker
import ai.platon.exotic.common.diffusing.config.DiffusingCrawlerConfig
import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.alwaysTrue
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
        const val PERIODICAL_SEED_BASE_DIR_KEY = "periodical.seed.base.dir"
        const val PERIODICAL_SEED_BASE_DIR_DEFAULT = "sites/amazon/crawl/generate/periodical"
    }

    private val logger = getLogger(AmazonGenerator::class)
    private val charset = Charset.defaultCharset()
    private val periodicalSeedBaseDir = session.sessionConfig[PERIODICAL_SEED_BASE_DIR_KEY, PERIODICAL_SEED_BASE_DIR_DEFAULT]
    private val urlFeederHelper get() = UrlFeederHelper(crawlLoop.urlFeeder)
    private val isDev get() = ClusterTools.isDevInstance()

    /**
     * Asin generation strategy, supported strategies:
     * 1. IMMEDIATELY: once a bestseller page is fetched, the asin links are extracted and submitted immediately.
     * 2. MONTHLY: all asin links are extracted when bestseller pages were fetched, and all they will be fetched in a month.
     * */
    val asinGenerateStrategy = session.unmodifiedConfig[ENABLE_ADVANCED_ASIN_GENERATE_STRATEGY, "IMMEDIATELY"]

    val name = "amazon"
    val label = "20220801"

    // create a new instant every day for that day
    val dailyAsinGenerator get() = AsinGenerator.computeDailyAsinGenerator(session, urlLoader, crawlLoop.urlFeeder)
    val confusingConfig = createConfusionConfig(label)
    val reviewGenerator = ReviewGenerator(confusingConfig, session, globalCacheFactory, trackedUrlRepository)

    fun createPeriodicalSeedsGenerator(residentTasks: List<ResidentTask>): PeriodicalSeedsGenerator {
        return PeriodicalSeedsGenerator(
            residentTasks,
            searchPeriodicalSeedDirectories(), urlFeederHelper, urlLoader, session, webDb
        )
    }

    /**
     * Generate tasks at startup.
     * */
    fun generateStartupTasks() {
        logger.info("Generating startup tasks ...")

        val tasks = listOf(
            PredefinedTask.BEST_SELLERS
        )
            .map { it.toResidentTask() }
            .filter { it.isRunTime() }

        generateLoadingTasks(tasks, true)

        generateAsinTasks()
    }

    /**
     * Generate tasks at the specified time point.
     * */
    fun generateLoadingTasksAtTimePoint(truncateUnit: ChronoUnit) {
        val now = Instant.now().truncatedTo(truncateUnit)
        val tasks = PredefinedTask.values()
            .filter { it == PredefinedTask.BEST_SELLERS }
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
        if (alwaysTrue()) {
            return
        }

        try {
            val generator = createPeriodicalSeedsGenerator(residentTasks)
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

        when (asinGenerateStrategy) {
            "MONTHLY" -> dailyAsinGenerator.generate()
            "IMMEDIATELY" -> {
                // see AmazonJdbcSinkSQLExtractor.collectHyperlinks -> collectAndSubmitASINLinks
            }
            else -> {
                // Nothing to do
            }
        }
    }

    fun clearPredefinedTasksIfNotInRunTime() {
        val tasks = PredefinedTask.values()
            .map { it.toResidentTask() }
            .filterNot { it.isRunTime() }

        tasks.forEach { urlFeederHelper.removeAllLike(it.name) }
    }

    fun searchPeriodicalSeedDirectories(): List<Path> {
        return ResourceWalker().list(periodicalSeedBaseDir)
            .filter { DateTimes.parseDurationOrNull(it.fileName.toString()) != null }
            .toList()
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
