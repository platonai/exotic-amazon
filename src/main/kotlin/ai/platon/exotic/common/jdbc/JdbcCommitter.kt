package ai.platon.exotic.common.jdbc

import ai.platon.pulsar.common.brief
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.stringify
import ai.platon.pulsar.ql.h2.utils.JdbcUtils
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

data class JdbcConfig(
    var driver: String,
    var url: String,
    var username: String,
    var password: String,
) {
    override fun toString(): String {
        return "$url $username $password"
    }
}

/**
 * Jdbc Sink Sql Extractor Config
 * */
data class JdbcCommitConfig(
    val jdbcConfig: JdbcConfig,
    val tableName: String = "",
    val name: String = "",
    var syncBatchSize: Int = 60,
    var minNumNonBlankFields: Int = 1,
) {
    override fun toString(): String {
        return "$jdbcConfig $tableName $name"
    }
}

open class JdbcCommitter(
    val jdbcConfig: JdbcConfig,
    val tableName: String,
) : AutoCloseable {
    companion object {
        @Volatile
        var commitCount = 0
    }

    private val logger = getLogger(JdbcCommitter::class)

    private val sinkConnectionPool = ConcurrentLinkedQueue<Connection>()

    @Volatile
    var lastCommitTime = Instant.EPOCH
    var dryRunSQLs: Boolean = false

    fun commit(rs: ResultSet) {
        var sinkConnection: Connection? = null

        try {
            sinkConnection = sinkConnectionPool.poll()?.takeUnless { it.isClosed } ?: createSinkConnection()
            commitWithConnection(rs, sinkConnection)
        } catch (e: Exception) {
            logger.warn(e.stringify())
        } finally {
            sinkConnection?.let { sinkConnectionPool.add(it) }
        }
    }

    private fun commitWithConnection(rs: ResultSet, connection: Connection): Int {
        var affectedRows = 0

        try {
            affectedRows += JdbcUtils.executeInsert(rs, connection, tableName, dryRun = dryRunSQLs)
        } catch (e: SQLException) {
            logger.warn(
                "{}. Failed to insert record to {}, check sql log for detail | {}",
                commitCount, tableName, e.brief()
            )
        }

        logger.info("{}. Committed to {}, {} rows affected", commitCount, tableName, affectedRows)

        return affectedRows
    }

    override fun close() {
        sinkConnectionPool.forEach { it.runCatching { it.close() }.onFailure { logger.warn(it.message) } }
        sinkConnectionPool.clear()
    }

    @Throws(SQLException::class)
    private fun createSinkConnection(): Connection {
        Class.forName(jdbcConfig.driver)
        val connection = DriverManager.getConnection(jdbcConfig.url, jdbcConfig.username, jdbcConfig.password)
        logger.info("JDBC sink has been connected | {}", connection.clientInfo)
        return connection
    }
}
