package ai.platon.exotic.common.proxy.vendor.apeyun

import ai.platon.pulsar.common.proxy.ProxyEntry
import ai.platon.pulsar.common.proxy.ProxyVendorException
import ai.platon.exotic.common.proxy.vendor.ProxyParser
import com.google.gson.GsonBuilder
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
class APEYunProxyParser: ProxyParser() {
    private val gson = GsonBuilder().create()

    override fun parse(text: String, format: String): List<ProxyEntry> {
        return doParse(text, format)
    }

    private fun doParse(text: String, format: String): List<ProxyEntry> {
        val ttl = Instant.now().plus(Duration.ofHours(24))
        if (format == "json") {
            val result = gson.fromJson(text, ProxyResult::class.java)
            if (result.code == 200) {
                return result.data.map { ProxyEntry(it.ip, it.port, it.origin_ip, declaredTTL = ttl) }
            }
            if (result.code != 200) {
                throw ProxyVendorException("Proxy vendor exception - $text")
            }
        }
        return listOf()
    }
}
