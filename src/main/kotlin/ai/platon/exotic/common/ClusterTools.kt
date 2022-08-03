package ai.platon.exotic.common

import ai.platon.pulsar.common.AppContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

object ClusterTools {
    private val logger = LoggerFactory.getLogger(ClusterTools::class.java)

    /**
     * TODO: load from config
     * */
    val REGISTERED_CRAWLERS =
        when {
            "crawl" in AppContext.HOST_NAME -> mapOf(
                "crawl0" to "42.194.239.233",
                "crawl1" to "42.194.237.104",
                "crawl2" to "42.194.241.96",
                "crawl3" to "42.194.242.91"
            )
            else -> {
                mapOf("localhost" to "127.0.0.1")
            }
        }

    val REGISTERED_CRAWLER_HOST_NAMES = REGISTERED_CRAWLERS.keys
    val REGISTERED_CRAWLER_IPS = REGISTERED_CRAWLERS.values

    val PRODUCT_LABEL_PATH = Paths.get(AppContext.USER_DIR).resolve(".PRODUCT")
    val TEST_LABEL_PATH = Paths.get(AppContext.USER_DIR).resolve(".TEST")

    val hostName = AppContext.HOST_NAME
    val crawlerCount = REGISTERED_CRAWLER_HOST_NAMES.size
    val instancePartition = REGISTERED_CRAWLER_HOST_NAMES.indexOf(hostName).coerceAtLeast(0)
    val devInstanceLimit = 100

    fun isCluster(): Boolean {
        return REGISTERED_CRAWLER_HOST_NAMES.size > 1
    }

    fun isDevInstance(): Boolean {
        val env = System.getenv("env")?.lowercase()
        return env == null || env == "dev"
    }

    fun isTestInstance(): Boolean {
        return Files.exists(TEST_LABEL_PATH) || isDevInstance()
    }

    fun getTestLabel(): String? {
        if (Files.exists(TEST_LABEL_PATH)) {
            return Files.readAllLines(TEST_LABEL_PATH).firstOrNull()
        }

        return null
    }

    fun isSingleInstanceMode(): Boolean {
        return AppContext.HOST_NAME !in REGISTERED_CRAWLER_HOST_NAMES
    }

    fun <T> partition(
        items: Collection<T>,
        partition: Int = instancePartition,
        numPartitions: Int = crawlerCount,
    ): Collection<T> {
        return when {
            isDevInstance() -> items.take(devInstanceLimit)
            isSingleInstanceMode() -> items
            else -> partitionTo(items, mutableListOf())
        }
    }

    fun <T, C : MutableCollection<T>> partitionTo(
        items: Collection<T>, destination: C,
        partition: Int = instancePartition,
        numPartitions: Int = crawlerCount,
    ): C {
        var start = 0
        val limit: Int

        when {
            isDevInstance() -> {
                limit = devInstanceLimit
            }
            isSingleInstanceMode() -> {
                limit = items.size
            }
            else -> {
                val partitionSize = items.size / numPartitions
                start = partition * partitionSize
                limit = if (partition == numPartitions - 1) items.size - partitionSize * partition else partitionSize
            }
        }

        logger.info("Partition items from {} to {} on host <{}>", start, start + limit, hostName)
        return items.asSequence().drop(start).take(limit).toCollection(destination)
    }
}
