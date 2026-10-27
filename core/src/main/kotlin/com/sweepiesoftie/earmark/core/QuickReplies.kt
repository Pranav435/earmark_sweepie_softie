package com.sweepiesoftie.earmark.core

/**
 * One-tap replies for the reply strip when Smart Reply can't answer (ADR 0026). Smart Reply only
 * answers in English, and calls short messages such as "hi" or "ok" not English. These come from
 * the message's words alone. Anything that isn't plainly a question, a greeting or thanks gets
 * replies that suit any message, bad news included.
 */
public object QuickReplies {
    private val questionStart = Regex(
        """(?i)^\s*(what|when|where|who|why|how|which|are|is|can|could|will|would|should|do|does|did|have|has|wanna|r u)\b""",
    )
    private val greeting = Regex("""(?i)^\s*(hi+|hey+|hel+o+|yo+|sup|hola|namaste|good (morning|afternoon|evening|night)|gm|gn)\b""")
    private val thanks = Regex("""(?i)\b(thanks|thank you|thx|ty|tysm)\b""")

    public fun forMessage(text: String): List<String> = when {
        isQuestion(text) && Classifier.hasTimeWord(text) -> listOf("Works for me", "Can't, sorry", "Let me check")
        isQuestion(text) -> listOf("Yes", "No", "Let me check")
        greeting.containsMatchIn(text) -> listOf("Hey!", "Hi! What's up?", "Give me a minute")
        thanks.containsMatchIn(text) -> listOf("You're welcome!", "Anytime", "Of course")
        else -> listOf("Got it", "Will reply soon", "Can I call you?")
    }

    private fun isQuestion(text: String): Boolean = Classifier.hasQuestionMark(text) || questionStart.containsMatchIn(text)
}
