package ai.platon.exotic.amazon.tools.category

import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.urls.PlainUrl
import ai.platon.pulsar.crawl.AbstractCrawler
import ai.platon.pulsar.session.AbstractPulsarSession
import ai.platon.scent.common.web.GeoAnchor
import ai.platon.scent.dom.web.*
import ai.platon.scent.ql.h2.context.ScentSQLContext
import ai.platon.scent.ql.h2.context.withSQLContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger

class CategoryTreeCrawler(
    val props: WebTreeProperties,
    val context: ScentSQLContext
) : AbstractCrawler(context) {
    private val logger = LoggerFactory.getLogger(CategoryTreeCrawler::class.java)

    val loadOptions = session.options(props.loadArguments)

    val processedUrls = ConcurrentSkipListSet<String>()
    val knownPaths = ConcurrentSkipListMap<String, String>()
    val outputDirectory = AppPaths.getTmp("category").resolve(DateTimes.formatNow("HHmm"))
    val categoryTreePath = outputDirectory.resolve("amazon-${props.label}-categories.txt")

    private lateinit var rootCategoryNode: WebNode
    private val crawlRound = AtomicInteger()

    val urlNormalizer = CategoryUrlNormalizer()
    private val categoryDOMParser = WebTreeNodeParser(props, urlNormalizer)

    private val abstractSession get() = session as AbstractPulsarSession
    private val globalCache get() = abstractSession.context.globalCacheFactory.globalCache
    private val urlPool get() = globalCache.urlPool
    private val crawlLoop get() = session.context.crawlLoops.first()

    init {
        val headLine = String.format(
            "%s | %s | %s | %s | %s | %s\n",
            "id", "parent id", "depth", "numSubcategories", "category path", "url"
        )

        Files.createDirectories(outputDirectory)
        Files.writeString(categoryTreePath, headLine, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }

    fun crawl() {
        val page = session.load(props.topCategoryUrl, "-i 1s -ignoreFailure -njr 3")
        val document = session.parse(page)
        document.absoluteLinks()

        val path = session.export(document)
        logger.info("Top category page is exported to file://$path")

        rootCategoryNode = WebNode(GeoAnchor(page.url, "Root"))
        val treeNodeDocument = TreeNodeDocument(page.url, document)
        val categoryTreeFragment = treeNodeDocument.categoryTreeFragmentOrNull ?: return
        val subcategoryTreeElement = categoryTreeFragment.subcategoryTreeElementOrNull ?: return

        categoryDOMParser.extractSubcategoryAnchorsTo(subcategoryTreeElement, rootCategoryNode)

        val topSubcategoryAnchors = if (props.requiredTopCategories.isNotEmpty()) {
            rootCategoryNode.plainNode.childAnchors.filter { it.text in props.requiredTopCategories }
        } else {
            rootCategoryNode.plainNode.childAnchors
        }

        rootCategoryNode.plainNode.childAnchors.clear()
        rootCategoryNode.plainNode.childAnchors.addAll(topSubcategoryAnchors)

        logger.info(
            "There are {} subcategories to analyze under root [{}]",
            rootCategoryNode.plainNode.childAnchors.size, rootCategoryNode.path
        )
        rootCategoryNode.plainNode.childAnchors
            .mapIndexed { i, anchor -> "${1 + i}.\t${anchor.text}" }
            .joinToString("\n")
            .also { logger.info("\n$it") }
        crawlCategoriesRecursively(1, rootCategoryNode)

        val text = categoryDOMParser.categoryUrls.joinToString("\n")
        val allCategoryUrlsFile = outputDirectory.resolve("all-category-urls.txt")
        Files.writeString(allCategoryUrlsFile, text, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }

    fun report() {
        try {
            val target = "amazon-${props.label}-categories.txt." + DateTimes.formatNow("HHmmss")
            Files.move(categoryTreePath, categoryTreePath.resolveSibling(target))
        } catch (e: IOException) {
            logger.warn(e.message)
        }

        format(rootCategoryNode)
        generateScripts()
    }

    private fun crawlCategoriesRecursively(depth: Int, category: WebNode) {
        crawlRound.incrementAndGet()
        logger.info(
            "Round $crawlRound.\tdepth [$depth] [${category.path}] {} subcategories | {}",
            category.plainNode.childAnchors.size, category.url)

        val loadOptions2 = loadOptions.clone()
        if (depth <= 3) {
            // loadOptions2.expires = Duration.ZERO
        }

        loadAndCreateSubcategories(category, loadOptions2)
        if (category.children.isEmpty()) {
            logger.info("No subcategory under [{}]", category.path)
            return
        }

        val urls = category.children.flatMap { it.plainNode.childAnchors.map { PlainUrl(it.url) } }
        logger.info("Loading {} links in depth $depth", urls.size)
        urlPool.normalCache.nReentrantQueue.addAll(urls)
        // wait for the urls are loaded
        var i = 0
        while (crawlLoop.urlFeeder.iterator().hasNext()) {
            if (i++ % 6 == 0) {
                logger.info(crawlLoop.abstract)
            }
            sleepSeconds(5)
        }

        category.children.parallelStream().forEach { subcategory ->
            if (subcategory.url == category.url) {
                logger.warn("Subcategory has the same url with the parent, ignore | {} <- {}",
                    subcategory.url, category.url)
                return@forEach
            }

            if (subcategory.path == category.path) {
                logger.warn("Subcategory has the same path with the parent, ignore | {} <- {}",
                    subcategory.path, category.path
                )
                return@forEach
            }

            val categoryDepth = subcategory.depth
            logger.info(
                "Crawling subcategory [{}] category depth:{} traversal depth {} under [{}]({})",
                subcategory.path, categoryDepth, depth, category.path, category.depth
            )
            crawlCategoriesRecursively(1 + depth, subcategory)
        }

        if (crawlRound.get() % 1000 == 0) {
            format(rootCategoryNode)
        }
    }

    /**
     *
     * */
    private fun loadAndCreateSubcategories(node: WebNode, loadOptions: LoadOptions) {
        val counter = AtomicInteger()
        node.plainNode.childAnchors.parallelStream().forEach { anchor ->
            try {
                val j = counter.incrementAndGet()
                loadAndCreateSubcategory(j, anchor, node, loadOptions)
            } catch (e: IllegalApplicationContextStateException) {
                logger.warn("Illegal app state, exit")
                return@forEach
            } catch (e: Throwable) {
                logger.warn("Unexpected throwable", e)
            }
        }
    }

    @Throws(IllegalApplicationContextStateException::class)
    private fun loadAndCreateSubcategory(
        j: Int, anchor: GeoAnchor, parentCategory: WebNode, loadOptions: LoadOptions) {
        processedUrls.add(anchor.url)

        val subcategoryUrl = anchor.url
        val subcategoryName = anchor.text
        val expectedPath = parentCategory.path + " > " + subcategoryName
        val knownPathUrl = knownPaths[expectedPath]
        if (knownPathUrl != null) {
            logger.info("Path is already known | {} | {}", expectedPath, knownPathUrl)
            return
        }

        logger.info("$j.\t[{}](expected) Loading category page | {}", expectedPath, subcategoryUrl)
        val categoryPage = session.load(subcategoryUrl, loadOptions)
        if (categoryPage.crawlStatus.isFetched) {
            val document = session.parse(categoryPage)
            // require(document.body.area > 1000 * 1000)
            document.absoluteLinks()
            val subcategory = categoryDOMParser.createWebNode(anchor, document, parentCategory)
            knownPaths[subcategory.path] = subcategory.url
            logger.info("$j.\t[{}](actual) Category is created | {}", subcategory.path, subcategoryUrl)
        } else {
            logger.warn("$j.\tFailed to load category [{}](expected) | {}", expectedPath, subcategoryUrl)
        }
    }

    private fun generateScripts() {
        val fileName = categoryTreePath.fileName
        val path = outputDirectory.resolve("gen.sh")
        val cmd = """
                cat $fileName | cut -d "|" -f 1-5 > $fileName.no.links.txt
                cat $fileName | grep "0 | Root" > $fileName.leaf.txt

            """.trimIndent()
        Files.writeString(path, cmd, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxrw-r--"))
    }

    private fun format(category: WebNode) {
        formatRecursively(category)
    }

    private fun formatRecursively(category: WebNode) {
        formatCategoryNode(category)
        category.children.forEach {
            formatRecursively(it)
        }
    }

    private fun formatCategoryNode(category: WebNode) {
        val line = category.toSlimNode().toString()
        // val l = "-".repeat(category.depth) + category.path
        println(line)
        Files.writeString(categoryTreePath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    override fun await() {
    }
}

class CategoryTreeCrawlerRunner(val config: WebTreeProperties) {
    fun run() {
        withSQLContext {
            val crawler = CategoryTreeCrawler(config, it)

//            crawler.testUrlNormalizer()
//            crawler.testUrlNormalizerCategories(knownCategoryResource, urlIdent)

//            val knowCategories = KnownCategoryLoader(config, it.createSession())
//            knowCategories.load(config.resource, config.label)

            crawler.crawl()
            crawler.report()
        }
    }
}
