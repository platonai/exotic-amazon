package ai.platon.exotic.common.proxy.vendor.mock

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.proxy.ProxyEntry
import ai.platon.exotic.common.proxy.vendor.ProxyParser
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

private class ProxyItem(
        val origin_ip: String = "",
        val ip: String = "",
        val port: Int = 0
)

private class ProxyResult(
        val code: Int = 200,
        val msg: String = "success",
        val data: List<ProxyItem> = listOf()
)

/**
 * https://www.apeyun.com/
 * */
class MockProxyParser: ProxyParser() {
    private val gson = GsonBuilder().create()
    private val ttl = Instant.now().plus(Duration.ofHours(24))
    private val defaultMockJson = "mock_proxy.json"

    override fun parse(text: String, format: String): List<ProxyEntry> {
        if (format == "json") {
            val result = gson.fromJson(text, ProxyResult::class.java)
            return result.data.map { ProxyEntry(it.ip, it.port, it.origin_ip, declaredTTL = ttl) }
        }
        return listOf()
    }

    override fun parse(path: Path, format: String): List<ProxyEntry> {
        val text = if (Files.exists(path)) Files.readString(path) else ResourceLoader.readString(defaultMockJson)
        return parse(text, format)
    }
}
