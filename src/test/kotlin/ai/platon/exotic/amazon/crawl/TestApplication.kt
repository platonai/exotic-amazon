package ai.platon.exotic.amazon.crawl

import ai.platon.commons.distributed.lock.mongo.configuration.EnableMongoDistributedLock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.ImportResource

@SpringBootApplication
@ComponentScan(
    "ai.platon.exotic.amazon.crawl.boot",
    "ai.platon.scent.boot.autoconfigure",
    "ai.platon.scent.rest.api"
)
@ImportResource("classpath:config/app/app-beans/app-context.xml")
@EnableMongoDistributedLock
class TestApplication
