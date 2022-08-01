package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.exotic.amazon.tools.category.CategoryProcessor
import ai.platon.scent.boot.autoconfigure.persist.WebNodeRepository
import ai.platon.scent.dom.web.TreeNodeDocument
import ai.platon.scent.dom.web.WebNode
import ai.platon.scent.dom.web.WebTreeNodeParser
import ai.platon.scent.jackson.scentObjectMapper
import ai.platon.scent.mongo.WebNodePersistable
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

class WebTreeNodeParserTests : TestBase() {
    val topCategoryUrl = "https://www.amazon.com/Best-Sellers-Beauty/zgbs/beauty"
    val topCategoryName = "Beauty & Personal Care"

    lateinit var processor: CategoryProcessor

    lateinit var parser: WebTreeNodeParser
    lateinit var page: WebPage

    lateinit var document: FeaturedDocument

    lateinit var nodeDocument: TreeNodeDocument
    lateinit var topCategoryNode: WebNode

    override var enableCrawlLoop = false

    @Autowired
    lateinit var webNodeRepository: WebNodeRepository

    @Before
    override fun setup() {
        webNodeRepository.deleteAll()

        page = session.load(topCategoryUrl)
        document = session.parse(page)

        processor = CategoryProcessor(session)
        parser = processor.getParser(processor.bsConf)!!

        nodeDocument = TreeNodeDocument(topCategoryUrl, document)
        topCategoryNode = parser.createWebNode(nodeDocument)
    }

    @Test
    fun `When build category tree then success`() {
        assertTrue { topCategoryNode.plainNode.childAnchors.isNotEmpty() }

        val parentNode = topCategoryNode
        parentNode.plainNode.childAnchors.forEach { anchor ->
            val subcategoryDocument = session.loadDocument(anchor.url)
            val node = parser.createWebNode(anchor, subcategoryDocument, topCategoryNode)
            // assertTrue { node.childAnchors.isNotEmpty() }
            assertTrue { node.path.contains(topCategoryName) }
            println(node.toSlimNode().toString())
            parentNode.children.add(node)
        }

        val topic = processor.bsConf.label
        webNodeRepository.save(WebNodePersistable(topic, parentNode.plainNode))
        webNodeRepository.saveAll(parentNode.children.map { WebNodePersistable(topic, it.plainNode) })

        val nodes = webNodeRepository.findAllByTopic(topic)
        assertTrue { nodes.isNotEmpty() }
        // assertEquals(1 + parentNode.children.size, nodes.size)
        println(scentObjectMapper().writeValueAsString(nodes))
    }
}
