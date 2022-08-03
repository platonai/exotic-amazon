package ai.platon.exotic.amazon.crawl.core

import ai.platon.pulsar.dom.features.AbstractFeatureCalculator
import ai.platon.pulsar.dom.features.FeatureRegistry
import ai.platon.pulsar.dom.nodes.forEach
import ai.platon.exotic.amazon.tools.common.AmazonUrls
import org.apache.commons.lang3.StringUtils
import org.apache.commons.math3.linear.ArrayRealVector
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

/**
 * Calculate amazon.com specific features
 * */
class AmazonFeatureCalculator : AbstractFeatureCalculator() {
    val jsVariableNames = listOf("parentAsin", "num_total_variations", "dimensionValuesDisplayData", "dimensionsDisplay")

    override fun calculate(document: Document) {
        val url = document.baseUri()

        if (AmazonUrls.isItemPage(url)) {
            extractVariablesInScripts(document)
        }
    }

    /**
     * Extract valuable data in the inlined scripts.
     * */
    private fun extractVariablesInScripts(document: Document) {
        var variablesStart: Int = -1
        var variablesEnd: Int = -1

        var variables: String? = null
        val jsVariableValues = mutableMapOf<String, String?>()
        NodeTraversor.filter(object : NodeFilter {
            override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                if (node is Element && node.tagName() == "script") {
                    val data = node.data()

                    if (variablesStart == -1 && variablesEnd == -1) {
                        variablesStart = data.indexOfAny(listOf("dpEnvironment", "ajaxUrlParams", "currentAsin"))
                        variablesEnd = data.indexOfAny(listOf("isIconPresentForDimensionValue", "hierarchicalPivoting", "topHierarchicalDimensionIndex"))
                    }

                    if (variablesStart > 0 && variablesEnd > 0) {
                        variables = data.substring(variablesStart - 1, variablesEnd - 1)

                        jsVariableNames.forEach { name ->
                            jsVariableValues.computeIfAbsent(name) {
                                StringUtils.substringBetween(variables, "\"$name\" : ", ",")
                                        ?.takeIf { it.isNotEmpty() && it[0] != '0' }
                                        ?.removeSurrounding("\"")
                            }
                        }
                    }
                }

                return if (variables == null) {
                    NodeFilter.FilterResult.CONTINUE
                } else NodeFilter.FilterResult.STOP
            }
        }, document.body())

        if (variables != null) {
            val container = document.body().appendElement("div")
                    .attr("id", "pulsarJsVariables")
                    .attr("style", "display: none")
            jsVariableValues.entries.forEach { (key, value) ->
                if (value != null) {
                    container.appendElement("pre").addClass(key).text(value)
                }
            }

            container.appendElement("pre")
                .addClass("jsVariables")
                .text(variables?:"")

            // make sure no exception when access the feature vector
            // TODO: ignore hidden elements in feature calculator
            container.forEach(includeRoot = true) { it.extension.features = ArrayRealVector(FeatureRegistry.dimension) }
        }
    }
}
