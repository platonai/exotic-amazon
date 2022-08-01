package ai.platon.exotic.amazon.tools.category

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.ResourceLoader
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class Discovery {

    fun rebuildDiscovered() {
        val prefix = "category/discovered/"
        val categories = listOf("0", "1", "2", "3")
            .map { "$prefix/collect/crawl$it.txt" }
            .map { ResourceLoader.readAllLines(it) }
            .flatten()

        val zgbs = categories.filter { it.contains("zgbs") }
        val mostWishedF = categories.filter { it.contains("most-wished-for") }
        val newReleases = categories.filter { it.contains("new-releases") }

        mapOf(
            "best-sellers.txt" to zgbs,
            "most-wished-for.txt" to mostWishedF,
            "new-releases.txt" to newReleases,
        ).forEach { fileName, c ->
            val path = AppPaths.getTmp("discovered/$fileName")
            Files.createDirectories(path.parent)
            Files.writeString(path, c.joinToString("\n"), StandardOpenOption.CREATE)
        }
    }
}

fun main() {
    Discovery().rebuildDiscovered()
}
