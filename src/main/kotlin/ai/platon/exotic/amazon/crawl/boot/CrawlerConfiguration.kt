package ai.platon.exotic.amazon.crawl.boot

import ai.platon.exotic.amazon.crawl.boot.component.AmazonCrawler
import ai.platon.exotic.amazon.crawl.boot.component.AmazonJdbcSinkSQLExtractor
import ai.platon.exotic.amazon.crawl.core.handlers.parse.WebDataExtractorInstaller
import ai.platon.pulsar.common.StartStopRunner
import ai.platon.pulsar.crawl.parse.ParseFilters
import ai.platon.scent.BasicScentSession
import ai.platon.scent.parse.html.JdbcCommitConfig
import ai.platon.scent.ql.h2.context.ScentSQLContext
import ai.platon.scent.ql.h2.context.ScentSQLContexts
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
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
    @Bean
    @Scope("prototype")
    fun getScentContext(): ScentSQLContext {
        return ScentSQLContexts.create(applicationContext)
    }

    @Bean
    @Scope("prototype")
    fun getScentSession(): BasicScentSession = getScentContext().createSession()

    @Bean(initMethod = "start", destroyMethod = "stop")
    fun crawlerRunner(): StartStopRunner {
        val extractorFactory = { conf: JdbcCommitConfig ->
            applicationContext.getBean<AmazonJdbcSinkSQLExtractor>()
        }

        WebDataExtractorInstaller(extractorFactory).install(parseFilters)

        return StartStopRunner(amazonCrawler)
    }
}
