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
    /** Group chatter that isn't for you: no loop, but a tap on its notification is learned (ADR 0028). */
    data class Quiet(val conversation: String) : Classification
    data class Skip(val reason: String) : Classification
}

/** Turns one notification into a loop, a piece of evidence, or a skip with a reason for DebugLog. */
internal object Classifier {
    val replyLifetime: Duration = Duration.ofHours(12)
    val paymentLifetime: Duration = Duration.ofHours(48)

    /** Email is answered more slowly than chat, so it waits a day. */
    val emailLifetime: Duration = Duration.ofHours(24)

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

    fun classify(signal: Signal, settings: Settings, stats: Stats = Stats()): Classification {
        if (signal.isOwn && !settings.demoMode) return Classification.Skip("own notification")
        if (signal.isOngoing) return Classification.Skip("ongoing")
        if (signal.isGroupSummary) return Classification.Skip("group summary")

        val body = listOfNotNull(signal.title, signal.text).joinToString(" ")
        val lastMessage = signal.messages.lastOrNull()
        if (lastMessage == null && isOneTimeCode(body)) return Classification.Skip("one-time code")

        // Dialers post a missed call as a chat from the caller, so it is told apart before messages are (ADR 0029).
        if (isMissedCall(signal)) return callBack(signal)
        if (signal.category == CATEGORY_EMAIL) return classifyEmail(signal)
        if (isReminder(signal)) return reminder(signal)
        if (lastMessage != null || signal.category == CATEGORY_MESSAGE) return classifyMessage(signal, settings, stats)

        if (paid.containsMatchIn(body)) return Classification.Paid(signal.packageName)
        if (booked.containsMatchIn(body)) return Classification.Booked(signal.packageName)
        val money = MoneyParser.find(body)
        if (money != null && requested.containsMatchIn(body)) return openPayment(signal, money)
        val details = listOfNotNull(signal.title, signal.bigText ?: signal.text).joinToString(" ")
        val owed = MoneyParser.find(details)
        if (owed != null && isBillDue(details)) return openBill(signal, owed, details, biller = signal.appLabel)
        return Classification.Skip("not a loop")
    }

    private fun classifyMessage(signal: Signal, settings: Settings, stats: Stats): Classification {
        val messages = signal.messages.map(::untag)
        val last = messages.lastOrNull()
        val text = last?.text ?: signal.text.orEmpty()
        if (isOneTimeCode(text)) return Classification.Skip("one-time code")
        if (last?.isBot == true) return Classification.Skip("bot")
        val conversation = signal.conversationKey ?: signal.key
        if (last?.fromUser == true) return Classification.Replied(conversation)
        val sender = (last?.sender ?: signal.title)?.trim()
        if (sender != null && businessSender.matches(sender)) return fromBusiness(signal, text, sender)
        // Everything they wrote since your last message, which chat apps keep until you open the chat.
        val waiting = messages.takeLastWhile { !it.fromUser }.ifEmpty { listOf(Message(signal.title, text, signal.postedAt)) }
        if (waiting.all { Urgency.acknowledges(it.text) }) return Classification.Skip("nothing to answer")
        if (signal.isGroupConversation) return classifyGroup(signal, waiting, conversation, settings, stats)
        val who = last?.sender?.let(::person) ?: signal.title ?: return Classification.Skip("no sender")
        return Classification.Open(reply(signal, conversation, who, waiting.last(), Urgency.read(waiting, inGroup = false)))
    }

    /**
     * A group message is a loop only when it's for you (ADR 0028): it mentions you, it's urgent, or it asks
     * something in a group whose notifications you usually open. The loop quotes that message and is as old
     * as it, so the chatter after it doesn't count as someone writing to you again.
     */
    private fun classifyGroup(signal: Signal, waiting: List<Message>, conversation: String, settings: Settings, stats: Stats): Classification {
        val mentioning = waiting.filter { it.mentionsYou || Urgency.mentions(it.text, settings.myNames, signal.selfName) }
        val urgent = waiting.filter { Urgency.isUrgent(it.text) }
        val opensOften = stats.contacts[conversation]?.opensOften == true
        val forYou = when {
            mentioning.isNotEmpty() || urgent.isNotEmpty() -> (mentioning + urgent).distinct().sortedBy { it.at }
            opensOften -> waiting.filter { Urgency.asks(it.text) }
            else -> emptyList()
        }
        if (forYou.isEmpty()) return Classification.Quiet(conversation)
        val quote = forYou.last()
        val who = quote.sender?.let(::person) ?: signal.title ?: return Classification.Skip("no sender")
        val reading = Urgency.read(forYou, inGroup = true)
        val why = listOfNotNull("mentions you".takeIf { mentioning.isNotEmpty() }, "a group you open".takeIf { opensOften })
        return Classification.Open(reply(signal, conversation, who, quote, reading.copy(reasons = reading.reasons + why), group = true))
    }

