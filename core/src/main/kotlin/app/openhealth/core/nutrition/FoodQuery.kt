package app.openhealth.core.nutrition

/**
 * Name normalisation and tokenisation shared by the index builder and the app, so the
 * tokens written by `tools/foodindex` are the tokens searched at runtime. Accents are
 * folded by a small Latin table (no JVM-only `java.text.Normalizer`, so the module stays
 * KMP-ready), punctuation becomes a space, and one-letter words are dropped.
 */
object FoodQuery {
    private val stopWords = setOf(
        "and", "with", "the", "of", "in", "or", "a", "an", "to", "for", "from", "on",
        "added", "unenriched", "includes", "not", "as", "per", "ready", "prepared",
    )

    /** The Latin-1 and Latin Extended-A letters a food name is likely to use. */
    private val fold: Map<Char, String> = buildMap {
        fun add(target: String, vararg letters: Char) {
            letters.forEach { put(it, target) }
        }
        add("a", 'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą')
        add("c", 'ç', 'ć', 'ĉ', 'ċ', 'č')
        add("d", 'ď', 'đ')
        add("e", 'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě')
        add("g", 'ĝ', 'ğ', 'ġ', 'ģ')
        add("h", 'ĥ', 'ħ')
        add("i", 'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ı')
        add("j", 'ĵ')
        add("k", 'ķ')
        add("l", 'ĺ', 'ļ', 'ľ', 'ŀ', 'ł')
        add("n", 'ñ', 'ń', 'ņ', 'ň', 'ŉ')
        add("o", 'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ŏ', 'ő')
        add("r", 'ŕ', 'ŗ', 'ř')
        add("s", 'ś', 'ŝ', 'ş', 'š', 'ș')
        add("t", 'ţ', 'ť', 'ŧ', 'ț')
        add("u", 'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų')
        add("w", 'ŵ')
        add("y", 'ý', 'ÿ', 'ŷ')
        add("z", 'ź', 'ż', 'ž')
        put('ß', "ss")
        put('æ', "ae")
        put('œ', "oe")
        put('þ', "th")
        put('ð', "d")
    }

    fun normalize(name: String): String {
        val folded = buildString(name.length) {
            name.lowercase().forEach { character ->
                append(fold[character] ?: character)
            }
        }
        return folded
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokens(name: String): List<String> = normalize(name).split(' ')
        .filter { it.length >= 2 && it !in stopWords }
        .distinct()
}
