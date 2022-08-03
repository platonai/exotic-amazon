package ai.platon.exotic.amazon.crawl

import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.exotic.amazon.crawl.boot.component.LinkCollector
import ai.platon.scent.boot.autoconfigure.persist.WebNodeRepository
import ai.platon.scent.boot.autoconfigure.persist.findByNodeAnchorUrlOrNull
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestLinkCollector: TestBase() {
    val url = "https://www.amazon.com/Best-Sellers-Beauty/zgbs/beauty/ref=zg_bs_nav_0"
    val label = "zgbs"
    lateinit var page: WebPage
    lateinit var document: FeaturedDocument

    override var enableCrawlLoop = false

    @Autowired
    private lateinit var linkCollector: LinkCollector

    @Autowired
    private lateinit var webNodeRepository: WebNodeRepository

    @Before
    override fun setup() {
        page = session.load("$url -ignoreFailure -label $label")
        document = session.parse(page)

        assertTrue { page.protocolStatus.isSuccess }
        assertTrue { page.contentLength > 1000 }
    }

    @Test
    fun `When collect navigation page then the next page link exists`() {
        val queue = LinkedList<UrlAware>()
        linkCollector.collectSecondaryLinksFromLabeledPortal(label, page, document, queue)
        assertEquals(1, queue.size)
    }

    @Test
    fun `When create node then the next page link exists`() {
        val node = linkCollector.updateWebNode(page, document)
        assertNotNull(node)
        assertEquals(url, node.node.anchor.url)
        assertTrue { node.node.childAnchors.isNotEmpty() }

        val loadedNode = webNodeRepository.findByNodeAnchorUrlOrNull(url)
        assertNotNull(loadedNode)
        assertEquals(label, loadedNode.topic)
        assertTrue { loadedNode.node.childAnchors.isNotEmpty() }
    }
}
