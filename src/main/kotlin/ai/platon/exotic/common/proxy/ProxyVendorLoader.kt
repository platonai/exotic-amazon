package ai.platon.exotic.common.proxy

import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.proxy.ProxyEntry
import ai.platon.pulsar.common.proxy.ProxyException
import ai.platon.pulsar.common.proxy.ProxyLoader
import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.exotic.common.proxy.vendor.ProxyVendorFactory
import ai.platon.pulsar.common.*
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

/**
 * Load proxies from proxy vendors
 */
class ProxyVendorLoader(conf: ImmutableConfig): ProxyLoader(conf) {

    private val log = LoggerFactory.getLogger(ProxyVendorLoader::class.java)
    private val providers = mutableSetOf<String>()
    private val minimumReadyProxies = 1
    private val minimumFetchInterval = Duration.ofSeconds(3)
    private var numLoadedProxies = 0
    @Volatile
    private var lastFetchTime = Instant.EPOCH
    private val bannedItemsLoaded = AtomicBoolean()

    @Throws(ProxyException::class)
    override fun updateProxies(reloadInterval: Duration): List<ProxyEntry> {
        if (bannedItemsLoaded.compareAndSet(false, true)) {
            loadBannedItems()
        }
        return updateProxies0(reloadInterval)
    }

    @Throws(IOException::class)
    private fun updateProxies0(reloadInterval: Duration): List<ProxyEntry> {
        return loadEnabledProxies(reloadInterval).takeIf { it.isNotEmpty() } ?: fetchProxiesFromProviders(reloadInterval)
    }

    fun fetchProxiesFromProviders(reloadInterval: Duration): List<ProxyEntry> {
        return loadEnabledProviders(reloadInterval).toCollection(providers)
            .flatMap { fetchProxiesFromProvider(it) }
    }

    fun fetchProxiesFromProvider(providerUrl: String): List<ProxyEntry> {
        val space = StringUtils.SPACE
        val url = providerUrl.substringBefore(space)
        val metadata = providerUrl.substringAfter(space)
        var vendor = "none"
        var format = "txt"

        metadata.split(space).zipWithNext().forEach {
            when (it.first) {
                "-vendor" -> vendor = it.second
                "-fmt" -> format = it.second
            }
        }

        if (vendor == "mock") {
            val path = AppPaths.AVAILABLE_PROXY_DIR.resolve("mock_proxy.json")
            return parseQualifiedProxies(path, vendor, format)
        }

        if (Duration.between(lastFetchTime, Instant.now()) < minimumFetchInterval) {
            sleepSeconds(minimumFetchInterval.seconds)
        }
        lastFetchTime = Instant.now()

        return kotlin.runCatching { fetchProxiesFromProvider(URL(url), vendor, format) }
            .onFailure { log.warn(it.simplify()) }
            .getOrNull() ?: listOf()
    }

    private fun loadEnabledProxies(expires: Duration): List<ProxyEntry> {
        if (alwaysTrue()) {
            // TODO: not tested
            return listOf()
        }

        return Files.list(AppPaths.ENABLED_PROXY_DIR).filter { Files.isRegularFile(it) }.map { Files.readAllLines(it) }
            .toList().flatMap { it.mapNotNull { ProxyEntry.parse(it) } }
    }

    private fun loadBannedItems() {
        try {
            loadBannedItemsIfNotExpiredTo(AppPaths.PROXY_BANNED_HOSTS_FILE, ipTimeToBan, bannedSegments)
            loadBannedItemsIfNotExpiredTo(AppPaths.PROXY_BANNED_SEGMENTS_FILE, segmentTimeToBan, bannedIps)
        } catch (e: IOException) {
            log.warn(e.toString())
        }

        bannedIps.takeIf { it.isNotEmpty() }?.joinToString()
            ?.let { StringUtils.abbreviateMiddle(it, "...", 200) }
            ?.also { log.info("There are {} banned hosts: \n{}", bannedIps.size, it) }
        bannedSegments.takeIf { it.isNotEmpty() }?.joinToString()
            ?.let { StringUtils.abbreviateMiddle(it, "...", 200) }
            ?.also { log.info("There are {} banned segments: \n{}", bannedSegments.size, it) }
    }

    /**
     * Load banned items to the destination if they are still banned
     * TODO: a bug here so the banned time is not reduced
     * */
    @Throws(IOException::class)
    private fun loadBannedItemsIfNotExpiredTo(path: Path, timeToBan: Duration, destination: MutableCollection<String>) {
        if (Files.exists(path)) {
            val lastModified = Files.getLastModifiedTime(path).toInstant()
            if (Duration.between(lastModified, Instant.now()) < timeToBan) {
                Files.readAllLines(path).toCollection(destination)
            }
        }
    }

    @Synchronized
    @Throws(SocketException::class)
    fun fetchProxiesFromProvider(providerURL: URL, vendor: String = "none", format: String = "txt"): List<ProxyEntry> {
        val filename = "proxies." + AppPaths.fromUri(providerURL.toString()) + "." + vendor + "." + format
        val target = AppPaths.PROXY_ARCHIVE_DIR.resolve(filename)

        if (!isActive) {
            return listOf()
        }

        log.info("Fetching proxies from provider | {}", providerURL)

        Files.deleteIfExists(target)
        FileUtils.copyURLToFile(providerURL, target.toFile())

        return parseQualifiedProxies(target, vendor, format)
    }

    fun loadEnabledProviders(reloadInterval: Duration): List<String> {
        return Files.list(AppPaths.ENABLED_PROVIDER_DIR).filter { Files.isRegularFile(it) }.toList().flatMap {
            loadIfModified(it, reloadInterval) { loadProviders(it) }
        }
    }

    fun loadProviders(path: Path): List<String> {
        return Files.readAllLines(path).map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .distinct().shuffled().filter { UrlUtils.isValidUrl(it) }
    }

    fun parseQualifiedProxies(path: Path, vendor: String = "none", format: String = "txt"): List<ProxyEntry> {
        if (vendor == "mock") {
            return ProxyVendorFactory.getProxyParser(vendor).parse(path, format)
        }

        log.info("Testing proxies, vendor: $vendor, format: $format | file://$path")
        val count = AtomicInteger()
        val proxies = ProxyVendorFactory.getProxyParser(vendor).parse(path, format)
            .filterNot { it.willExpireAfter(minimumProxyTTL) }
            .filterNot { it.outSegment in bannedSegments }
            .filterNot { it.outIp in bannedIps }
            .shuffled().chunked(minimumReadyProxies).flatMap {
                it.parallelStream().filter { isActive && count.get() < minimumReadyProxies && test(it) }
                    .map { it.also { log.info("Test passed: ${it.display}"); count.incrementAndGet() } }
                    .toList()
            }

        numLoadedProxies += proxies.size
        log.info("Loaded {}/{} proxies", proxies.size, numLoadedProxies)

        return proxies
    }

    private fun test(proxyEntry: ProxyEntry): Boolean {
        return proxyEntry.takeIf { testProxyBeforeUse }?.test(URL(testUrl))?:true
    }
}
