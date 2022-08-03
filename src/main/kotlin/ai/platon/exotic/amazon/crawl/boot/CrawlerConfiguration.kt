package ai.platon.exotic.amazon.crawl.boot

import ai.platon.exotic.amazon.crawl.boot.component.JDBCSinkSQLExtractor
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.pulsar.common.StartStopRunner
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.scent.parse.html.JdbcCommitConfig
import org.springframework.beans.factory.getBean
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
    private val amazonCrawler: AmazonCrawler,
    /**
     * The parse filter manager
     * */
    private val parseFilters: ParseFilters,
    /**
     * Trigger amazon crawler initialization
     * */
    private val applicationContext: ApplicationContext,
) {
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun crawlerRunner(): StartStopRunner {
        val extractorFactory = { conf: JdbcCommitConfig ->
            applicationContext.getBean<JDBCSinkSQLExtractor>()
        }

        WebDataExtractorInstaller(extractorFactory).install(parseFilters)

        return StartStopRunner(amazonCrawler)
    }
}
