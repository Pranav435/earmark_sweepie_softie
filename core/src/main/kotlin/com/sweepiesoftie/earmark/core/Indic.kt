package com.sweepiesoftie.earmark.core

/**
 * Reads a word in an Indian script as Latin letters, so a group message to "प्रणव" or "પ્રણવ" counts as one to
 * Pranav. Devanagari, Bengali, Gurmukhi, Gujarati, Oriya, Tamil, Telugu, Kannada and Malayalam share one layout
 * in Unicode, each in its own block of 128, so one table reads them all.
 */
internal object Indic {
    private const val FIRST = 0x0900
    private const val LAST = 0x0DFF

    private val vowels = arrayOf("a", "aa", "i", "ii", "u", "uu", "ri", "l", "e", "e", "e", "ai", "o", "o", "o", "au")
    private val consonants = arrayOf(
        "k", "kh", "g", "gh", "n", "ch", "chh", "j", "jh", "n", "t", "th", "d", "dh", "n", "t", "th", "d", "dh", "n", "n",
        "p", "ph", "b", "bh", "m", "y", "r", "r", "l", "l", "l", "v", "sh", "sh", "s", "h",
    )
    private val signs = arrayOf("aa", "i", "ii", "u", "uu", "ri", "ri", "e", "e", "e", "ai", "o", "o", "o", "au")
    private val withDot = arrayOf("q", "kh", "g", "z", "r", "rh", "f", "y")

    /** Endings that attach to a name in Marathi and Hindi: "प्रणवला", "प्रणवजी". */
    private val endings = listOf("la", "cha", "chi", "che", "ne", "na", "ji", "da", "di", "kade", "bhai", "bhaiya")

    private val word = Regex("""[ऀ-෿]{2,}""")

    fun words(text: String): List<String> = word.findAll(text).map { it.value }.toList()

    /** Letters as they're said: a consonant carries "a" unless a vowel sign or virama follows, and a word's last "a" isn't said. */
    fun romanize(text: String): String {
        val out = StringBuilder()
        var inherent = false
        fun said() {
            if (inherent) out.append('a')
            inherent = false
        }
        for (c in text) {
            if (c.code !in FIRST..LAST) {
                said()
                out.append(c.lowercaseChar())
                continue
            }
            when (val at = (c.code - FIRST) and 0x7F) {
                0x3C -> {}
                0x4D -> inherent = false
                in 0x3E..0x4C -> {
                    inherent = false
                    out.append(signs[at - 0x3E])
                }
                in 0x15..0x39 -> {
                    said()
                    out.append(consonants[at - 0x15])
                    inherent = true
                }
                in 0x58..0x5F -> {
                    said()
                    out.append(withDot[at - 0x58])
                    inherent = true
                }
                in 0x05..0x14 -> {
                    said()
                    out.append(vowels[at - 0x05])
                }
                0x01, 0x02 -> {
                    said()
                    out.append('n')
                }
                else -> said()
            }
        }
        return out.toString()
    }

    /**
     * The same name, spelled either way: "प्रणव" and "Pranav", "প্রণব" and "Pranav". Spellings differ in long vowels,
     * aspiration, v and w, and an unsaid "a", so a word also matches when its consonants do, for names with three or more.
     */
    fun sameName(word: String, name: String): Boolean {
        val spoken = key(romanize(word))
        val written = key(romanize(name))
        if (written.length < 2) return false
        if (spoken == written || endings.any { spoken == written + it }) return true
        val consonantsOf = { s: String -> s.filter { it !in "aeiou" } }
        val skeleton = consonantsOf(written)
        return skeleton.length >= 3 && endings.plus("").any { consonantsOf(spoken) == skeleton + consonantsOf(it) }
    }

    private val sounds = listOf(
        "chh" to "ch", "sh" to "s", "ph" to "f", "kh" to "k", "gh" to "g", "jh" to "j", "th" to "t", "dh" to "d", "bh" to "b",
        "w" to "v", "b" to "v", "z" to "j", "q" to "k", "ee" to "i", "oo" to "u", "aa" to "a", "ii" to "i", "uu" to "u",
    )
    private val doubled = Regex("""(.)\1+""")

    private fun key(text: String): String {
        var plain = text.lowercase().filter { it in 'a'..'z' }
        sounds.forEach { (from, to) -> plain = plain.replace(from, to) }
        return doubled.replace(plain, "$1")
    }
}
