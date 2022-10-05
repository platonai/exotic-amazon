package ai.platon.exotic.common.proxy.vendor.dailiyun

import ai.platon.pulsar.common.proxy.ProxyEntry
import ai.platon.pulsar.common.proxy.ProxyVendorException
import ai.platon.exotic.common.proxy.vendor.ProxyParser
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
 * https://www.dailiyun.com/
 * */
class DaiLiYunProxyParser: ProxyParser() {
    override fun parse(text: String, format: String): List<ProxyEntry> {
        return doParse(text, format)
    }

    private fun doParse(text: String, format: String): List<ProxyEntry> {
        // 代理服务器地址,外网ip，位置描述，入库时间（unix）,过期时间(unix)
        // "202.98.0.68:19527,202.98.0.68,中国-吉林省-吉林市-电信,1512961581,1512962271"
        try {
            val text0 = text.trim()
            val parts = text0.split(",")

            if (parts.size == 5 && parts[0].contains(":")) {
                val (ip, port) = parts[0].split(":")
                val outIp = parts[1]
                val ttl = Instant.ofEpochSecond(parts[4].toLong())
                val proxy = ProxyEntry(ip, port.toInt(), outIp, declaredTTL = ttl)

//                println("<pre>${parts[4]}</pre>")
//                println("<pre>$ttl</pre>")
//                println("<pre>$proxy</pre>")

                return listOf(proxy)
            } else {
                throw ProxyVendorException("Illegal proxy format - <pre>$text</pre>")
            }
        } catch (t: Throwable) {
            throw ProxyVendorException("Failed to parse proxy - <pre>$text</pre>", t)
        }

        return listOf()
    }
}
