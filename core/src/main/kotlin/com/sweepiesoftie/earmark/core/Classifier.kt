package com.sweepiesoftie.earmark.core

import java.time.Duration

internal sealed interface Classification {
    data class Open(val loop: Loop) : Classification
    /** You answered this conversation yourself, so its Reply loop is done. */
    data class Replied(val conversation: String) : Classification
    /** A ride, order or checkout went through in this app. */
    data class Booked(val packageName: String) : Classification
    /** A payment went through in this app. */
    data class Paid(val packageName: String) : Classification
    data class Skip(val reason: String) : Classification
}

/** Turns one notification into a loop, a piece of evidence, or a skip with a reason for DebugLog. */
internal object Classifier {
    val replyLifetime: Duration = Duration.ofHours(12)
    val paymentLifetime: Duration = Duration.ofHours(48)

    private val oneTimeCode = Regex("""(?i)\b(otp|one[- ]time|verification|security code|passcode|login code|your code|code is)\b""")
    private val codeDigits = Regex("""\b\d{4,8}\b""")
    private val timeWords = Regex(
        """(?i)\b(at \d{1,2}(:\d{2})?|\d{1,2}:\d{2}|\d{1,2}(:\d{2})?\s?(am|pm)|tonight|today|tomorrow|now|asap|soon|this (morning|afternoon|evening)|in \d+ ?(min|mins|minutes|hours?|h))\b""",
    )
    private val requested = Regex("""(?i)\b(requested|requests|requesting|request(s|ed)? (for|of)|asked you for|is asking for|sent you a request)\b""")
    private val requester = Regex("""(?i)^(.+?)\s+(?:has\s+)?(?:requested|requests|is requesting|sent you a request|asked you for|is asking for)""")
    private val requestFrom = Regex("""(?i)request (?:of|for) \S+ from (.+?)[.!]?$""")
    /** Lines that say nothing about what the money was for. */
    private val boilerplate = Regex("""(?i)^(tap to .*|payment request|venmo|paypal|google pay|gpay|phonepe|paytm|cash app)$""")
    private val booked = Regex("""(?i)\b(ride booked|booked|booking confirmed|(ride|order|booking|trip) (is )?confirmed|order (placed|confirmed)|ordered|on the way|arriving in|is \d+ min(ute)?s? away|driver (assigned|is)|out for delivery)\b""")
    private val paid = Regex("""(?i)\b(paid to|payment (successful|sent|complete|completed)|you paid|you sent)\b""")

    fun classify(signal: Signal, settings: Settings): Classification {
        if (signal.isOwn && !settings.demoMode) return Classification.Skip("own notification")
        if (signal.isOngoing) return Classification.Skip("ongoing")
        if (signal.isGroupSummary) return Classification.Skip("group summary")

        val body = listOfNotNull(signal.title, signal.text).joinToString(" ")
        val lastMessage = signal.messages.lastOrNull()
        if (lastMessage == null && isOneTimeCode(body)) return Classification.Skip("one-time code")

        if (lastMessage != null || signal.category == "msg") return classifyMessage(signal, lastMessage)

        if (paid.containsMatchIn(body)) return Classification.Paid(signal.packageName)
        if (booked.containsMatchIn(body)) return Classification.Booked(signal.packageName)
        val money = MoneyParser.find(body)
        if (money != null && requested.containsMatchIn(body)) return openPayment(signal, money)
        return Classification.Skip("not a loop")
    }

    private fun classifyMessage(signal: Signal, last: Message?): Classification {
        val text = last?.text ?: signal.text.orEmpty()
        if (isOneTimeCode(text)) return Classification.Skip("one-time code")
        if (last?.isBot == true) return Classification.Skip("bot")
        if (signal.isGroupConversation && !signal.mentionsUser) return Classification.Skip("group without a mention")
        val conversation = signal.conversationKey ?: signal.key
        if (last?.fromUser == true) return Classification.Replied(conversation)
        val who = last?.sender ?: signal.title ?: return Classification.Skip("no sender")
        return Classification.Open(
            Loop(
                id = LoopId("reply:$conversation"),
                kind = LoopKind.Reply,
                task = "Reply to $who",
                who = who,
                detail = "“${clamp(text)}”",
                packageName = signal.packageName,
                conversation = conversation,
                notificationKey = signal.key,
                route = Route.Notification(signal.key, signal.packageName),
                openedAt = signal.postedAt,
                updatedAt = last?.at ?: signal.postedAt,
                expiresAt = signal.postedAt.plus(replyLifetime),
                due = replyDue(text),
                canReply = signal.hasReplyAction,
            ),
        )
    }

    private fun openPayment(signal: Signal, money: Money): Classification {
        val title = signal.title.orEmpty()
        val text = signal.text.orEmpty()
        val who = requester.find(title)?.groupValues?.get(1)?.trim()
            ?: requester.find(text)?.groupValues?.get(1)?.trim()
            ?: requestFrom.find(text)?.groupValues?.get(1)?.trim()
            ?: requestFrom.find(title)?.groupValues?.get(1)?.trim()
            ?: signal.appLabel
        // The line without the amount is the note ("Dinner at Nori"), unless it is app boilerplate.
        val note = listOfNotNull(signal.title, signal.text)
            .firstOrNull { MoneyParser.find(it) == null && !boilerplate.matches(it.trim()) && it != signal.appLabel }
        return Classification.Open(
            Loop(
                id = LoopId("payment:${signal.conversationKey ?: signal.key}"),
                kind = LoopKind.Payment,
                task = "Pay $who back ${money.short}",
                who = who,
                detail = listOfNotNull(money.full, note).joinToString(" · "),
                packageName = signal.packageName,
                conversation = null,
                notificationKey = signal.key,
                route = Route.Notification(signal.key, signal.packageName),
                openedAt = signal.postedAt,
                updatedAt = signal.postedAt,
                expiresAt = signal.postedAt.plus(paymentLifetime),
                // A direct request for money is owed now.
                due = 1.0,
            ),
        )
    }

    /** A question mark in any script; shared with the quick replies so the two agree. */
    fun hasQuestionMark(text: String): Boolean = text.any { it in QUESTION_MARKS }

    fun hasTimeWord(text: String): Boolean = timeWords.containsMatchIn(text)

    private fun isOneTimeCode(text: String): Boolean = oneTimeCode.containsMatchIn(text) && codeDigits.containsMatchIn(text)

    /** A message to you is half due; a question and a time word add a quarter each, so "free at 5?" is fully due. */
    private fun replyDue(text: String): Double =
        0.5 + (if (hasQuestionMark(text)) 0.25 else 0.0) + (if (hasTimeWord(text)) 0.25 else 0.0)

    /** Latin, Arabic and full-width question marks. */
    private const val QUESTION_MARKS = "?؟？"

    /** Keeps the chip's age visible: long messages are cut at a word near 80 characters. */
    private fun clamp(text: String): String {
        if (text.codePointCount(0, text.length) <= MAX_QUOTE) return text
        val cut = text.offsetByCodePoints(0, MAX_QUOTE)
        val space = text.lastIndexOf(' ', cut).takeIf { it > cut / 2 } ?: cut
        return text.substring(0, space).trimEnd(' ', ',', '.') + "…"
    }

    private const val MAX_QUOTE = 80
}