    private fun reply(signal: Signal, conversation: String, who: String, quote: Message, reading: Reading, group: Boolean = false): Loop {
        val groupName = signal.conversationTitle?.replace(messageCount, "")?.trim()?.takeIf { group && it.isNotEmpty() }
        return Loop(
            id = LoopId("reply:$conversation"),
            kind = LoopKind.Reply,
            task = "Reply to $who" + groupName?.let { " in $it" }.orEmpty(),
            who = who,
            detail = "“${clamp(quote.text)}”",
            packageName = signal.packageName,
            conversation = conversation,
            notificationKey = signal.key,
            route = Route.Notification(signal.key, signal.packageName),
            openedAt = signal.postedAt,
            updatedAt = if (group) quote.at else signal.messages.lastOrNull()?.at ?: signal.postedAt,
            expiresAt = signal.postedAt.plus(replyLifetime),
            due = reading.due,
            canReply = signal.hasReplyAction,
            urgent = reading.urgent,
            why = reading.reasons,
            inGroup = group,
            groupName = groupName,
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

    /**
     * "Missed call", "Missed voice call", "2 missed calls" or "Missed call from Mom", as a whole line, so a chat
     * message such as "sorry I missed your call" stays a message.
     */
    private val missedCall = Regex("""(?i)^[^\p{L}\p{N}]*(\d+\s+)?missed\s+(group\s+)?(voice\s+|video\s+)?calls?\b""")
    private val callerIn = Regex("""(?i)missed\s+(?:group\s+)?(?:voice\s+|video\s+)?calls?\s+from\s+(.+)$""")
    private val callCount = Regex("""(?i)\b(\d+)\s+missed\b|\((\d+)\)\s*$""")
    private val countSuffix = Regex("""\s*\(\d+\)\s*$""")

    private fun isMissedCall(signal: Signal): Boolean = signal.category == CATEGORY_MISSED_CALL ||
        listOfNotNull(signal.title, signal.text, signal.messages.lastOrNull()?.text).any { missedCall.containsMatchIn(it.trim()) }

    /**
     * A missed call is owed a call back, three quarters due: they wanted you then. Someone who called twice is
     * urgent. The caller is the chat's sender (Google's dialer), the line that isn't "Missed call", or "from …".
     */
    private fun callBack(signal: Signal): Classification {
        val lines = listOfNotNull(signal.title, signal.text).map { it.trim() }.filter { it.isNotEmpty() }
        val named = signal.messages.lastOrNull { !it.fromUser }?.sender
            ?: lines.firstNotNullOfOrNull { callerIn.find(it)?.groupValues?.get(1) }
            ?: lines.firstOrNull { !missedCall.containsMatchIn(it) }
        val who = named?.let { person(countSuffix.replace(it, "")) }?.takeIf { it.isNotEmpty() } ?: return Classification.Skip("no caller")
        val count = lines.firstNotNullOfOrNull { line -> callCount.find(line)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toInt() }
            ?: signal.messages.count { missedCall.containsMatchIn(it.text.trim()) }.coerceAtLeast(1)
        val again = count > 1
        return Classification.Open(
            Loop(
                id = LoopId("call:${signal.conversationKey ?: signal.key}"),
                kind = LoopKind.CallBack,
                task = "Call $who back",
                who = who,
                detail = if (again) "$count missed calls" else "Missed call",
                packageName = signal.packageName,
                conversation = signal.conversationKey,
                notificationKey = signal.key,
                route = Route.Notification(signal.key, signal.packageName),
                openedAt = signal.postedAt,
                updatedAt = signal.messages.lastOrNull()?.at ?: signal.postedAt,
                expiresAt = signal.postedAt.plus(replyLifetime),
                due = if (again) 1.0 else 0.75,
                urgent = again,
                why = listOf(if (again) "called $count times" else "missed call"),
            ),
        )
    }

    /** Senders that are a system, not a person: nobody is waiting on a reply to them. */
    private val automated = Regex("""(?i)\b(no[-_. ]?reply|do[-_. ]?not[-_. ]?reply|notifications?|newsletters?|digest|alerts?|mailer|support|team|updates?)\b""")

    /**
     * An email is a loop only when it asks you something or is urgent, by the same word lists as a message
     * (ADR 0027), and isn't from a no-reply sender. Newsletters and receipts ask nothing, so they stay out.
     */
    private fun classifyEmail(signal: Signal): Classification {
        val sender = signal.title?.let(::person)?.takeIf { it.isNotEmpty() } ?: return Classification.Skip("no sender")
        if (automated.containsMatchIn(sender)) return Classification.Skip("automated email")
        val text = signal.bigText ?: signal.text.orEmpty()
        if (isOneTimeCode(text)) return Classification.Skip("one-time code")
        val reading = Urgency.read(listOf(Message(sender, text, signal.postedAt)), inGroup = false)
        if (!reading.urgent && !Urgency.asks(text)) return Classification.Skip("email asks nothing")
        // Gmail and Outlook put the subject on the text's first line.
        val subject = signal.text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: text
        return Classification.Open(
            Loop(
                id = LoopId("email:${signal.conversationKey ?: signal.key}"),
                kind = LoopKind.Email,
                task = "Reply to $sender's email",
                who = sender,
                detail = "“${clamp(subject)}”",
                packageName = signal.packageName,
                conversation = signal.conversationKey,
                notificationKey = signal.key,
                route = Route.Notification(signal.key, signal.packageName),
                openedAt = signal.postedAt,
                updatedAt = signal.postedAt,
                expiresAt = signal.postedAt.plus(emailLifetime),
                due = reading.due,
                canReply = signal.hasReplyAction,
                urgent = reading.urgent,
                why = reading.reasons,
            ),
        )
    }

    private val reminderCategories = setOf(CATEGORY_REMINDER, CATEGORY_EVENT)

    /** Titles that only say it's a reminder; the words of the reminder are then in the text. */
    private val reminderWords = Regex("""(?i)^(reminders?|upcoming|due( today| now| soon)?|today|now|tasks?|to-?dos?)$""")

    private fun isReminder(signal: Signal): Boolean =
        signal.category in reminderCategories || (signal.packageName in CuratedApps.reminders && signal.category == null)

    /** A reminder you set goes off when you meant to do it, so it is fully due as it fires; its words are the task. */
    private fun reminder(signal: Signal): Classification {
        val what = listOfNotNull(signal.title, signal.text).map { gaps.replace(it, " ").trim() }
            .firstOrNull { it.isNotEmpty() && !reminderWords.matches(it) && it != signal.appLabel }
            ?: return Classification.Skip("reminder says nothing")
        return Classification.Open(
            Loop(
                id = LoopId("reminder:${signal.conversationKey ?: signal.key}"),
                kind = LoopKind.Reminder,
                task = clamp(what),
                who = null,
                detail = "Reminder in ${signal.appLabel}",
                packageName = signal.packageName,
                conversation = null,
                notificationKey = signal.key,
                route = Route.Notification(signal.key, signal.packageName),
                openedAt = signal.postedAt,
                updatedAt = signal.postedAt,
                expiresAt = signal.postedAt.plus(replyLifetime),
                due = 1.0,
                why = listOf("reminder"),
            ),
        )
    }

    /** An Indian sender ID such as AX-AIRTEL or VM-HDFCBK-S, or a short code such as 56767. */
    private val businessSender = Regex("""^([A-Z]{2}-[A-Z0-9]{3,9}(-[A-Z])?|\d{3,6})$""")

    /** A text from a business is never a reply; it can be a bill that is due (ADR 0029). */
    private fun fromBusiness(signal: Signal, text: String, sender: String): Classification {
        val money = MoneyParser.find(text)
        if (money == null || !isBillDue(text)) return Classification.Skip("text from a business")
        val name = sender.replace(senderPrefix, "").replace(senderSuffix, "").takeUnless { it.all(Char::isDigit) }
        return openBill(signal, money, text, biller = name)
    }

    private val senderPrefix = Regex("""^[A-Z]{2}-""")
    private val senderSuffix = Regex("""-[A-Z]$""")

    private val billNoun = Regex("""(?i)\b(bills?|statement|emi|premium|dues|rent|invoice)\b""")
    private val billDue = Regex("""(?i)\b(is due|are due|due (on|by|date|today|tomorrow|in)|overdue|pay by|pay before|last date|payment due|amount due|total due|min(imum)? (amount )?due)\b""")

    /** A receipt for a bill also says when it was due. "Ignore if already paid" doesn't count: the bill may still be owed. */
    private val settled = Regex("""(?i)\b(received|successful(ly)?|thank(s| you) for (your )?pay(ment|ing)|has been paid|is paid|you paid|you have paid)\b""")
    private val dueSoon = Regex("""(?i)\b(today|tomorrow|tonight|overdue|immediately)\b""")
    private val billOf = Regex("""(?i)\byour\s+(.{2,30}?)\s+(?:bill|statement|emi|premium|invoice)\b""")
    private val dueDate = Regex("""(?i)\bdue (?:on|by|date:?)\s*([0-3]?\d(?:st|nd|rd|th)?[-/ ](?:[a-z]{3,9}|[01]?\d)(?:[-/ ]\d{2,4})?)""")

    private fun isBillDue(text: String): Boolean = billNoun.containsMatchIn(text) && billDue.containsMatchIn(text) && !settled.containsMatchIn(text)

    /** "Pay your Airtel postpaid bill ₹499": the biller from "your … bill", else the sender ID or the app. */
    private fun openBill(signal: Signal, money: Money, text: String, biller: String?): Classification {
        val who = billOf.find(text)?.groupValues?.get(1)?.trim() ?: biller
        val due = dueDate.find(text)?.groupValues?.get(1)
        return Classification.Open(
            Loop(
                id = LoopId("payment:${signal.conversationKey ?: signal.key}"),
                kind = LoopKind.Payment,
                task = "Pay your ${who?.let { "$it " }.orEmpty()}bill ${money.short}",
                who = who,
                detail = listOfNotNull(money.full, due?.let { "due $it" }).joinToString(" · "),
                packageName = signal.packageName,
                conversation = null,
                notificationKey = signal.key,
                route = Route.Notification(signal.key, signal.packageName),
                openedAt = signal.postedAt,
                updatedAt = signal.postedAt,
                expiresAt = signal.postedAt.plus(paymentLifetime),
                due = if (dueSoon.containsMatchIn(text)) 1.0 else 0.75,
                why = listOf("bill"),
                bill = true,
            ),
        )
    }

    /** WhatsApp adds how many messages are waiting to a group's name: "Family (2 messages)". */
    private val messageCount = Regex("""\s*\(\d+ (new )?messages?\)\s*$""")

    /**
     * WhatsApp marks a group message that mentions you by giving it the sender "📣 Mentioned you" and moving
     * who wrote it to the front of the text: "Riya: @Pranav see this". Seen on the A34 (ADR 0028).
     */
    private val mentionedYou = Regex("""^[^\p{L}]*mentioned you[^\p{L}]*$""", RegexOption.IGNORE_CASE)

    private fun untag(message: Message): Message {
        if (message.sender == null || !mentionedYou.matches(message.sender)) return message
        val sender = message.text.substringBefore(": ", "").takeIf { it.isNotBlank() }
        return message.copy(sender = sender, text = if (sender != null) message.text.substringAfter(": ") else message.text, mentionsYou = true)
    }

    /** The invisible marks WhatsApp puts around a mentioned name, and the tabs and line breaks it leaves, so the chip doesn't show gaps. */
    private val isolates = Regex("""[\u2066-\u2069]""")
    private val gaps = Regex("""\s+""")

    /** WhatsApp puts "~ " before the name of someone you haven't saved. */
    private fun person(name: String): String = name.removePrefix("~").trim().ifEmpty { name }

    /** A question mark in any script; shared with the quick replies so the two agree. */
    fun hasQuestionMark(text: String): Boolean = text.any { it in QUESTION_MARKS }

    fun hasTimeWord(text: String): Boolean = timeWords.containsMatchIn(text)

    private fun isOneTimeCode(text: String): Boolean = oneTimeCode.containsMatchIn(text) && codeDigits.containsMatchIn(text)

    /** Latin, Arabic and full-width question marks. */
    private const val QUESTION_MARKS = "?؟？"

    /** Android's `Notification.CATEGORY_*` values, spelled out because the core has no Android imports. */
    private const val CATEGORY_MESSAGE = "msg"
    private const val CATEGORY_MISSED_CALL = "missed_call"
    private const val CATEGORY_EMAIL = "email"
    private const val CATEGORY_REMINDER = "reminder"
    private const val CATEGORY_EVENT = "event"

    /** Keeps the chip's age visible: long messages are cut at a word near 80 characters. */
    private fun clamp(quote: String): String {
        val text = gaps.replace(isolates.replace(quote, ""), " ").trim()
        if (text.codePointCount(0, text.length) <= MAX_QUOTE) return text
        val cut = text.offsetByCodePoints(0, MAX_QUOTE)
        val space = text.lastIndexOf(' ', cut).takeIf { it > cut / 2 } ?: cut
        return text.substring(0, space).trimEnd(' ', ',', '.') + "…"
    }

    private const val MAX_QUOTE = 80
}
