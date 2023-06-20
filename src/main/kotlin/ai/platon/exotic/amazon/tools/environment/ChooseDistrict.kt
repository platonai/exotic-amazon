package ai.platon.exotic.amazon.tools.environment

import ai.platon.pulsar.browser.common.BrowserSettings
import ai.platon.pulsar.browser.common.InteractSettings
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.alwaysTrue
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.emoji.PopularEmoji
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class DistrictInfo(
    val domain: String,
    val country: String,
    val zipcode: String,
    val display: String,
    val verify: String,
    val language: String,
    val comment: String,
)

class ChooseLanguageJsEventHandler(
    val districtInfo: DistrictInfo,
): WebPageWebDriverEventHandler() {
    override suspend fun invoke(page: WebPage, driver: WebDriver): Any? {
        val language = districtInfo.language
        val expressions = """
document.querySelector("input[value*=$language]").value;
document.querySelector("input[value*=$language]").click();
document.querySelector("span#icp-save-button input[type=submit]").value;
document.querySelector("span#icp-save-button input[type=submit]").click();
"""
        expressions.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach {
            val result = driver.evaluate(it)
            println(">>> $it")
            println(StringUtils.normalizeSpace(result?.toString()))
            delay(1000)
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
        val delimiters = listOf(" ", "-", "#")
        delimiters.forEach { delimiter ->
            if (zipcode.contains(delimiter)) {
                val zipcodePair = zipcode.split(delimiter)
                script = script
                    .replace("zipcode1/2", zipcodePair[0])
                    .replace("zipcode2/2", zipcodePair[1])
            }
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
    val chooseLanguageUrl = "https://www.amazon.com/gp/customer-preferences/select-language"
    private val interactSettings = InteractSettings(initScrollPositions = "0.2", scrollCount = 0)
    private val options = session.options("-refresh")
    private val lock = ReentrantLock()
    private val isDone = lock.newCondition()

    private val distrctLines = ResourceLoader.readAllLines("config/districts.csv")
    private val headerLine = distrctLines.firstOrNull() ?: ""

    private val csvText = distrctLines.joinToString("\n")
    // private val header = "domain,country,priority,zipcode,country display,verify,language,currency symbol,currency choice,comment".split(",").toTypedArray()
    private val header = headerLine.split(",").toTypedArray()
    private val format = CSVFormat.Builder.create()
        .setHeader(*header)
        .setSkipHeaderRecord(true) // autodetect headers
        .build()
    val districts = CSVParser.parse(csvText, format).map { record ->
        DistrictInfo(
            domain = record.get("domain"),
            country = record.get("country"),
            zipcode = record.get("zipcode"),
            display = record.get("country display"),
            verify = record.get("verify"),
            language = record.get("language"),
            comment = record.get("comment"),
        )
    }
        .filterNot { it.domain.contains(".co.jp") }  // Japanese site requires special proxy

    val domainDistricts = districts.associateBy { it.domain }

    fun choose() {
        districts.forEach { district ->
            ChooseDistrict(session = session).choose(district)

//            println("Press enter to continue ...")
//            readLine()
        }
    }

    fun choose(domain: String) {
        val district = domainDistricts[domain]
        if (district == null) {
            logger.warn("Domain not supported: $domain")
            return
        }

        chooseLanguage0(district)
        choose0(district)

        lock.withLock {
            isDone.signal()
        }
    }

    fun choose(district: DistrictInfo) {
        choose0(district)

        lock.withLock {
            isDone.signal()
        }
    }

    fun chooseLanguage(district: DistrictInfo) {
        chooseLanguage0(district)

        lock.withLock {
            isDone.signal()
        }
    }

    fun await() {
        lock.withLock {
            isDone.await(2, TimeUnit.MINUTES)
        }
    }

    private fun chooseLanguage0(district: DistrictInfo) {
        val language = district.language
        if (language == "N/A") {
            logger.info("{} Language is OK (not required) | {} {}",
                PopularEmoji.WHITE_HEAVY_CHECK, district.zipcode, district.domain)
            return
        }

        val portalUrl = "https://www." + district.domain
        logger.info("Choose language for $portalUrl")

        val be = options.event.browseEvent
        be.onWillNavigate.addLast { page, driver ->
            page.setVar("InteractSettings", interactSettings)
            null
        }

        // 1. warm up
        logger.info("1. Check the last language ...")
        val page = session.load(portalUrl, options)

        var document = session.parse(page)
        var text = document.selectFirstOrNull("#nav-tools a[href~=preferences]")?.cleanText ?: "(unknown)"
        if (text.contains(language)) {
            logger.info("{} Language is already set correctly: {}", PopularEmoji.WHITE_HEAVY_CHECK, text)
            return
        }

        logger.info("Current language: $text")

        // 3. choose district
        val preferenceUrl = document.selectFirstOrNull("#nav-tools a[href~=preferences]")?.attr("abs:href")
        if (preferenceUrl == null) {
            logger.warn("No preference url | {}", district.domain)
            return
        }
        logger.info("3. Choosing language for {} | {}", district.domain, preferenceUrl)
        val jsEventHandler = ChooseLanguageJsEventHandler(district)
        be.onDocumentActuallyReady.addLast(jsEventHandler)
        session.load(preferenceUrl, options)
        be.onDocumentActuallyReady.remove(jsEventHandler)

        // 4. check the result
        logger.info("4. Checking language: (last is $text)")
        document = session.loadDocument(portalUrl, options)

        text = document.selectFirstOrNull("#nav-tools a[href~=preferences]")?.cleanText ?: "(unknown)"
        logger.info("Current language: $text")

        text = document.selectFirstOrNull("#nav-tools a[href~=preferences]")?.cleanText ?: "(unknown)"
        logger.info("Current language (final): $text")
        println("Current language: $text")
        val path = session.export(document)
        logger.info("Exported to file://$path")

        // ISSUE#29: https://github.com/platonai/pulsarr/issues/29
        // Failed to copy chrome data dir when there is a SingletonSocket symbol link
        Files.deleteIfExists(AppPaths.CHROME_DATA_DIR_PROTOTYPE.resolve("SingletonSocket"))
    }

    private fun choose0(district: DistrictInfo) {
        val portalUrl = "https://www." + district.domain
        println()
        println()
        logger.info("Choose district for $portalUrl")

        val be = options.event.browseEvent
        be.onWillNavigate.addLast { page, driver ->
            page.setVar("InteractSettings", interactSettings)
            null
        }

        // 1. warm up
        logger.info("1. Check the last district ...")
        val page = session.load(portalUrl, options)

        var document = session.parse(page)
        var text = document.selectFirstOrNull("#glow-ingress-block")?.cleanText ?: "(unknown)"
        if (text.contains(district.verify)) {
            val message = String.format("%s District is already set correctly: %s",
                PopularEmoji.WHITE_HEAVY_CHECK, text)
            println(message)
            logger.info(message)
            return
        }

        logger.info("Current area: $text")

        // 2. choose language
//        var jsEventHandler: JsEventHandler = ChooseLanguageJsEventHandler()
//        session.load(chooseLanguageUrl, options)
//        session.sessionConfig.removeBean(jsEventHandler)

        // 3. choose district
        logger.info("3. Choosing: $text")
        val jsEventHandler = ChooseCountryJsEventHandler(district)
        be.onDocumentActuallyReady.addLast(jsEventHandler)
        session.load(portalUrl, options)
        be.onDocumentActuallyReady.remove(jsEventHandler)

        // 4. check the result
        logger.info("4. Checking: (last is $text)")
        document = session.loadDocument(portalUrl, options)

        text = document.selectFirstOrNull("#nav-tools a span.icp-nav-flag")?.attr("class") ?: "(unknown)"
        logger.info("Current country: $text")

        text = document.selectFirstOrNull("#glow-ingress-block")?.cleanText ?: "(unknown)"
        println("Current area: $text")
        logger.info("Current area (final): $text")
        val path = session.export(document)
        logger.info("Exported to file://$path")

        // ISSUE#29: https://github.com/platonai/pulsarr/issues/29
        // Failed to copy chrome data dir when there is a SingletonSocket symbol link
        Files.deleteIfExists(AppPaths.CHROME_DATA_DIR_PROTOTYPE.resolve("SingletonSocket"))
    }
}

fun main(argv: Array<String>) {
//    var domain = "amazon.com"
//    var domain = "amazon.uk.co"
//    var domain = "amazon.ca"
//    var domain = "amazon.de"
    var domain = "amazon.de"

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
    ScentEnvironment().checkEnvironment()
    // And then override scent environment properties
    BrowserSettings.privacy(1).maxTabs(1)
        .supervised()
        .withSPA()
    System.setProperty(CapabilityTypes.PRIVACY_CONTEXT_ID_GENERATOR_CLASS, "ai.platon.pulsar.crawl.fetch.privacy.PrototypePrivacyContextIdGenerator")

    val session = ScentContexts.createSession()
    ChooseDistrict(session = session).choose(domain)
}
