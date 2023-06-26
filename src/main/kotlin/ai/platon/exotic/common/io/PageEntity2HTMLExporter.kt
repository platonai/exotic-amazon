package ai.platon.exotic.common.io

import ai.platon.pulsar.common.AppFiles
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.session.PulsarSession
import java.nio.file.Path

class PageEntity2HTMLExporter(
    val session: PulsarSession
) {
    private val logger = getLogger(this)

    fun export(entity: String, page: WebPage): Path? {
        val path = ExportUtils.buildExportPath("0", entity, page, "htm")
        return exportTo(path, page)
    }

    fun exportTo(path: Path, page: WebPage): Path? {
        val url = page.url
        val pageModel = page.pageModel
        if (pageModel == null) {
            logger.warn("No page model | {}", url)
            return null
        }

        val numFields = pageModel.numFields
        logger.debug("There are {}/{}/{} (non-blank/non-null/total) extracted fields in page | {}",
            pageModel.numNonBlankFields,
            pageModel.numNonNullFields,
            numFields,
            url
        )

        val fieldGroup = pageModel.findById(1)
        if (fieldGroup != null) {
            val fields = fieldGroup.fieldsCopy

            val document = FeaturedDocument.createShell(path.toString())
            val lang = fields["language"] ?: fields["lang"]
            if (lang != null) {
                document.document.attr("lang", lang)
            }
            document.head.appendElement("meta").attr("charset", "utf-8")
            val dl = document.body.appendElement("dl")
            fields.forEach { (name, value) ->
                dl.appendElement("dt").text(name)
                dl.appendElement("dd").appendElement("pre").text(value ?: "")
            }

            AppFiles.saveTo(document.outerHtml, path, true)
        }

        return path
    }
}
