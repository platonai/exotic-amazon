package ai.platon.exotic.amazon.crawl

import org.junit.Test

class TestEventHandlers: TestBase() {

    @Test
    fun `Ensure JdEventHandler works`() {
        var count = 20
        while (count-- > 0) {
            session.load("https://item.jd.com/100006377581.html")
        }
    }
}
