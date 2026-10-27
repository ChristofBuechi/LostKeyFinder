package ch.lostkeyfinder.translations

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Service
class Translations {
    private val catalogs: Map<String, JsonNode> =
        listOf("de", "en").associateWith { language ->
            ClassPathResource("translations/$language.json").inputStream.use { JsonMapper.builder().build().readTree(it) }
        }
    private val placeholder = Regex("\\{([A-Za-z][A-Za-z0-9]*)}")

    fun text(
        language: String,
        key: String,
        parameters: Map<String, String> = emptyMap(),
    ): String {
        val catalog = catalogs[language] ?: catalogs.getValue("de")
        val node = key.split('.').fold(catalog) { value, part -> requireNotNull(value[part]) { "Missing translation: $key" } }
        require(node.isString) { "Translation must be text: $key" }
        return placeholder.replace(node.asString()) { match -> parameters.getValue(match.groupValues[1]) }
    }
}
