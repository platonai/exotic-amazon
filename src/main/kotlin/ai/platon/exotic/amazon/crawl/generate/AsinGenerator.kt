package ai.platon.exotic.amazon.crawl.generate

import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.common.ClusterTools
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.UrlFeeder
import ai.platon.pulsar.common.collect.UrlFeederHelper
import ai.platon.pulsar.common.collect.collector.UrlCacheCollector
import ai.platon.pulsar.common.getLogger
import ai.platon.scent.ScentSession
import java.nio.file.Files
import java.time.LocalDateTime

object AsinGenerator {

    private val logger = getLogger(AsinGenerator::class)

    private val isDev get() = ClusterTools.isDevInstance()

    private var dailyAsinGenerator: DailyAsinGenerator? = null

    private lateinit var urlFeederHelper: UrlFeederHelper

    private lateinit var lastUrlLoader: ExternalUrlLoader

    var asinCollector: UrlCacheCollector? = null

    var reviewCollector: UrlCacheCollector? = null

    var testMode = false

    /**
     * Should create a new generator every day.
     * */
    @Synchronized
    fun computeDailyAsinGenerator(
        session: ScentSession,
        urlLoader: ExternalUrlLoader,
        urlFeeder: UrlFeeder
    ): DailyAsinGenerator {
        val now = LocalDateTime.now()
        val monthValue = now.monthValue
        val dayOfMonth = now.dayOfMonth

        lastUrlLoader = urlLoader
        urlFeederHelper = UrlFeederHelper(urlFeeder)

        val oldGenerator = dailyAsinGenerator
        val isNewCollector = dailyAsinGenerator?.dayOfMonth != dayOfMonth || testMode
        if (isNewCollector) {
            asinCollector = null
            reviewCollector = null
            dailyAsinGenerator = DailyAsinGenerator(session, monthValue, dayOfMonth)
        }

        if (isNewCollector) {
            oldGenerator?.dailyAsinTaskPath?.let { Files.deleteIfExists(it) }
            oldGenerator?.externalClear()
        }

        computeCollectors()

        return dailyAsinGenerator!!
    }

    fun computeCollectors() {
        if (asinCollector == null) {
            asinCollector = createCollector(PredefinedTask.ASIN, lastUrlLoader)
        }

        if (reviewCollector == null) {
            reviewCollector = createCollector(PredefinedTask.REVIEW, lastUrlLoader)
        }
    }

    private fun createCollector(task: PredefinedTask, urlLoader: ExternalUrlLoader): UrlCacheCollector {
        val priority = task.priority.value
        urlFeederHelper.remove(task.name)

        logger.info("Creating collector for {} with url loader {}", task.name, urlLoader::class)

        return urlFeederHelper.create(task.name, priority, urlLoader).also {
            it.labels.add(task.name)
        }
    }
}
