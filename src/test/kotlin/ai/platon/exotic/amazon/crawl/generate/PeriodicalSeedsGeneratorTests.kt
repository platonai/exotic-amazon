package ai.platon.exotic.amazon.crawl.generate

import ai.platon.exotic.amazon.crawl.TestBase
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.exotic.common.JarTool
import ai.platon.exotic.common.ResourceWalker
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.getLogger
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.jar.JarFile
import kotlin.io.path.absolutePathString
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PeriodicalSeedsGeneratorTests: TestBase() {
    private val logger = getLogger(this)
    private val period = "pt24h"
    private val resource = "sites/amazon/crawl/generate/periodical/$period/best-sellers.txt"

    @Test
    fun TestLoadSeedsFromResource() {
        val path = ResourceWalker().getPath(resource)
        assertNotNull(path)

        loadSeedsFromPathAndAssert(path, period, "internal resource")
    }

    @Test
    fun TestLoadSeedsFromExternalFile() {
        val period = "pt24h"
        val resource = "sites/amazon/crawl/generate/periodical/$period/best-sellers.txt"
        val path = ResourceWalker().getPath(resource)
        assertNotNull(path)

        val path2 = AppPaths.TMP_DIR.resolve(period).resolve("best-sellers.txt")
        Files.createDirectories(path2.parent)
        Files.copy(path, path2)

        loadSeedsFromPathAndAssert(path2, period, "external file")
        Files.deleteIfExists(path2)
    }

    @Test
    fun testLoadSeedsFromExternalJar() {
        val resourcePrefix = "sites/amazon/crawl/generate/periodical"
        val period = "pt24h"
        val resource = "$resourcePrefix/$period/best-sellers.txt"
        val sourcePath = ResourceWalker().getPath(resource)
        assertNotNull(sourcePath)
        val rootPath = ResourceWalker().getPath(resourcePrefix)
        assertNotNull(rootPath)
        val rootPathString = rootPath.absolutePathString()
        val sourcePathString = sourcePath.absolutePathString()

        val tool = JarTool()
        tool.startManifest()
        val jarFile = AppPaths.TMP_DIR.resolve("TestLoadSeedsFromJar.jar").toString()
        tool.openJar(jarFile).use {  target ->
            logger.info("rootPathString: \n{}", rootPathString)
            logger.info("sourcePathString: \n{}", sourcePathString)

            tool.addFile(target, rootPathString, sourcePathString)
        }

        logger.info("Entries in jar | {}", jarFile)
        val jar = JarFile(jarFile)
        println(jar.name)
        var k = 0
        jar.entries().asIterator().forEach {
            ++k
            println("$k.\t" + it.name)
        }

        val path2 = Paths.get("$jarFile!/$period/best-sellers.txt")
        assertNotNull(path2)
        loadSeedsFromPathAndAssert(path2, period, "external jar")
        Files.deleteIfExists(path2)
    }

    @Test
    fun TestListSeedDirectory() {

    }

    @Test
    fun TestLoadTasksFromSearchDirectories() {

    }

    private fun loadSeedsFromPathAndAssert(path: Path, period: String, message: String) {
        val predefinedTask = listOf(PredefinedTask.BEST_SELLERS).map { it.toResidentTask() }
        val generator = amazonGenerator.createPeriodicalSeedsGenerator(predefinedTask)

        val collectedTasks = generator.loadSeedsFromFile(path, Duration.parse(period))
        collectedTasks.forEach { task ->
            logger.info("Collected task: {} {} | {}", task.task.name, task.task.fileName, message)

            assertEquals("zgbs", task.task.label)
            assertEquals("BEST_SELLERS", task.task.name)
            assertEquals("best-sellers.txt", task.task.fileName)
        }
    }
}
