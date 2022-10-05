package ai.platon.exotic.amazon.crawl

import ai.platon.exotic.amazon.crawl.boot.CrawlerInitializer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.ImportResource
import org.springframework.test.context.ContextConfiguration

@SpringBootApplication
@ContextConfiguration(initializers = [CrawlerInitializer::class])
@ComponentScan(
    "ai.platon.exotic.amazon.crawl.boot",
    "ai.platon.scent.boot.autoconfigure",
    "ai.platon.scent.rest.api"
)
@ImportResource("classpath:config/app/app-beans/app-context.xml")
class TestApplication
