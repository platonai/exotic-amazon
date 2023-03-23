package ai.platon.exotic.amazon.crawl.boot.component.common

import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.persist.ext.label
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.crawl.parse.ParseResult
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.WebPage
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicBoolean

abstract class AbstractSinkAwareSQLExtractor(
    session: ScentSession,
    scentStatusTracker: ScentStatusTracker,
    globalCacheFactory: GlobalCacheFactory,
    conf: ImmutableConfig,
) : AbstractSQLExtractor(session, scentStatusTracker, globalCacheFactory, conf) {

    private val logger = getLogger(AbstractSinkAwareSQLExtractor::class)
    private val taskLogger = getLogger(AbstractSinkAwareSQLExtractor::class, ".Task")

    private var totalMillis = 0L

    protected val closed = AtomicBoolean()

    override fun onAfterFilter(page: WebPage, document: FeaturedDocument, parseResult: ParseResult) {
        super.onAfterFilter(page, document, parseResult)
    }

    override fun onAfterExtract(page: WebPage, document: FeaturedDocument, rs: ResultSet?): ResultSet? {
        val resultSet = super.onAfterExtract(page, document, rs)

        return resultSet
    }

    override fun extractWithConnection(task: Task, conn: Connection): ResultSet? {
        val rs = super.extractWithConnection(task, conn)
        totalMillis += task.millis
        logExtractComplete(task)
        return rs
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeConnections()
        }
    }

    override fun toString(): String {
        return "id: $id, pid: ${parent?.id ?: 0}, pattern: $urlFilter, sql: $sqlName, children: ${children.size}"
    }

    private fun logExtractComplete(task: Task) {
        var report = logger.isInfoEnabled
        if (parent != null && task.numFitRecords == 0) {
            report = false
        }

        if (report) {
            val m = task.page.pageModel
            val modelReport = if (m != null) {
                String.format("| %d/%d/%d model fields ",
                    m.numNonBlankFields, m.numNonNullFields, m.numFields)
            } else ""
            val msg = String.format(
                "%3d. Parsed in %,dms/%4.2fs %4.2fms/p" +
                        " | %d/%d/%d fields in %d/%d records %s| %s | %s",
                task.page.id,
                task.millis, totalMillis / 1000.0, 1.0 * totalMillis / filterCount.coerceAtLeast(1),
                task.numNonBlankFields, task.numNonNullFields, task.numFields,
                task.numFitRecords, task.numRecords,
                modelReport,
                task.page.label,
                name
            )
            taskLogger.info(msg)
        }
    }
}
