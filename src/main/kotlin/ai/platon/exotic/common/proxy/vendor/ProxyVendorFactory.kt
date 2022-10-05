package ai.platon.exotic.common.proxy.vendor

import ai.platon.exotic.common.proxy.vendor.apeyun.APEYunProxyParser
import ai.platon.exotic.common.proxy.vendor.dailiyun.DaiLiYunProxyParser
import ai.platon.exotic.common.proxy.vendor.mock.MockProxyParser
import ai.platon.exotic.common.proxy.vendor.zm.ZMProxyParser
import ai.platon.pulsar.common.proxy.ProxyEntry
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

abstract class ProxyParser {
    companion object {
        const val IPADDRESS_PATTERN = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
    }

    val logger = LoggerFactory.getLogger(ProxyParser::class.java)
    abstract fun parse(text: String, format: String): List<ProxyEntry>
    open fun parse(path: Path, format: String): List<ProxyEntry> = parse(Files.readString(path), format)
}

class DefaultProxyParser: ProxyParser() {
    override fun parse(text: String, format: String): List<ProxyEntry> {
        return text.split("\n").mapNotNull { ProxyEntry.parse(text) }
    }
}

object ProxyVendorFactory {
    fun getProxyParser(vendor: String): ProxyParser {
        return when (vendor) {
            "zm" -> ZMProxyParser()
            "apeyun" -> APEYunProxyParser()
            "dailiyun" -> DaiLiYunProxyParser()
            "mock" -> MockProxyParser()
            else -> DefaultProxyParser()
        }
    }
}
