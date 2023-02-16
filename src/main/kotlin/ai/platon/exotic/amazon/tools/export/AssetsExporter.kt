package ai.platon.exotic.amazon.tools.export

import ai.platon.pulsar.common.AppContext
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.config.CapabilityTypes.APP_ID_STR
import com.aspose.cells.Workbook
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.listDirectoryEntries

typealias AsposeLoadOptions = com.aspose.cells.LoadOptions

class AssetsExporter {
    private val ident = AppContext.APP_IDENT
    private val exportDir = AppPaths.DOC_EXPORT_DIR
        .resolve("amazon")
        .resolve("json")
    private val asinFields = mutableListOf<String>()

    fun generateAsinAssets() {
        val bigJsonBuilder = StringBuilder("[")
        val taskName = "asin-customer-hui"
        val asinDir = exportDir.resolve(taskName)

        println(asinDir)

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

        val jsonPath = asinDir.resolveSibling("$taskName.json")
        Files.writeString(jsonPath, bigJsonBuilder)
        println("Big json for [$taskName] saved to:\nfile://$jsonPath")

        val options = AsposeLoadOptions()
        options.checkExcelRestriction = false

        val excel = Workbook(jsonPath.absolutePathString(), options)
        excel.save("asin.amazon.$ident.xlsx")
//        println("Excel exported to:\nfile://${excel.absolutePath}")
    }
}

fun main(args: Array<String>) {
    System.setProperty(APP_ID_STR, "com")
    val exporter = AssetsExporter()
    exporter.generateAsinAssets()
}
