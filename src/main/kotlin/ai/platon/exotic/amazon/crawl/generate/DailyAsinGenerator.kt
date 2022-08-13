package ai.platon.exotic.amazon.crawl.generate

import ai.platon.exotic.common.ClusterTools
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.tools.common.AsinUrlNormalizer
import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.collect.CollectorHelper
import ai.platon.pulsar.common.collect.ExternalUrlLoader
import ai.platon.pulsar.common.collect.UrlFeeder
import ai.platon.pulsar.common.collect.collector.UrlCacheCollector
import ai.platon.pulsar.common.collect.queue.AbstractLoadingQueue
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.context.support.AbstractPulsarContext
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.gora.generated.GWebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.WebDbLongTimeTask
import ai.platon.scent.crawl.SeedProperties
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.*
import java.util.*

/**
 * Generator asin pages (product pages) every day.
 * The asin urls are extracted from best-seller pages, rotate every month.
 * */
class DailyAsinGenerator(
    val session: ScentSession,
    val dayOfMonth: Int
) {
    companion object {
        private val logger = getLogger(DailyAsinGenerator::class)

        private val isDev = ClusterTools.isDevInstance()

        private val month get() = MonthDay.now().monthValue

        private var generator: DailyAsinGenerator? = null

        private lateinit var collectorHelper: CollectorHelper

        private lateinit var lastUrlLoader: ExternalUrlLoader

        private var asinCollector: UrlCacheCollector? = null

        private var reviewCollector: UrlCacheCollector? = null

        var testMode = false
        var minAsinTasks = if (isDev) 0 else 200
        var maxAsinTasks = if (isDev) 200 else Int.MAX_VALUE

        @Synchronized
        fun getOrCreate(
            session: ScentSession,
            urlLoader: ExternalUrlLoader,
            urlFeeder: UrlFeeder
        ): DailyAsinGenerator {
            val dayOfMonth = MonthDay.now().dayOfMonth
            lastUrlLoader = urlLoader
            collectorHelper = CollectorHelper(urlFeeder)

            val oldGenerator = generator
            val isNewCollector = generator?.dayOfMonth != dayOfMonth || testMode
            if (isNewCollector) {
                asinCollector = null
                reviewCollector = null
                generator = DailyAsinGenerator(session, dayOfMonth)
            }

            if (isNewCollector) {
                oldGenerator?.generatePath?.let { Files.deleteIfExists(it) }
                oldGenerator?.externalClear()
            }

            getOrCreateCollectors()

            return generator!!
        }

        /**
         * Check if this host is the supervisor node, only the supervisor node generates ths tasks.
         * All nodes take turns serving as the supervisor.
         * */
        fun isSupervisor(): Boolean {
            if (isDev) {
                return true
            }

            val dayOfMonth = LocalDate.now().dayOfMonth
            val mod = dayOfMonth % ClusterTools.crawlerCount
            if (ClusterTools.instancePartition != mod) {
                logger.info("Do not generate asins, supervisor is crawl{}", mod)
                return false
            }

            return true
        }

        private fun getOrCreateCollectors() {
            if (asinCollector == null) {
                asinCollector = createCollector(PredefinedTask.ASIN, lastUrlLoader)
            }

            if (reviewCollector == null) {
                reviewCollector = createCollector(PredefinedTask.REVIEW, lastUrlLoader)
            }
        }

        private fun createCollector(task: PredefinedTask, urlLoader: ExternalUrlLoader): UrlCacheCollector {
            val priority = task.priority.value
            collectorHelper.remove(task.name)

            logger.info("Creating collector for {}", task.name)

            return collectorHelper.addUrlPoolCollector(task.name, priority, urlLoader).also {
                it.labels.add(task.name)
            }
        }
    }

    private val context get() = session.context as AbstractPulsarContext
    private val webDb get() = context.webDb
    // The leaf categories of best-sellers
    private val bestSellerResource = "sites/amazon/crawl/inject/seeds/category/best-sellers/leaf-categories.txt"
    private val propertiesResource = "sites/amazon/crawl/inject/seeds/category/best-sellers/seeds.properties"
    // The path of the file to store fetched best-seller urls, for dev mode only
    private val fetchedBestSellerUrlPath = AppPaths.REPORT_DIR.resolve("fetch/fetched-best-sellers")
    private val normalizer = AsinUrlNormalizer()
    private val expires = PredefinedTask.ASIN.expires
    private val deadTime = PredefinedTask.ASIN.deadTime()
    private val taskTime = DateTimes.startOfDay()
    private val taskId = DateTimes.startOfDay().toString()

    // A temporary file that holds the generated urls
    private val generatePath
        get() = AppPaths.REPORT_DIR.resolve("generate/asin/$month/$dayOfMonth.txt")
    // Keep the best-seller pages temporary to extract asin urls later
    private val relevantBestSellers = mutableListOf<WebPage>()
    // The minimal interval to check best-seller pages in the database
    private val zgbsMinimalCheckInterval = Duration.ofMinutes(10)
    // The next time to check best-seller pages in the database
    private var zgbsNextCheckTime = Instant.now()

    private var generatedCount = 0
    private var averageVividLinkCount = 100
    private var collectedCount = 0

    val asinCollector: UrlCacheCollector?
        get() = Companion.asinCollector

    val reviewCollector: UrlCacheCollector?
        get() = Companion.reviewCollector

    fun generate(): Queue<UrlAware> {
        getOrCreateCollectors()

        if (!isSupervisor()) {
            return LinkedList()
        }

        val collector = asinCollector
        if (collector == null) {
            logger.warn("Asin collector is null")
            return LinkedList()
        }

        val queue = collector.urlCache.nonReentrantQueue as AbstractLoadingQueue
        val externalSize = queue.externalSize
        if (externalSize > minAsinTasks) {
            logger.info("Still {} asin tasks in seed repository, do not generate", externalSize)
            return queue
        } else {
            logger.info("Less than {} asin tasks({}) in seed repository, re-generating tasks",
                minAsinTasks, externalSize)
        }

        collectedCount = collector.collectedCount
        generateTo(queue)

        logger.info("Generated {}/{} asin urls", queue.size, queue.externalSize)

        return queue
    }

    /**
     * Seed urls, with load arguments are stored in a file, here we read them and parse all configured seed urls into a
     * queue of NormUrl.
     *
     * We will fetch each seed page, parse it into a document, extract all required hyperlinks from it,
     * keep all the links into WebPage.vividLinks. We do not write the page content to database, which is useless and
     * time-consuming.
     * */
    @Synchronized
    fun generateTo(sink: MutableCollection<UrlAware>) {
        val propertiesContent = ResourceLoader.readString(propertiesResource)
        val props: SeedProperties = JavaPropsMapper().readValue(propertiesContent)
        val options = session.options(props.loadOptions)

        // val n = if (ClusterTools.isDevInstance()) 10 else Int.MAX_VALUE

        prepareFiles()
        relevantBestSellers.clear()
        generateRelevantBestSellersTo(relevantBestSellers)

        var count = 0
        relevantBestSellers.forEach { count += writeAsinTasks(it) }
        if (count != 0) {
            logger.info("Written {} asin urls to {}", count, generatePath)
        }

        val asins = if (Files.exists(generatePath)) {
            readAsinTasks(options)
        } else listOf()

        if (asins.isEmpty()) {
            logger.warn("No asin urls in file | {}", generatePath)
            return
        }

        val now = Instant.now()
        relevantBestSellers.onEach { it.prevCrawlTime1 = now }.forEach { session.persist(it) }
        logger.info(
            "Updated {} relevant best sellers with prevCrawlTime1 to be now({})",
            relevantBestSellers.size, LocalDateTime.now()
        )

        val asinOptions = session.options("-expires 30d")
        val (fetchedUrls, time) = measureTimedValueJvm {
            WebDbLongTimeTask(webDb, "ASIN")
                .getAll(asins, GWebPage.Field.PREV_FETCH_TIME)
                .filterNot { asinOptions.isExpired(it.prevFetchTime) }
                .mapTo(HashSet()) { it.url }
        }

        val readyTasks = if (testMode) {
            asins
        } else {
            asins.filterNot { it.url in fetchedUrls }.shuffled()
        }

        logger.info(
            "Generated {} asin links from {} best sellers in {}, with {} ones removed(fetched)",
            readyTasks.size, relevantBestSellers.size, time, fetchedUrls.size
        )

        generatedCount += readyTasks.size
        // shuffle the urls so they have fair chance to run
        if (isDev) {
            readyTasks.take(50).toCollection(sink)
        } else {
            readyTasks.toCollection(sink)
        }
    }

    @Synchronized
    fun writeAsinTasks(page: WebPage): Int {
        val referer = page.url

        val urls = page.vividLinks.keys.mapNotNullTo(HashSet()) { normalizer(it.toString()) }

        try {
            val text = urls.joinToString("\n") { "$it $referer" }
            Files.writeString(generatePath, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            Files.writeString(generatePath, "\n", StandardOpenOption.APPEND)
        } catch (e: IOException) {
            logger.warn("Failed to write vivid links", e)
        }

        return urls.size
    }

    @Synchronized
    fun readAsinTasks(options: LoadOptions): List<Hyperlink> {
        val urlRegex = options.outLinkPattern.toRegex()
        val args = "-i $expires -parse -ignoreFailure -label asin" +
                " -taskId $taskId -taskTime $taskTime -deadTime $deadTime"

        if (Files.exists(generatePath)) {
            val asins = Files.readAllLines(generatePath).asSequence()
                .filter { !it.trim().startsWith("#") }
                .map { it.split("\\s+".toRegex()) }
                .filter { it.size == 2 }
                .associate { normalizeOrEmpty(it[0]) to it[1] }
                .filter { it.key.matches(urlRegex) }
                .map { Hyperlink(it.key, args = args, referer = it.value) }
                .toList()

            logger.info("Read {} asins from file | {}", asins.size, generatePath)

            return asins
        }

        return listOf()
    }

    @Synchronized
    fun generateRelevantBestSellersTo(bestSellerPages: MutableCollection<WebPage>) {
        if (zgbsNextCheckTime > Instant.now()) {
            logger.warn("The next best seller check will be at {}", zgbsNextCheckTime)
            return
        }
        zgbsNextCheckTime += zgbsMinimalCheckInterval

        val primaryZgbs = if (isDev) {
            LinkExtractors.fromFile(fetchedBestSellerUrlPath).filter { "zgbs" in it && "pg=2" !in it }
        } else {
            LinkExtractors.fromResource(bestSellerResource)
        }

        val secondaryZgbs = primaryZgbs.map { "$it/ref=zg_bs_pg_2?_encoding=UTF8&pg=2" }
        var unorderedZgbs = (primaryZgbs + secondaryZgbs).toList()

        if (isDev) {
            unorderedZgbs = unorderedZgbs.take(30)
            logger.info("Checking {} best sellers in database (development mode)", unorderedZgbs.size)
        } else {
            logger.info("Checking {} best sellers in database", unorderedZgbs.size)
        }

        val startTime = Instant.now()
        val sortedBestSellers = WebDbLongTimeTask(webDb, "Relevant BS")
            .getAll(unorderedZgbs.asSequence(), GWebPage.Field.PREV_CRAWL_TIME1)
            .toList()
            .sortedBy { it.prevCrawlTime1 }

        if (sortedBestSellers.isEmpty()) {
            logger.warn("No best sellers loaded")
            return
        }

        val days = 28
        val linkCountPerPage = averageVividLinkCount.coerceAtLeast(1)
        val collectedPageCount = collectedCount / linkCountPerPage
        // the endTime for ASIN task is 23:30, so minus Duration.ofMinutes(30)
        val remainingSeconds = PredefinedTask.ASIN.endTime().epochSecond - Instant.now().epochSecond
        val pagesPerSecond = 1.0 * ClusterTools.crawlerCount
        val maxBSPageCount = (pagesPerSecond * remainingSeconds / linkCountPerPage).toInt()
        var bsPageCount = (sortedBestSellers.size / days - collectedPageCount)
            .coerceAtLeast(1)
            .coerceAtMost(maxBSPageCount)
        // crawl asin between 14:00 ~ 23:30, about 36000 seconds/pages every day
        // every best seller has less than 50 pages, so we need about 36000 / 50 = 720 best seller pages
        val fields = arrayOf(GWebPage.Field.PREV_CRAWL_TIME1.toString(), GWebPage.Field.VIVID_LINKS.toString())

        if (testMode) {
            bsPageCount = sortedBestSellers.size
            sortedBestSellers.parallelStream().forEach {
                val page = session.load(it.url, "-storeContent true -requireSize 1000")
            }
        }

        val finalBestSellers = sortedBestSellers.asSequence().distinct().take(bsPageCount)
            .mapNotNull { webDb.getOrNull(it.url, fields = fields) }
            .toList()

        if (finalBestSellers.isEmpty()) {
            logger.warn("There is no best sellers (unexpected)")
        }

        val linkCount = finalBestSellers.sumOf{ it.vividLinks.size }
        averageVividLinkCount = linkCount / finalBestSellers.size
        bestSellerPages.addAll(finalBestSellers)

        logger.info(
            "Loaded {}/{}/{} best sellers (max {} ones for today) with {} links in {} | prev crawl time: {} -> {}",
            bestSellerPages.size, finalBestSellers.size, sortedBestSellers.size, maxBSPageCount,
            linkCount,
            DateTimes.elapsedTime(startTime),
            finalBestSellers.first().prevCrawlTime1, finalBestSellers.last().prevCrawlTime1
        )
    }

    fun clearAll() {
        val collector = asinCollector ?: return
        collector.urlCache.clear()
        externalClear()
    }

    fun externalClear() {
        val collector = asinCollector ?: return

        val queues = collector.urlCache.queues.filterIsInstance<AbstractLoadingQueue>()
        val count = queues.sumOf { it.externalSize }
        if (count > 0) {
            logger.info("Clear {} external asin tasks", count)
            queues.forEach { it.externalClear() }
        } else {
            logger.info("No external asin tasks to clear")
        }
    }

    @Synchronized
    private fun prepareFiles() {
        val directory = generatePath.parent
        if (Files.exists(generatePath)) {
            val predicate = { path: Path, attr: BasicFileAttributes ->
                attr.isRegularFile && "$dayOfMonth.txt" in path.fileName.toString()
            }
            val count = Files.find(directory, 1, predicate).count()
            if (count > 0) {
                Files.move(generatePath, directory.resolve("$dayOfMonth.txt.$count"))
            }
        } else {
            Files.createDirectories(directory)
        }
    }

    private fun normalizeOrEmpty(url: String): String {
        return normalizer(url) ?: ""
    }
}
