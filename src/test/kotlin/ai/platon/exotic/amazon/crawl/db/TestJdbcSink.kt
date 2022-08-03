package ai.platon.exotic.amazon.crawl.db

import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.exotic.amazon.crawl.TestBase
import ai.platon.scent.crawl.serialize.config.v1.JdbcConfig
import ai.platon.scent.jackson.scentObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import org.slf4j.LoggerFactory
import org.springframework.test.context.ActiveProfiles
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore("Make sure JDBC sink is available")
class TestJdbcSink: TestBase() {
    private val logger = LoggerFactory.getLogger(TestJdbcSink::class.java)

    private val jdbcResource = "config/jdbc-sink-config.json"

    private val jdbcConfig = scentObjectMapper()
        .readValue<JdbcConfig>(ResourceLoader.readString(jdbcResource))

    private val conn = createSinkConnection()

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `When check best seller' task_time then it's start of day`() {
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery("SELECT id, create_time, task_time, title from asin_best_sellers_sync order by id desc limit 10;")
        while (rs.next()) {
            val text = rs.getString("task_time")
            val taskTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(DateTimes.zoneId)
                .parse(text) { LocalDateTime.from(it) }
            assertEquals(0, taskTime.hour, text)
            assertEquals(0, taskTime.minute, text)
            assertEquals(0, taskTime.second, text)
        }

        stmt.closeOnCompletion()
    }

    @Test
    fun `When check asin' jsVariables then it exists`() {
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery("SELECT id, jsVariables, createtime from `asin_sync_utf8mb4` order by id desc limit 10;")
        var count = 0
        while (rs.next()) {
            val text = rs.getString("jsVariables")
            // assertTrue { text.isNullOrBlank() }
            if (text.isNullOrBlank()) {
                ++count
            }
            // println(text)
        }
        assertTrue("The field jsVariables should exists") { count > 0 }

        stmt.closeOnCompletion()
    }

    @Throws(SQLException::class)
    private fun createSinkConnection(): Connection {
        Class.forName(jdbcConfig.driver)
        return DriverManager.getConnection(jdbcConfig.url, jdbcConfig.username, jdbcConfig.password)
    }
}
