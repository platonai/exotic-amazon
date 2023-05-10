package ai.platon.exotic.common.io

import ai.platon.pulsar.common.AppFiles
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.crawl.common.URLUtil
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.ql.h2.utils.ResultSetUtils
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.sql.ResultSet
import java.time.MonthDay

class ResultSet2JsonExporter {

    fun export(entity: String, page: WebPage, rs: ResultSet): Path {
        val entities = ResultSetUtils.getTextEntitiesFromResultSet(rs)
        val json = GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(entities)
        val path = ExportUtils.buildExportPath("0", entity, page, "json")
        return AppFiles.saveTo(json, path, true)
    }
}
