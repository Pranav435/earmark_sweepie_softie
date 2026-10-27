package com.sweepiesoftie.earmark.core

import java.text.Normalizer
import java.time.Duration

/** What the messages waiting for you ask of you. Only [reasons] reach DebugLog, never the words (ADR 0027). */
internal data class Reading(
    /** How soon a reply is owed, 0..1. */
    val due: Double,
    /** Why, for DebugLog: "urgent", "asks", "time", "keeps writing". */
    val reasons: List<String>,
    val urgent: Boolean,
)

/**
 * Reads what a message asks of you from its words, on the phone, with word lists in English, Hinglish and
 * Hindi (ADR 0027). Rules rather than a model: they run on any phone, and the log can say which one fired.
 */
internal object Urgency {
    /** Three messages within this long of the latest: they keep writing. */
    private val burstWindow: Duration = Duration.ofMinutes(10)
    private const val BURST = 3

    private val urgentWords = words(
        """urgent(ly)?""", """emergency""", """asap""", """a\.s\.a\.p""", """immediately""", """right away""", """sos""",
        """(please|pls|plz|need|send) help""", """help!+""", """accident""", """hospital""", """ambulance""",
        """(pick(ing)?|answer(ing)?) up (the |your |ur )?phone""", """answer (the |your |ur )?phone""",
        """(not|aren't|isn't|why aren't (you|u)) (picking up|answering)""", """where (are|r) (you|u)""",
        """(are|r) (you|u) (ok|okay|alright|safe)""", """is everything (ok|okay|alright|fine)""", """call me (back )?(right )?now""",
        // Hinglish
        """jaldi+""", """jldi""", """turant""", """fau?ran""", """(zaroori|zaruri|jaruri|jaroori|jruri)""", """phone uth\w*""",
        """(kaha|kahan|kidhar) ho""", """madad""",
        // Hindi
        """तुरंत""", """जल्दी""", """ज़?रूरी""", """फ़?ोन उठा\S*""", """(कहाँ|कहां|किधर) हो""", """मदद""",
    )

    private val requestWords = words(
        """(can|could|would|will) (you|u)""", """(please|pls|plz|plss)""", """let me know""", """lmk""", """tell me""", """send me""",
        """(call|ring|text) me""", """call back""", """need (you|u) to""",
        // Hinglish
        """bata(o|na|do|dena)?""", """bhej(o|na|do|dena)?""", """(kar|kr) (do|dena|de)""", """(kardo|krdo)""", """call (kar|karo|kr|kro)""",
        // Hindi
        """(बताओ|बताना|भेजो|भेज दो|कर दो|कॉल करो|फोन करो)""",
    )

    /** Words that answer or react without asking anything back. */
    private val acknowledgements = setOf(
        "ok", "okay", "okk", "okkk", "okie", "oki", "ohk", "k", "kk", "kkk", "lol", "lmao", "lmfao", "rofl", "xd",
        "thanks", "thanx", "thnx", "thx", "ty", "tysm", "welcome", "cool", "nice", "great", "sure", "np", "yes", "yeah", "yea",
        "yep", "yup", "ya", "yaa", "haan", "han", "no", "nope", "nah", "done", "noted", "alright", "fine", "sweet", "perfect",
        "awesome", "gn", "hmm", "hm", "hmmm", "mm", "oh", "ohh", "ah", "wow", "true", "same", "sticker", "gif", "accha", "acha", "achha",
    )
    private val laughs = Regex("""^((a?ha)+h?|(he)+h?|(hi){2,}|lo+l)$""")

    /** Said in two words but meaning one acknowledgement. */
    private val phrases = listOf(
        "thank you" to "thanks", "thank u" to "thanks", "got it" to "ok", "sounds good" to "ok", "all good" to "ok",
        "no problem" to "np", "no worries" to "np", "good night" to "gn", "you're welcome" to "welcome", "youre welcome" to "welcome",
        "theek hai" to "ok", "thik hai" to "ok", "thk h" to "ok",
    ).map { (from, to) -> Regex("""(?<![\p{L}\p{N}])${Regex.escape(from)}(?![\p{L}\p{N}])""") to to }
    private val reactions = Regex("""^(reacted\s.*\bto\b|liked (a|your) message|(loved|liked|laughed at|emphasized|disliked)\s*“)""")

    /** Everything but letters, digits and apostrophes, including emoji and the selectors that colour them. */
    private val notWords = Regex("""[^\p{L}\p{M}\p{N}\s']|[︎️⃣]""")
    private val spaces = Regex("""\s+""")

    fun read(messages: List<Message>, inGroup: Boolean): Reading {
        val texts = messages.map { normalize(it.text) }
        val urgent = texts.any { urgentWords.containsMatchIn(it) }
        val asks = texts.any(::asks)
        val time = texts.any(Classifier::hasTimeWord)
        val latest = messages.maxOfOrNull { it.at }
        val keepsWriting = !inGroup && latest != null && messages.count { !it.at.isBefore(latest.minus(burstWindow)) } >= BURST
        val reasons = listOfNotNull("urgent".takeIf { urgent }, "asks".takeIf { asks }, "time".takeIf { time }, "keeps writing".takeIf { keepsWriting })
        val due = if (urgent) 1.0 else listOf(asks, time, keepsWriting).count { it } * 0.25 + 0.5
        return Reading(due.coerceAtMost(1.0), reasons, urgent)
    }

    fun isUrgent(text: String): Boolean = urgentWords.containsMatchIn(normalize(text))

    /** A question mark in any script, or a request such as "can you" or "let me know". */
    fun asks(text: String): Boolean = Classifier.hasQuestionMark(text) || requestWords.containsMatchIn(normalize(text))

    /** Only "ok", "thanks", laughter, emoji or a reaction: nothing is owed back. */
    fun acknowledges(text: String): Boolean {
        if (Classifier.hasQuestionMark(text)) return false
        var plain = normalize(text).lowercase()
        if (reactions.containsMatchIn(plain)) return true
        plain = notWords.replace(plain, " ").replace(spaces, " ").trim()
        phrases.forEach { (from, to) -> plain = from.replace(plain, to) }
        return plain.split(' ').filter { it.isNotEmpty() }.all { it in acknowledgements || laughs.matches(it) }
    }

    /**
     * Your name from your contact card, its first word, or the app's @name for you (WhatsApp's "@You"), as a
     * whole word. The plain word "you" never counts. Names shorter than two letters are left out.
     */
    fun mentions(text: String, names: List<String>, selfName: String?): Boolean {
        val plain = normalize(text)
        val forms = names.flatMap { listOf(it.trim(), it.trim().substringBefore(' ')) }.filter { it.length >= 2 }.distinct()
        if (forms.any { Regex("""$BEFORE@?${Regex.escape(it)}$AFTER""", RegexOption.IGNORE_CASE).containsMatchIn(plain) }) return true
        val self = selfName?.trim()?.takeIf { it.length >= 2 } ?: return false
        return Regex("""@${Regex.escape(self)}$AFTER""", RegexOption.IGNORE_CASE).containsMatchIn(plain)
    }

    /** Composed Hindi letters and curly apostrophes, so the word lists match however the keyboard typed them. */
    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).replace('’', '\'')

    private fun words(vararg patterns: String): Regex =
        Regex("""$BEFORE(?:${patterns.joinToString("|")})$AFTER""", RegexOption.IGNORE_CASE)

    private const val BEFORE = """(?<![\p{L}\p{M}\p{N}])"""
    private const val AFTER = """(?![\p{L}\p{M}\p{N}])"""
}
