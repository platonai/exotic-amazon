package ai.platon.exotic.amazon.tools.category

import ai.platon.pulsar.common.urls.UrlUtils
import ai.platon.pulsar.common.urls.preprocess.UrlNormalizer

/**
 * Normalize category urls
 * */
class CategoryUrlNormalizer: UrlNormalizer {
    val redundantUrlParameters = arrayOf("qid", "ref", "_ref", "ref_")
    val redundantUrlParts = mapOf<String, String>()

    override fun invoke(url: String?): String? {
        if (url == null) return null

        return normalize(url)
    }

    fun normalize(url: String): String {
        var u = url

        var parts = u.split("/")
        if (parts.last().matches("(\\d{3})-(\\d+)-(\\d+)".toRegex())) {
            parts = parts.dropLast(1)
        }
        u = parts.filterNot { it.startsWith("ref=") }.joinToString("/")
        u = u.substringBefore("/ref=")

        redundantUrlParameters.forEach { q ->
            u = UrlUtils.removeQueryParameters(u, q)
        }
        redundantUrlParts.forEach { (key, value) ->
            u = key.replace(url, value)
        }
        return u.removeSuffix("?")
    }
}
