package ai.platon.exotic.amazon.crawl.boot

import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.exotic.amazon.crawl.boot.component.AmazonGenerator
import ai.platon.exotic.amazon.crawl.core.PredefinedTask
import ai.platon.exotic.amazon.crawl.core.isRunTime
import ai.platon.exotic.amazon.crawl.core.toResidentTask
import ai.platon.exotic.amazon.crawl.generate.MonthlyBasisAsinGenerator
import ai.platon.pulsar.common.CheckState
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.crawl.CrawlLoops
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.scent.boot.autoconfigure.component.ScentCrawlLoop
import ai.platon.scent.common.AMAZON_CRAWLER_GENERATE_DEFAULT_TASKS
import ai.platon.scent.common.MINUTE_TO_MILLIS
import ai.platon.scent.common.SECOND_TO_MILLIS
import ai.platon.scent.common.ScentStatusTracker
import ai.platon.scent.rest.api.service.v1.ScrapeServiceV1
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "amazon", name = ["enable.scheduler"], havingValue = "true")
class CrawlScheduler(
    private val crawler: AmazonCrawler,
    private val amazonGenerator: AmazonGenerator,
    private val scrapeServiceV1: ScrapeServiceV1,
    private val scentStatusTracker: ScentStatusTracker,
    private val crawlLoop: ScentCrawlLoop,
    private val crawlLoops: CrawlLoops,
    private val parseFilters: ParseFilters,
    private val conf: ImmutableConfig
) {
    companion object {
        const val INITIAL_DELAY = 3 * MINUTE_TO_MILLIS
    }

    private val logger = LoggerFactory.getLogger(CrawlScheduler::class.java)
    private var commitCount = 0
    private val generateDefaultTasks get() = conf.getBoolean(AMAZON_CRAWLER_GENERATE_DEFAULT_TASKS, true)

    /**
     * Report periodically
     * */
    @Scheduled(initialDelay = 30 * SECOND_TO_MILLIS, fixedDelay = 10 * SECOND_TO_MILLIS)
    fun report() {
        // logger.info("========================= crawl scheduler is enabled")
    }

    /**
     * cron syntax:
     * 1. second, minute, hour, day of month, month, day(s) of week
     * 2. (*) means match any
     * 3. (*)/X means "every X"
     * 4. ? ("no specific value")
     *
     * @see (Cron Expression Generator & Explainer)[https://www.freeformatter.com/cron-expression-generator-quartz.html]
     * "0 0 9 ? * *" = At 09:00:00am every day
     * */
    @Scheduled(cron = "0 0 9 ? * *")
    fun runDailyTasksAt09h00m() {
        if (!generateDefaultTasks) {
            return
        }

        amazonGenerator.generateLoadingTasksAtTimePoint(ChronoUnit.MINUTES)
    }

    /**
     * Refresh daily crawl tasks at 00:00:10am every day, remove old collectors and create new ones,
     * we choose the 00:00:10am instead of 00:00:00am to ensure the clean procedure is done
     *
     * cron syntax:
     * 1. second, minute, hour, day of month, month, day(s) of week
     * 2. (*) means match any
     * 3. (*)/X means "every X"
     * 4. ? ("no specific value")
     *
     * @see (Cron Expression Generator & Explainer)[https://www.freeformatter.com/cron-expression-generator-quartz.html]
     * "10 0 0 ? * *" = At 00:00:10am every day
     * */
    @Scheduled(cron = "10 0 0 ? * *")
    fun runDailyTasksAt00h00m10s() {
        if (!generateDefaultTasks) {
            return
        }

        amazonGenerator.generateLoadingTasksAtTimePoint(ChronoUnit.MINUTES)
    }

    /**
     * @see (Cron Expression Generator & Explainer)[https://www.freeformatter.com/cron-expression-generator-quartz.html]
     * "0 0 14 ? * *" = At 14:00:00pm every day
     * */
    @Scheduled(cron = "0 0 14 ? * *")
    fun runDailyTasksAt14h00m() {
        if (!generateDefaultTasks) {
            return
        }

        amazonGenerator.generateAsinTasks()
    }

    /**
     * Run hourly crawl tasks
     *
     * cron syntax:
     * 1. second, minute, hour, day of month, month, day(s) of week
     * 2. (*) means match any
     * 3. (*)/X means "every X"
     * 4. ? ("no specific value")
     *
     * @see (Cron Expression Generator & Explainer)[https://www.freeformatter.com/cron-expression-generator-quartz.html]
     * "0 0 * ? * *" = At second :00 of minute :00 of every hour
     * */
    @Scheduled(cron = "0 0 * ? * *")
    fun runHourlyCrawlTasks() {
        if (!generateDefaultTasks) {
            return
        }

        val tasks = PredefinedTask.values()
            .filter { it.taskPeriod == Duration.ofHours(1) }
            .map { it.toResidentTask() }

        logger.info("Run hourly crawl tasks ...")
        amazonGenerator.generateLoadingTasks(tasks, true)
    }

    @Scheduled(initialDelay = 2 * INITIAL_DELAY, fixedDelay = 2 * MINUTE_TO_MILLIS)
    fun createAsinTaskIfOldOneFinishedOrNoRunningTask() {
        logger.info("Checking asin task ...")
        val state = createAsinTaskIfOldOneFinishedOrNoRunningTask0()
        logger.info("Asin task generation, state: {} message: {}", state.code, state.message)
    }

    /**
     * Check and remove retried cache collectors at midnight
     * */
    @Scheduled(cron = "0 0 0 ? * *")
    fun removeRetiredTasksAt00h00m() {
        if (!generateDefaultTasks) {
            return
        }

        removeRetiredUrlCacheCollectors()
    }

    private fun createAsinTaskIfOldOneFinishedOrNoRunningTask0(): CheckState {
        if (!generateDefaultTasks) {
            return CheckState(100, "Not enabled")
        }

        val task = PredefinedTask.ASIN.toResidentTask()
        if (!task.isRunTime()) {
            return CheckState(95, "Not runtime")
        }

        val collectors = crawlLoop.urlFeeder.openCollectors
        val reviewCount = collectors.asSequence()
            .filter { PredefinedTask.REVIEW.name in it.name }
            .sumOf { it.externalSize }
        if (reviewCount > 0) {
            // run review tasks
            return CheckState(90, "Still review tasks")
        }

        // The last asin collector is finished, but it's still the asin task's time
        val asinCount = collectors.asSequence()
            .filter { task.name in it.name }
            .sumOf { it.externalSize }

        if (asinCount in 1..MonthlyBasisAsinGenerator.minAsinTasks && task.isRunTime()) {
            logger.info("Too few asins, generating asin tasks ...")
            amazonGenerator.generateAsinTasks()
            return CheckState(0, "Few asins")
        }

        // No any running task, generate asin tasks
        if (collectors.sumOf { it.externalSize } > 0) {
            return CheckState(80, "Still other tasks")
        }

        amazonGenerator.generateAsinTasks()
        return CheckState(0, "No any task")
    }

    /**
     * Clear asin tasks at 23:30:00pm, so review tasks have chance to run
     *
     * cron syntax:
     * 1. second, minute, hour, day of month, month, day(s) of week
     * 2. (*) means match any
     * 3. (*)/X means "every X"
     * 4. ? ("no specific value")
     *
     * @see (Cron Expression Generator & Explainer)[https://www.freeformatter.com/cron-expression-generator-quartz.html]
     * "0 30 23 ? * *" = At 23:30:00pm every day
     * */
    @Scheduled(cron = "0 30 23 ? * *")
    fun clearAsinTasksAt23h30m00s() {
        if (!generateDefaultTasks) {
            return
        }

        amazonGenerator.asinGenerator.clearAll()
    }

    @Scheduled(initialDelay = INITIAL_DELAY, fixedDelay = MINUTE_TO_MILLIS)
    fun removeRetiredUrlCacheCollectors() {
        if (!generateDefaultTasks) {
            return
        }

        amazonGenerator.clearPredefinedTasksIfNotInRunTime()
    }
}
