package ai.platon.exotic.amazon.crawl.crawl.common

import ai.platon.pulsar.crawl.parse.AbstractParseFilter
import ai.platon.pulsar.crawl.parse.FilterResult
import ai.platon.pulsar.crawl.parse.html.ParseContext

class SimpleParseFilter: AbstractParseFilter() {
    override fun doFilter(parseContext: ParseContext): FilterResult {
        return FilterResult.success()
    }
}
