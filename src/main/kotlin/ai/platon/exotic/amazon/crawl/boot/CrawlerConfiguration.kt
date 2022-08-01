package ai.platon.exotic.amazon.crawl.boot

import ai.platon.exotic.amazon.crawl.core.handlers.jdbc.JdbcSinkRegistry
import ai.platon.exotic.amazon.crawl.boot.component.MainCrawler
import ai.platon.pulsar.common.StartStopRunner
import ai.platon.scent.ScentSession
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableAsync
@EnableScheduling
@ComponentScan(
    "ai.platon.scent.boot.autoconfigure",
    "ai.platon.exotic.amazon.crawl.boot",
)
class CrawlerConfiguration(
    /**
     * The amazon crawler
     * */
    private val mainCrawler: MainCrawler,
    /**
     * Trigger amazon crawler initialization
     * */
    private val applicationContext: ApplicationContext,
    /**
     * The scent session
     * */
    private val session: ScentSession,
) {
    val unmodifiedConfig get() = session.unmodifiedConfig

    @Bean(initMethod = "start", destroyMethod = "stop")
    fun crawlerRunner(): StartStopRunner {
        JdbcSinkRegistry(applicationContext).register()
        return StartStopRunner(mainCrawler)
    }
}
