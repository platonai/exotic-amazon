package ai.platon.exotic.common.io

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.crawl.common.URLUtil
import ai.platon.pulsar.persist.WebPage
import java.nio.file.Path
import java.time.MonthDay

object ExportUtils {

    /**
     * Build the path to export a file
     * */
    fun buildExportPath(batch: String, entity: String, page: WebPage, extension: String): Path {
        val url = page.url
        val month = MonthDay.now().month
        val dayOfMonth = MonthDay.now().dayOfMonth
        val domain = URLUtil.getDomainName(url) ?: "unknown"
        val filename = AppPaths.fromUri(page.url,"", ".$extension")
        val path = AppPaths.DOC_EXPORT_DIR
            .resolve(domain)
            .resolve(extension)
            .resolve(entity)
            .resolve("$month")
            .resolve("$dayOfMonth")
            .resolve(batch)
            .resolve(filename)
        return path
    }
}
