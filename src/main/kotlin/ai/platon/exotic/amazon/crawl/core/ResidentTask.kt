package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.Priority13
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.common.options.LoadOptionDefaults
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.scent.ScentSession
import ai.platon.scent.common.ClusterTools
import ai.platon.scent.mongo.v1.NaiveResidentTask
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ResidentTask(
    val id: Int,
    val name: String,
    val label: String,
    val priority: Priority13,
    val taskPeriod: Duration,
    val expires: Duration,
    val deadTime: () -> Instant,
    val startTime: () -> Instant,
    val endTime: () -> Instant,
    val fileName: String? = null,
    var ignoreTTL: Boolean = false,
    var refresh: Boolean = false,
    var storeContent: Boolean = false
) {
    var zoneId = DateTimes.zoneId
    /**
     * The path of file with file name is [fileName] if exist
     * */
    var path: Path? = null
}

fun ResidentTask.expireAt(): Instant {
    return when {
        taskPeriod.toHours() == 1L -> Instant.now().truncatedTo(ChronoUnit.HOURS)
        taskPeriod.toDays() == 1L -> startTime()
        else -> LoadOptionDefaults.expireAt
    }
}

/**
 * All single fetch tasks are dead, do not serve it anymore, do not fetch, do not retry, and do not sync to sink
 *
 * TODO: distinguish deadTime and endTime
 * */
fun ResidentTask.isDead(): Boolean {
    if (ignoreTTL) {
        return false
    }

    return Instant.now() > deadTime()
}

fun ResidentTask.isRunTime(): Boolean {
    if (ignoreTTL) {
        return true
    }

    val now = Instant.now()
    return now >= startTime() && now < endTime()
}

fun ResidentTask.localStartTime(): LocalDateTime {
    return startTime().atOffset(DateTimes.zoneOffset).toLocalDateTime()
}

fun ResidentTask.createOptions(taskId: String, taskTime: Instant, session: ScentSession): LoadOptions {
    return session.options().also { initOptions(it, taskId, taskTime) }
}

fun ResidentTask.createOptions(taskId: String, taskTime: Instant): LoadOptions {
    return LoadOptions.create(VolatileConfig.UNSAFE).also { initOptions(it, taskId, taskTime) }
}

fun ResidentTask.initOptions(options: LoadOptions, taskId: String, taskTime: Instant): LoadOptions {
    options.parse = true
    options.expires = expires
    options.expireAt = expireAt()
    options.taskId = taskId
    options.taskTime = taskTime
    options.label = label
    options.ignoreFailure = true
    options.refresh = refresh
    options.storeContent = storeContent

    return options
}

fun ResidentTask.isSupervised(): Boolean {
    val mod = id % ClusterTools.crawlerCount
    return ClusterTools.instancePartition == mod
}

class CollectedResidentTask(
    val task: ResidentTask,
    val hyperlinks: Set<Hyperlink>
)
