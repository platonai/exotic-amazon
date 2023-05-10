package ai.platon.exotic.amazon.crawl.common

import ai.platon.exotic.common.ResourceWalker
import ai.platon.pulsar.common.ResourceLoader
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertTrue

class ResourceWalkerTests {

    @Test
    fun testResource() {
        val resource = "nlp/colors.txt"
        val uri = ResourceLoader.getResource(resource)
        // println(uri)
        assertTrue(uri.toString().matches("jar:file:.+/$resource".toRegex()))
    }

    @Test
    fun testWalk() {
        val resourceBase = "nlp"
        var exists = false
        val resourceWalker = ResourceWalker()
        resourceWalker.walk(resourceBase, 3) { path ->
            println(path)
            if (path.toString().contains("colors.txt")) {
                exists = true
            }
        }
        assertTrue(exists)
    }

    @Test
    fun testListResourceEntries() {
        val resourceWalker = ResourceWalker()
        val paths = resourceWalker.list("nlp")
        paths.forEach {
            println(it)
        }
        assertTrue(paths.toString().contains("nlp/colors.txt"))
    }
}
