package app.openhealth.core.nutrition

import java.text.Normalizer
import java.util.Locale

/**
 * Name normalisation and tokenisation shared by the index builder and the app, so the
 * tokens written by `tools/foodindex` are the tokens searched at runtime. Accents are
 * folded, punctuation becomes a space, and one-letter words are dropped.
 */
object FoodQuery {
    private val stopWords = setOf(
        "and", "with", "the", "of", "in", "or", "a", "an", "to", "for", "from", "on",
        "added", "unenriched", "includes", "not", "as", "per", "ready", "prepared",
    )

    fun normalize(name: String): String {
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
        val ascii = buildString {
            decomposed.forEach { c ->
                val type = Character.getType(c)
                if (type != Character.NON_SPACING_MARK.toInt() &&
                    type != Character.COMBINING_SPACING_MARK.toInt()
                ) {
                    append(c)
                }
            }
        }
        return ascii.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokens(name: String): List<String> = normalize(name).split(' ')
        .filter { it.length >= 2 && it !in stopWords }
        .distinct()
}
