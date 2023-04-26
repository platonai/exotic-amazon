package ai.platon.exotic.amazon.crawl.generate

import ai.platon.exotic.amazon.crawl.core.CollectedResidentTask
import ai.platon.exotic.amazon.crawl.core.ResidentTask
import ai.platon.exotic.amazon.crawl.core.createOptions
import ai.platon.exotic.amazon.crawl.core.isSupervised
import ai.platon.exotic.common.ClusterTools
import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.collect.UrlFeederHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.LocalFileHyperlinkCollector
import ai.platon.pulsar.common.collect.collector.UrlCacheCollector
import ai.platon.pulsar.common.collect.queue.LoadingQueue
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.persist.WebDb
import ai.platon.pulsar.persist.gora.generated.GWebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.WebDbLongTimeTask
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.isRegularFile

class PeriodicalSeedsGenerator(
    val tasks: List<ResidentTask>,
    private val searchDirectories: List<Path>,
    private val urlFeederHelper: UrlFeederHelper,
    private val urlLoader: ExternalUrlLoader,
    private val session: ScentSession,
    private val webDb: WebDb
) {
    private val logger = getLogger(this)
    private val taskTime = DateTimes.startOfDay()
    private val taskId = taskTime.toString()
    private val isDev get() = ClusterTools.isDevInstance()

    private val seedCache = mutableListOf<CollectedResidentTask>()

    private val fields = listOf(
        GWebPage.Field.PREV_FETCH_TIME,
        GWebPage.Field.PREV_CRAWL_TIME1,
    ).map { it.getName() }.toTypedArray()

    fun generate(refresh: Boolean): List<UrlCacheCollector> {
        loadTasksFromResources()

        logger.info("Collected tasks: {}", seedCache.joinToString { it.task.name })

        // for loading tasks
        val collectors = mutableListOf<UrlCacheCollector>()
        seedCache.forEach { task ->
            collectors.add(createUrlCacheCollector(task, refresh))
            // have a rest to reduce database pressure
            sleepSeconds(15)
        }

        return collectors
    }

    /**
     * Create a UrlCacheCollector for [collectedTask].
     * For every collected link, check the database if it's expired, only expired links are added to the url pool to
     * fetch.
     * */
    private fun createUrlCacheCollector(collectedTask: CollectedResidentTask, refresh: Boolean): UrlCacheCollector {
        val task = collectedTask.task

        // If the old collector is still alive, remove it
        removeOldCollectors(task)

        val collector = createUrlCacheCollector(task, urlLoader)

        if (refresh) {
            // clear both the in-memory cache and the external storage
            collector.deepClear()
        }

        if (collector.externalSize > 0) {
            logger.info(
                "There are still {} tasks in collector {}, do not generate",
                collector.estimatedSize, collector.name
            )

            return collector
        }

        val links = collectedTask.hyperlinks
        if (links.isEmpty()) {
            return collector
        }

        val readyQueue = collector.urlCache.nonReentrantQueue as LoadingQueue
        logger.info("Checking {} links for task <{}> in database", links.size, task.name)
        val (fetchedUrls, time) = loadFetchedUrls(links, task)

        links.asSequence().filterNot { it.url in fetchedUrls }
            .onEach { it.args = task.createOptions(taskId, taskTime, session).toString() }
            .toCollection(readyQueue)

        logger.info(
            "Generated {}/{} {} tasks with collector {} in {}, with {} ones removed(fetched)",
            readyQueue.size, readyQueue.externalSize, task.name, collector.name, time, fetchedUrls.size
        )

        return collector
    }

    private fun loadFetchedUrls(links: Collection<Hyperlink>, task: ResidentTask): JvmTimedValue<Set<String>> {
        logger.info("Checking {} links for task <{}> in database", links.size, task.name)
        if (task.refresh) {
            return JvmTimedValue(setOf(), Duration.ZERO)
        }

        return measureTimedValueJvm {
            WebDbLongTimeTask(webDb, task.name).getAll(links, fields)
                .filter { it.prevFetchTime >= task.startTime() } // already fetch during this period
                .mapTo(HashSet()) { it.url }
        }
    }

    private fun getRelevantCollectors(task: ResidentTask): List<UrlCacheCollector> {
        return urlFeederHelper.feeder.collectors
            .filter { task.name in it.name }
            .filterIsInstance<UrlCacheCollector>()
    }

    private fun removeOldCollectors(task: ResidentTask): List<UrlCacheCollector> {
        // If the collector is still alive, remove it
        return getRelevantCollectors(task).onEach {
            urlFeederHelper.removeAllLike(task.name)
        }
    }

    private fun createUrlCacheCollector(task: ResidentTask, urlLoader: ExternalUrlLoader): UrlCacheCollector {
        val priority = task.priority.value
        urlFeederHelper.remove(task.name)

        logger.info("Creating collector for {}", task.name)

        // the name addUrlPoolCollector is confusing, should be addUrlCacheCollector, corrected in 1.10.0+
        return urlFeederHelper.create(task.name, priority, urlLoader).also {
            it.deadTime = task.deadTime()
            it.labels.add(task.name)
        }
    }

    private fun loadTasksFromResources() {
        seedCache.clear()

        searchDirectories.forEach { dir ->
            val fileName = dir.fileName.toString()
            val period = runCatching { Duration.parse(fileName) }.getOrNull()
            if (period != null) {
                loadSeedsInDirectory(dir, period)
            }

//            ResourceWalker().walk(it.toAbsolutePath().toString(), 3) { path ->
//                loadTasksIfMatch(path)
//            }
        }
    }

    private fun loadSeedsInDirectory(dir: Path, period: Duration) {
        Files.list(dir)
            .filter { it.isRegularFile() }
            .filter { it.fileName.toString().endsWith(".txt") }.forEach { seedPath ->
                loadSeedsFromFile(seedPath, period)
            }
    }

    private fun loadSeedsFromFile(seedPath: Path, period: Duration) {
        val propsFileName = seedPath.fileName.toString().substringBeforeLast(".") + "properties"
        val propsFilePath = seedPath.resolveSibling(propsFileName)
        // TODO: parse props

        tasks.asSequence()
            .filter { it.taskPeriod == period }
            .filter { isSupervisor(it) }
            .filterNot { it.fileName.isNullOrBlank() }
//            .onEach { println(it.label + " " + it.taskPeriod + " " + period + " fileName: " + it.fileName + " " + seedPath) }
            .filter { seedPath.toString().endsWith(it.fileName!!) }
            .mapTo(seedCache) { CollectedResidentTask(it, collectHyperlinks(seedPath)) }
    }

    private fun isSupervisor(task: ResidentTask) = isDev || task.isSupervised()

    private fun collectHyperlinks(path: Path): Set<Hyperlink> {
        return collectHyperlinksTo(path, mutableSetOf())
    }

    private fun collectHyperlinksTo(path: Path, hyperlinks: MutableSet<Hyperlink>): Set<Hyperlink> {
        val collector = LocalFileHyperlinkCollector(path)

        val links = if (isDev) collector.hyperlinks.shuffled().take(100) else collector.hyperlinks

        val message = if (isDev) " (dev mode)" else ""
        logger.info("Loaded {} links$message | {}", links.size, path)

        hyperlinks.addAll(links)

        return hyperlinks
    }
}
