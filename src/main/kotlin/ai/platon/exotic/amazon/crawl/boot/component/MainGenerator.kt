package ai.platon.exotic.amazon.crawl.boot.component

import ai.platon.exotic.amazon.crawl.core.ClusterTools
import ai.platon.exotic.amazon.crawl.core.ConfigurableStreamingCrawler
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.exotic.amazon.crawl.generate.DailyAsinGenerator
import ai.platon.exotic.amazon.crawl.generate.LoadingSeedsGenerator
import ai.platon.exotic.amazon.crawl.generate.ReviewGenerator
import ai.platon.pulsar.common.Priority13
import ai.platon.pulsar.common.collect.CollectorHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.persist.WebDb
import ai.platon.scent.ScentSession
import ai.platon.scent.boot.autoconfigure.component.ScentCrawlLoop
import ai.platon.scent.boot.autoconfigure.persist.TrackedUrlRepository
import ai.platon.scent.common.PRIMER_DIFFUSING_TASK_LABEL
import ai.platon.scent.crawl.ResidentTask
import ai.platon.scent.crawl.diffusing.config.DiffusingCrawlerConfig
import ai.platon.scent.crawl.isRunTime
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class MainGenerator(
    private val session: ScentSession,
    private val globalCacheFactory: GlobalCacheFactory,
    private val crawlLoop: ScentCrawlLoop,
    private val urlLoader: ExternalUrlLoader,
    private val trackedUrlRepository: TrackedUrlRepository,
    private val webDb: WebDb,
) {
    private val logger = getLogger(MainGenerator::class)
    private val charset = Charset.defaultCharset()

    val isDev get() = ClusterTools.isDevInstance()
    val label = PRIMER_DIFFUSING_TASK_LABEL
    val globalCache get() = globalCacheFactory.globalCache

    val collectorHelper get() = CollectorHelper(crawlLoop.urlFeeder)

    // create a new instant every day for that day
    val asinGenerator get() = DailyAsinGenerator.getOrCreate(session, urlLoader, crawlLoop.urlFeeder)

    val diffusingConfig = createDiffusingCrawlerConfig(label)
    val reviewGenerator = ReviewGenerator(diffusingConfig, session, globalCacheFactory, trackedUrlRepository)

    val name = "amazon"

    private val periodicalSeedDirectories get() = listOf("pt30m", "pt1h", "pt12h", "pt24h")
        .map { buildPeriodicalSeedDirectory(name, it) }

    fun generate(priority: Priority13) {

    }

    fun generateStartupTasks() {
        val tasks = listOf(
            PredefinedTask.MOVERS_AND_SHAKERS,
            PredefinedTask.BEST_SELLERS,
            PredefinedTask.MOST_WISHED_FOR,
            PredefinedTask.NEW_RELEASES
        )
            .map { it.toResidentTask() }
            .onEach { it.ignoreTTL = isDev }
            .filter { it.isRunTime() }

        logger.info("Generating startup tasks")
        generateLoadingTasks(tasks, true)
    }

    fun generateLoadingTasksAtTimePoint(truncateUnit: ChronoUnit) {
        val now = Instant.now().truncatedTo(truncateUnit)
        val tasks = PredefinedTask.values()
            .map { it.toResidentTask() }
            .filter { it.taskPeriod == Duration.ofDays(1) }
            .filter { it.startTime() == now }
            .filter { it.fileName != null }

        logger.info("Generating loading tasks at time point ...")

        generateLoadingTasks(tasks, true)
    }

    fun generateLoadingTasks(residentTasks: List<ResidentTask>, refresh: Boolean) {
        try {
            val generator = LoadingSeedsGenerator(residentTasks,
                periodicalSeedDirectories, collectorHelper, urlLoader, webDb)

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

        tasks.forEach { collectorHelper.removeAllLike(it.name) }
    }

    private fun createDiffusingCrawlerConfig(label: String): DiffusingCrawlerConfig {
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

    private fun buildPeriodicalSeedDirectory(projectName: String, duration: String): String {
        return ConfigurableStreamingCrawler.periodicalSeedResourceDirectoryTemplate
            .replace("{project}", projectName)
            .replace("{period}", duration)
    }
}
