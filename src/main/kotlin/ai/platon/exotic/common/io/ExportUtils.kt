package ai.platon.exotic.common.io

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.crawl.common.URLUtil
import ai.platon.pulsar.persist.WebPage
import java.nio.file.Path
import java.time.LocalDateTime

object ExportUtils {

    /**
     * Build the path to export a file
     * */
    fun buildExportPath(batch: String, entity: String, page: WebPage, extension: String): Path {
        val url = page.url
        val now = LocalDateTime.now()
        val month = now.month
        val dayOfMonth = now.dayOfMonth
        val hour = now.hour
        val domain = URLUtil.getDomainName(url) ?: "unknown"
        val filename = AppPaths.fromUri(page.url,"", ".$extension")
        val path = AppPaths.DOC_EXPORT_DIR
            .resolve(domain)
            .resolve(extension)
            .resolve(entity)
            .resolve("$month")
            .resolve("$dayOfMonth")
            .resolve("$hour")
            .resolve(batch)
            .resolve(filename)
        return path
    }
}
