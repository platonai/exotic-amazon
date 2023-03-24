package ai.platon.exotic.amazon.tools.environment

import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.browser.common.InteractSettings
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.alwaysTrue
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.context.PulsarContexts
import ai.platon.pulsar.crawl.event.WebPageWebDriverEventHandler
import ai.platon.pulsar.crawl.fetch.driver.WebDriver
import ai.platon.pulsar.dom.nodes.node.ext.cleanText
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.session.PulsarSession
import ai.platon.scent.ScentEnvironment
import ai.platon.scent.context.ScentContexts
import kotlinx.coroutines.delay
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.nio.file.Files

data class DistrictInfo(
    val domain: String,
    val country: String,
    val zipcode: String,
    val display: String,
    val verify: String,
    val comment: String,
)

class ChooseLanguageJsEventHandler: WebPageWebDriverEventHandler() {
    var verbose = true

    override suspend fun invoke(page: WebPage, driver: WebDriver): Any? {
        val expressions = "document.querySelector(\"input[value=en_US]\").click();\n" +
                "document.querySelector(\"span#icp-btn-save input[type=submit]\").click();"
        expressions.split(";").forEach {
            driver.evaluate(it)
        }
        return null
    }
}

class ChooseCountryJsEventHandler(
    val districtInfo: DistrictInfo,
): WebPageWebDriverEventHandler() {
    override suspend fun invoke(page: WebPage, driver: WebDriver): Any? {
//        val zipcode = listOf("30301").shuffled().first()
        var script = ResourceLoader.readString("sites/amazon/js/choose-district.js")
        val zipcode = districtInfo.zipcode.trim()
        if (zipcode.contains(" ")) {
            val zipcodePair = zipcode.split(" ")
            script = script
                .replace("zipcode1/2", zipcodePair[0])
                .replace("zipcode2/2", zipcodePair[1])
        }
        val expressions = script.replace("10001", zipcode)
            .split(";\n")
            .filter { it.isNotBlank() }
            .filter { !it.startsWith("// ") }
            .joinToString(";\n")

        expressions.split(";").map { it.trim() }.forEach {
            val result = driver.evaluate(it)
            println(">>> $it")
            println(StringUtils.normalizeSpace(result?.toString()))
            delay(1000)
        }

        return null
    }
}

class ChooseDistrict(
    private val session: PulsarSession = PulsarContexts.createSession()
) {
    private val logger = LoggerFactory.getLogger(ChooseDistrict::class.java)
    private val interactSettings = InteractSettings(initScrollPositions = "0.2", scrollCount = 0)
    private val options = session.options("-refresh")

    private val csvText = ResourceLoader.readString("config/districts.csv")
    private val header = "domain,country,zipcode,display,verify,comment".split(",").toTypedArray()
    private val format = CSVFormat.Builder.create()
        .setHeader(*header)
        .setSkipHeaderRecord(true)
        .build()
    private val csvParser = CSVParser.parse(csvText, format)
    val districts = csvParser.map { record ->
        DistrictInfo(
            domain = record.get("domain"),
            country = record.get("country"),
            zipcode = record.get("zipcode"),
            display = record.get("display"),
            verify = record.get("verify"),
            comment = record.get("comment"),
        )
    }

    fun choose() {
        districts.forEach { district ->
            ChooseDistrict(session = session).choose(district)
        }
    }

    fun choose(domain: String) {
        val district = districts.firstOrNull { it.domain == domain }
        if (district == null) {
            logger.warn("Domain not supported: $domain")
            return
        }

        choose(district)
    }

    fun choose(district: DistrictInfo) {
        val portalUrl = "https://www." + district.domain
        println()
        println()
        println("Choose district for $portalUrl")

        val be = options.event.browseEvent
        be.onWillNavigate.addLast { page, driver ->
            page.setVar("InteractSettings", interactSettings)
            null
        }

        // 1. warm up
        println("1. Check the last district ...")
        val page = session.load(portalUrl, options)

        var document = session.parse(page)
        var text = document.selectFirstOrNull("#glow-ingress-block")?.cleanText ?: "(unknown)"
        if (text.contains(district.verify)) {
            logger.info("District is already set correctly: {}", text)
            return
        }

        println("Current area: $text")

        // 3. choose district
        println("3. Choosing: $text")
        val jsEventHandler = ChooseCountryJsEventHandler(district)
        be.onDocumentActuallyReady.addLast(jsEventHandler)
        session.load(portalUrl, options)
        be.onDocumentActuallyReady.remove(jsEventHandler)

        // 4. check the result
        println("4. Checking: (last is $text)")
        document = session.loadDocument(portalUrl, options)

        text = document.selectFirstOrNull("#nav-tools a span.icp-nav-flag")?.attr("class") ?: "(unknown)"
        logger.info("Current country: $text")

        text = document.selectFirstOrNull("#glow-ingress-block")?.cleanText ?: "(unknown)"
        logger.info("Current area: $text")
        val path = session.export(document)
        logger.info("Exported to file://$path")

        // ISSUE#29: https://github.com/platonai/pulsarr/issues/29
        // Failed to copy chrome data dir when there is a SingletonSocket symbol link
        Files.deleteIfExists(AppPaths.CHROME_DATA_DIR_PROTOTYPE.resolve("SingletonSocket"))
    }
}

fun main(argv: Array<String>) {
    var domain = "amazon.com"
//    var domain = "amazon.uk.co"
//    var domain = "amazon.ca"

    var i = 0
    while (i < argv.size) {
        val arg = argv[i]
        if (i == argv.size - 1) {
            domain = arg
        }
        ++i
    }

    if (domain.isBlank()) {
        println("Domain should be specified.")
        return
    }

    // First set scent environment properties
    // TODO: remove scent dependency
    ScentEnvironment().checkEnvironment()
    // And then override default environment properties
    System.setProperty(CapabilityTypes.PRIVACY_CONTEXT_ID_GENERATOR_CLASS, "ai.platon.pulsar.crawl.fetch.privacy.PrototypePrivacyContextIdGenerator")

    // If you are running in a server without GUI, try headless mode
    BrowserSettings.privacy(1).maxTabs(1).headed().withSPA()

    val session = ScentContexts.createSession()
    ChooseDistrict(session = session).choose(domain)
}
