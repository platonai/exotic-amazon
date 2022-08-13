package ai.platon.exotic.amazon.crawl.generate

import ai.platon.exotic.common.ResourceWalker
import ai.platon.exotic.common.ClusterTools
import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.collect.CollectorHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.LocalFileHyperlinkCollector
import ai.platon.pulsar.common.collect.collector.UrlCacheCollector
import ai.platon.pulsar.common.collect.queue.LoadingQueue
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.measureTimedValueJvm
import ai.platon.pulsar.common.sleepSeconds
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.persist.WebDb
import ai.platon.pulsar.persist.gora.generated.GWebPage
import ai.platon.scent.common.WebDbLongTimeTask
import ai.platon.scent.crawl.CollectedResidentTask
import ai.platon.scent.crawl.ResidentTask
import ai.platon.scent.crawl.createArgs
import ai.platon.scent.crawl.isSupervised
import java.nio.file.Path

class LoadingSeedsGenerator(
    val tasks: List<ResidentTask>,
    val searchDirectories: List<String>,
    val collectorHelper: CollectorHelper,
    val urlLoader: ExternalUrlLoader,
    val webDb: WebDb
) {
    private val logger = getLogger(this)
    private val taskTime = DateTimes.startOfDay()
    private val taskId = taskTime.toString()
    private val isDev = ClusterTools.isDevInstance()

    private val loadedTasks = mutableListOf<CollectedResidentTask>()

    private val fields = listOf(
        GWebPage.Field.PREV_FETCH_TIME,
        GWebPage.Field.PREV_CRAWL_TIME1,
    ).map { it.getName() }.toTypedArray()

    fun generate(refresh: Boolean): List<UrlCacheCollector> {
        loadTasksFromResources()

        logger.info("Collected tasks: {}", loadedTasks.joinToString { it.task.name })

        // for loading tasks
        val collectors = mutableListOf<UrlCacheCollector>()
        loadedTasks.forEach { task ->
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
        val (fetchedUrls, time) = measureTimedValueJvm {
            WebDbLongTimeTask(webDb, task.name).getAll(links, fields)
                .filter { it.prevFetchTime >= task.startTime() }
                .mapTo(HashSet()) { it.url }
        }

        links.asSequence().filterNot { it.url in fetchedUrls }
            .onEach { it.args = task.createArgs(taskId, taskTime).toString() }
            .toCollection(readyQueue)

        logger.info(
            "Generated {}/{} {} tasks with collector {} in {}, with {} ones removed(fetched)",
            readyQueue.size, readyQueue.externalSize, task.name, collector.name, time, fetchedUrls.size
        )

        return collector
    }

    private fun getRelevantCollectors(task: ResidentTask): List<UrlCacheCollector> {
        return collectorHelper.feeder.collectors
            .filter { task.name in it.name }
            .filterIsInstance<UrlCacheCollector>()
    }

    private fun removeOldCollectors(task: ResidentTask): List<UrlCacheCollector> {
        // If the collector is still alive, remove it
        return getRelevantCollectors(task).onEach {
            collectorHelper.removeAllLike(task.name)
        }
    }

    private fun createUrlCacheCollector(task: ResidentTask, urlLoader: ExternalUrlLoader): UrlCacheCollector {
        val priority = task.priority.value
        collectorHelper.remove(task.name)

        logger.info("Creating collector for {}", task.name)

        // the name addUrlPoolCollector is confusing, should be addUrlCacheCollector, corrected in 1.10.0+
        return collectorHelper.addUrlPoolCollector(task.name, priority, urlLoader).also {
            it.deadTime = task.deadTime()
            it.labels.add(task.name)
        }
    }

    private fun loadTasksFromResources() {
        loadedTasks.clear()

        searchDirectories.forEach {
            ResourceWalker().walk(it, 3) { path ->
                loadTasksIfMatch(path)
            }
        }
    }

    private fun loadTasksIfMatch(path: Path) {
        val collectedResidentTasks = tasks
            .filter { isSupervisor(it) }
            .filter { !it.fileName.isNullOrBlank() }
            .filter { path.endsWith(it.fileName!!) }
            .map { CollectedResidentTask(it, collectHyperlinks(path)) }

        loadedTasks.addAll(collectedResidentTasks)
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
