package ai.platon.exotic.amazon.crawl.boot.component.common

import ai.platon.pulsar.common.*
import ai.platon.pulsar.common.PulsarParams.VAR_IS_SCRAPE
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.persist.ext.options
import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.common.sql.SQLTemplate
import ai.platon.pulsar.crawl.common.GlobalCacheFactory
import ai.platon.pulsar.crawl.parse.AbstractParseFilter
import ai.platon.pulsar.crawl.parse.FilterResult
import ai.platon.pulsar.crawl.parse.ParseResult
import ai.platon.pulsar.crawl.parse.html.OpenMapFields
import ai.platon.pulsar.crawl.parse.html.ParseContext
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.nodes.forEachElement
import ai.platon.pulsar.dom.nodes.node.ext.isAnchor
import ai.platon.pulsar.persist.HyperlinkPersistable
import ai.platon.pulsar.persist.ParseStatus
import ai.platon.pulsar.persist.ProtocolStatus
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.metadata.ParseStatusCodes
import ai.platon.pulsar.persist.model.PageModel
import ai.platon.pulsar.ql.h2.utils.ResultSetUtils
import ai.platon.pulsar.session.SessionAware
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.crawl.sql.ScrapeAPIUtils
import ai.platon.scent.parse.html.ExtractCounter
import ai.platon.scent.ql.h2.context.support.AbstractScentSQLContext
import org.h2.jdbc.JdbcSQLException
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

