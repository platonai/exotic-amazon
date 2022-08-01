package ai.platon.exotic.amazon.tools.environment

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.common.options.LoadOptions
import ai.platon.pulsar.context.support.AbstractPulsarContext
import ai.platon.pulsar.persist.CrawlStatus
import ai.platon.pulsar.persist.ProtocolStatus
import ai.platon.scent.context.withContext
import org.apache.commons.lang3.SystemUtils

fun main(args: Array<String>) {
    var portalUrl = "https://www.amazon.ca/"
    var loadArguments = "-i 1s -ignoreFailure"
    var gui = SystemUtils.IS_OS_WINDOWS

    var i = 0
    while (i++ < args.size - 1) {
        if (args[i] == "-url") portalUrl = args[i++]
        if (args[i] == "-args") loadArguments = args[i++]
        if (args[i] == "-gui") gui = true
    }

    val options = LoadOptions.parse(loadArguments, VolatileConfig.UNSAFE)
    // New York City
    val zipcode = listOf("J0K 0A1", "J0K 0A2", "J0K 0A3", "J0K 0A4")
        .map { it.split(" ") }
        .map { it[0] to it[1] }
        .shuffled().first()
    val chooseDistrictExpressions = ResourceLoader.readString("sites/amazon/js/choose-district.ca.js")
        .replace("A1A", zipcode.first)
        .replace("1A1", zipcode.second)
        .split(";\n")
        .filter { it.isNotBlank() }
        .filter { !it.startsWith("// ") }
        .joinToString(";\n")

    withContext { cx ->
        val unmodifiedConfig = (cx as AbstractPulsarContext).unmodifiedConfig.unbox()
        System.setProperty(CapabilityTypes.PRIVACY_CONTEXT_ID_GENERATOR_CLASS,
            "ai.platon.pulsar.crawl.fetch.privacy.PrototypePrivacyContextIdGenerator")

        val session = cx.createSession()
        options.refresh = true

        // 1. warn up
        val page = session.load(portalUrl, options)
        page.protocolStatus = ProtocolStatus.STATUS_NOTFETCHED
        page.crawlStatus = CrawlStatus.STATUS_UNFETCHED

        var document = session.parse(page)
        var text = document.selectFirstOrNull("#glow-ingress-block")?.text() ?: "(unknown)"
        println("Current area: $text")

        TODO("Event handler registration is updated")
        // 3. choose district
        // unmodifiedConfig.set(FETCH_CLIENT_JS_AFTER_FEATURE_COMPUTE, chooseDistrictExpressions)
        session.load(portalUrl, options)

        // 4. check the result
        // unmodifiedConfig.unset(FETCH_CLIENT_JS_AFTER_FEATURE_COMPUTE)
        document = session.loadDocument(portalUrl, options)

        text = document.selectFirstOrNull("#nav-tools a span.icp-nav-flag")?.attr("class") ?: "(unknown)"
        println("Current country: $text")

        text = document.selectFirstOrNull("#glow-ingress-block")?.text() ?: "(unknown)"
        println("Current area: $text")
        val path = session.export(document)
        println("Exported to file://$path")
    }
}
