package com.sweepiesoftie.earmark.core

/**
 * The few words of a message a chip or pill quotes (ADR 0032). A short message is quoted whole. From a long one
 * it takes the sentence that is urgent, or else the last real question, or the last one that asks by its words,
 * because that is what you owe; otherwise the start. No phone Earmark runs on can summarise in the background,
 * so this picks rather than rewrites.
 */
internal object Gist {
    const val MAX_WORDS: Int = 12
    const val MAX_CHARS: Int = 72

    fun of(text: String): String {
        val plain = links.replace(isolates.replace(text, ""), " ")
        val sentences = plain.split(breaks).map { gaps.replace(it, " ").trim() }.filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return trim(gaps.replace(text, " ").trim())
        val whole = sentences.joinToString(" ")
        if (whole.split(' ').size <= MAX_WORDS && whole.length() <= MAX_CHARS) return whole
        val pick = sentences.firstOrNull(Urgency::isUrgent)
            ?: sentences.lastOrNull { it.endsWith('?') }
            ?: sentences.lastOrNull(Urgency::asks)
            ?: sentences.first()
        // Picked from the middle, so the quote doesn't pass for the whole message.
        return (if (pick == sentences.first()) "" else "…") + trim(pick)
    }

    /** Whole words up to [MAX_WORDS] and [MAX_CHARS]; a single word longer than that is cut where it reaches it. */
    private fun trim(sentence: String): String {
        val words = sentence.split(' ')
        val kept = StringBuilder()
        for (word in words.take(MAX_WORDS)) {
            val next = if (kept.isEmpty()) word else "$kept $word"
            if (next.length() > MAX_CHARS) break
            kept.clear().append(next)
        }
        if (kept.isEmpty()) kept.append(sentence, 0, sentence.offsetByCodePoints(0, MAX_CHARS))
        if (kept.length == sentence.length) return sentence
        return kept.toString().trimEnd(' ', ',', '.', ';', ':', '-') + "…"
    }

    private fun String.length(): Int = codePointCount(0, length)

    /** After a sentence's end mark (with the Devanagari danda), or at a line break. */
    private val breaks = Regex("""(?<=[.?!।])\s+|\s*\n+\s*""")
    private val links = Regex("""https?://\S+|www\.\S+""")
    private val gaps = Regex("""\s+""")

    /** The invisible marks WhatsApp puts around a mentioned name. */
    private val isolates = Regex("""[⁦-⁩]""")
}