abstract class AbstractSQLExtractor(
    override val session: ScentSession,
    val statusTracker: ScentStatusTracker,
    val globalCacheFactory: GlobalCacheFactory,
    val conf: ImmutableConfig,
) : AbstractParseFilter(), SessionAware {

    private val logger = getLogger(AbstractSQLExtractor::class)
    private val tracer = logger.takeIf { it.isTraceEnabled }

    class Task(val page: WebPage, val document: FeaturedDocument, val sql: String) {
        val url = page.url

        var numRecords = 0
        var numFitRecords = 0
        var numNonBlankFields = 0
        var numNonNullFields = 0
        var numFields = 0

        var millis = 0L
    }
    protected var filterCount = 0

    protected val globalCache get() = globalCacheFactory.globalCache
    private val sqlContext get() = session.scentContext as AbstractScentSQLContext
    private val connectionPool get() = sqlContext.connectionPool
    private val randomConnectionOrNull
        get() = kotlin.runCatching { sqlContext.randomConnection }
                .onFailure { logger.warn(it.simplify()) }
                .getOrNull()
    private val resultSetType = ResultSet.TYPE_SCROLL_SENSITIVE
    private val resultSetConcurrency = ResultSet.CONCUR_READ_ONLY

    /**
     * The unique name of this extractor
     * */
    var name = "X$id"
    var sqlTemplate: SQLTemplate = SQLTemplate("")

    /**
     * Filters
     * */
    var urlFilter = Regex(".+")
    var onlyFetched = false
    open var minContentSize = 100_000
    open var minNumNonBlankFields = 1

    /**
     * If true, transpose the result set of the sql query. The result set must be transposable.
     * */
    val sqlName get() = sqlTemplate.display
    val persistPageModel get() = true

    /**
     * The temporary stored states, for debug purpose
     * */
    var lastTask: Task? = null
        protected set
    var lastResultSet: ResultSet? = null
        protected set
    var lastPageModel: PageModel? = null
        protected set
    var lastRelevantState: CheckState? = null
        protected set

    val regexLinkFilters = arrayOf<Regex>()

    private val closed = AtomicBoolean()
    val isActive get() = !closed.get() && AppContext.isActive

    override fun isRelevant(parseContext: ParseContext): CheckState {
        val messageWriter = statusTracker.messageWriter
        val page = parseContext.page
        val protocolStatus = page.protocolStatus

        val state = when {
            !isActive -> CheckState(10, "system down")
            page.isInternal -> CheckState(20, "internal page")
            !urlFilter.matches(page.url) -> CheckState(30, "url not match")

            page.hasVar(VAR_IS_SCRAPE) -> CheckState(40, "scraping")
            protocolStatus.isNotFound -> {
                CheckState(protocolStatus.minorCode, "not found")
            }
            page.isCanceled -> CheckState(ProtocolStatus.CANCELED, "canceled")
            !protocolStatus.isSuccess -> CheckState(protocolStatus.minorCode, protocolStatus.minorName)
            onlyFetched && !page.isFetched -> {
                // this extractor handles pages only just fetched by default
                // TODO: what about cached?
                messageWriter.reportLoadedIrrelevantPages(page)
                CheckState(60, "loaded")
            }
            page.content == null -> {
                CheckState(65, "null content")
            }
            page.contentLength < minContentSize -> {
                messageWriter.reportTooSmallPages(page)
                CheckState(70, "small content")
            }
            page.options.isDead() -> {
                CheckState(80, "dead")
            }
            !parseContext.parseResult.isParsed -> {
                logger.warn("Page is be parsed | {}", page.url)
                CheckState(90, "not parsed")
            }
            !parseContext.parseResult.isSuccess -> {
                logger.warn("Page is not parsed correctly | {}", page.url)
                CheckState(92, "parse failure")
            }
            parseContext.document == null -> {
                logger.warn("Document is null | {}", page.url)
                CheckState(93, "null document")
            }
            else -> CheckState()
        }

        return state
    }

    override fun doFilter(parseContext: ParseContext): FilterResult {
        ++filterCount
        val page = parseContext.page

        if (!parseContext.parseResult.isParsed) {
            logger.warn("Page is be parsed | {}", page.url)
            return FilterResult.failed(ParseStatusCodes.FAILED_EXCEPTION, "Not parsed")
        }

        return try {
            val document = parseContext.parseResult.document
            if (document == null) {
                logger.warn("Document is null, page might not be parsed | {}", page.url)
                return FilterResult.failed(ParseStatusCodes.FAILED_EXCEPTION, "Not parsed")
            }

            parseContext.parseResult.document = document
            parseContext.parseResult.majorCode = ParseStatus.SUCCESS

            onBeforeFilter(page, document)
            extract(page, document)
            onAfterFilter(page, document, parseContext.parseResult)

            return FilterResult(ParseStatusCodes.SUCCESS)
        } catch (e: IllegalApplicationStateException) {
            AppContext.beginTermination()
            logger.warn("Illegal context state | {}", e.message)
            FilterResult.failed(e)
        }
    }

    open fun onBeforeFilter(page: WebPage, document: FeaturedDocument) {
    }

    open fun onAfterFilter(page: WebPage, document: FeaturedDocument, parseResult: ParseResult) {
        var order = 0
        document.document.forEachElement {
            if (it.isAnchor) {
                val href = it.attr("abs:href")
                if (regexLinkFilters.any { it.matches(href) }) {
                    ++order
                    val hyperLink = HyperlinkPersistable(href, it.extension.immutableText, order)
                    parseResult.hypeLinks.add(hyperLink)
                }
            }
        }
    }

    open fun onBeforeExtract(page: WebPage, document: FeaturedDocument) {
    }

    open fun onAfterExtract(page: WebPage, document: FeaturedDocument, rs: ResultSet?): ResultSet? {
        return rs
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeConnections()
        }
    }

    protected open fun extract(page: WebPage, document: FeaturedDocument): ResultSet? {
        if (!isActive) {
            return null
        }

        var result: ResultSet? = null
        val millis = measureTimeMillis {
            takeIf { AppContext.isActive } ?: return null

            val conn = connectionPool.poll() ?: randomConnectionOrNull ?: return null

            try {
                onBeforeExtract(page, document)

                result = extractWithConnection(page, document, conn)

                onAfterExtract(page, document, result)
            } catch (e: JdbcSQLException) {
                logger.warn("JDBC exception", e)
            } catch (e: SQLException) {
                logger.error("Extract failed", e)
            } finally {
                if (!conn.isClosed) {
                    connectionPool.add(conn)
                }
            }
        }

        takeIf { logger.isTraceEnabled }?.traceExtractResult(millis, page)

        return result
    }

    protected open fun extractWithConnection(page: WebPage, document: FeaturedDocument, conn: Connection): ResultSet? {
        val sql = sqlTemplate.createInstance(page.url)
        // TODO: use a regex to validate sql format
        if (!sql.sql.contains("select", ignoreCase = true)) {
            logger.warn("Illegal sql format: >>>{}<<<", sql)
            return null
        }

        val task = Task(page, document, sql.sql)
        lastTask = task
        return extractWithConnection(task, conn)
    }

    protected open fun extractWithConnection(task: Task, conn: Connection): ResultSet? {
        try {
            val startTime = System.currentTimeMillis()
            val rs = executeQuery0(task, conn)
            task.millis = System.currentTimeMillis() - startTime
            return rs
        } catch (e: JdbcSQLException) {
            logger.warn("Unexpected jdbc sql exception", e)
        }

        return null
    }

    protected open fun copyResultSetIfQualified(task: Task, sourceRs: ResultSet): ResultSet? {
        stat(sourceRs, task)

        if (task.numFitRecords == 0) {
            return null
        }

        sourceRs.beforeFirst()
        val simpleRs = ResultSetUtils.copyResultSet(sourceRs)

        if (persistPageModel) {
            resultSet2PageModel(simpleRs, task.page)
        }

        if (logger.isDebugEnabled) {
            sourceRs.beforeFirst()
            val formattedString = ResultSetFormatter(sourceRs, asList = true).toString()
            statusTracker.messageWriter.reportExtractListResult(formattedString)
        }

        return simpleRs
    }

    protected open fun checkFieldRequirement(url: String, page: WebPage, onlyRecordRs: ResultSet) {

    }

    protected open fun resultSet2PageModel(rs: ResultSet, page: WebPage) {
        val fields = OpenMapFields()
        val columnCount = rs.metaData.columnCount

        rs.beforeFirst()
        while (rs.next()) {
            IntRange(1, columnCount).forEach { j ->
                val name = rs.metaData.getColumnName(j)
                val value = rs.getString(j)
                fields[name] = value
            }
        }
        rs.beforeFirst()

        page.ensurePageModel().emplace(1, 0, "sql", fields.map)

        lastPageModel = page.ensurePageModel().deepCopy()
    }

    protected open fun exportToCsv(rs: ResultSet, buffer: StringBuilder) {
        rs.beforeFirst()
        buffer.setLength(0)
        ResultSetFormatter(rs, withHeader = false, buffer = buffer)
        if (buffer.lastOrNull() == '\n') buffer.deleteCharAt(buffer.length - 1)
        statusTracker.messageWriter.reportExtractCsvResult(buffer.toString())
    }

    protected fun closeConnections() {
        connectionPool.forEach { it.runCatching { it.close() }.onFailure { logger.warn(it.simplify()) } }
        connectionPool.clear()
        super.close()
    }

    private fun executeQuery0(task: Task, conn: Connection): ResultSet? {
        val url = task.url

        var result: ResultSet? = null

        if (conn.isClosed) {
            return null
        }

        conn.createStatement(resultSetType, resultSetConcurrency).run {
            globalCache.putPDCache(task.page, task.document)
            val normSql = ScrapeAPIUtils.normalizeSQL(task.sql, "-parse", addArgs = "-readonly")

            executeQuery(normSql.sql)?.use { rs ->
                result = copyResultSetIfQualified(task, rs)
            }
        }

        lastResultSet = result

        return result
    }

    private fun stat(sourceRs: ResultSet, task: Task) {
        val columnCount = sourceRs.metaData.columnCount
        sourceRs.beforeFirst()
        while (sourceRs.next()) {
            ++task.numRecords
            var numNonBlankFields = 0
            IntRange(1, columnCount).forEach { k ->
                val value = sourceRs.getString(k)
                ++task.numFields
                if (value != null) {
                    ++task.numNonNullFields
                }
                if (!value.isNullOrBlank() && value != "()") {
                    ++numNonBlankFields
                    ++task.numNonBlankFields
                }
            }

            if (numNonBlankFields < minNumNonBlankFields) {
                // The record is not qualified
                statusTracker.metrics.inc(ExtractCounter.xFewFields)
                checkFieldRequirement(task.url, task.page, sourceRs)
            } else {
                ++task.numFitRecords
            }
        }
    }

    private fun traceExtractResult(millis: Long, page: WebPage) {
        if (logger.isTraceEnabled && millis > 10_000) {
            val m = page.pageModel ?: return
            tracer?.trace(
                "It takes {} to parse {}/{}/{} fields | {}", Duration.ofMillis(millis).readable(),
                m.numNonBlankFields, m.numNonNullFields, m.numFields, page.url
            )
        }
    }

    protected fun collectNullFields(onlyRecordRs: ResultSet): List<String> {
        val numColumns = onlyRecordRs.metaData.columnCount
        onlyRecordRs.beforeFirst()
        return if (onlyRecordRs.next()) {
            IntRange(1, numColumns).mapNotNull { col ->
                onlyRecordRs.getObject(col)
                onlyRecordRs.takeIf { it.wasNull() }?.metaData?.getColumnName(col)
            }
        } else listOf()
    }
}
