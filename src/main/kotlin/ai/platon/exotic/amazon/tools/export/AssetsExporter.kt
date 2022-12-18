package ai.platon.exotic.amazon.tools.export

import ai.platon.pulsar.common.AppPaths
import ai.platon.scent.ScentSession
import ai.platon.scent.ql.h2.context.ScentSQLContexts
import com.aspose.cells.Workbook
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.listDirectoryEntries

typealias AsposeLoadOptions = com.aspose.cells.LoadOptions

class AssetsExporter {
    private val session: ScentSession = ScentSQLContexts.createSession()
    private val exportDir = AppPaths.DOC_EXPORT_DIR
        .resolve("amazon")
        .resolve("json")
    private val asinFields = mutableListOf<String>()

    fun generateAsinAssets() {
        val bigJsonBuilder = StringBuilder("[")
        val taskName = "asin-customer-hui"
        val asinDir = exportDir.resolve(taskName)
        var i = 0
        asinDir.listDirectoryEntries("*.json").forEach {
            ++i
            val json = Files.readString(it).trim().removeSurrounding("[", "]")
            val tree = jacksonObjectMapper().readTree(json)
            if (tree.isObject) {
                if (i == 1) {
                    tree.fieldNames().asSequence().toCollection(asinFields)
                    println("fieldNames:\t" + asinFields.joinToString(", "))
                }

                bigJsonBuilder.append(json).append(",")
                // println("$i.\t" + json.length + "\t" + obj.get("url") + " " + obj.get("title"))
            }
        }
        bigJsonBuilder.append(']')

        val exportPath = asinDir.resolveSibling("$taskName.json")
        Files.writeString(exportPath, bigJsonBuilder)
        println("Big json for [$taskName] exported to:\nfile://$exportPath")

        val options = AsposeLoadOptions()
        options.checkExcelRestriction = false

        val html = Workbook(exportPath.absolutePathString(), options)
        html.save("Output.html")
        println("Html exported to:\nfile${html.absolutePath}")

        val excel = Workbook(exportPath.absolutePathString(), options)
        excel.save("Output.xlsx")
        println("Excel exported to:\nfile${excel.absolutePath}")
    }
}

fun main() {
    val exporter = AssetsExporter()
    exporter.generateAsinAssets()
}
