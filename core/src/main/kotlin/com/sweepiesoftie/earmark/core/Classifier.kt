package com.sweepiesoftie.earmark.core

import java.time.Duration

internal sealed interface Classification {
    data class Open(val loop: Loop) : Classification
    /** You answered this conversation yourself, so its Reply loop is done. */
    data class Replied(val conversation: String) : Classification
    /** A ride, order or checkout went through in this app. */
    data class Booked(val packageName: String) : Classification
    /** A payment went through in this app, for [amount] when it says. */
    data class Paid(val packageName: String, val amount: Money? = null) : Classification
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
    private const val CODE_WORD = """\b(otp|one[- ]time|verification|security code|passcode|login code|your code|code is)\b"""

    /** 4–8 digits on their own, not part of a date or an amount. "G-482913" and "OTP:482913" are codes; "31-10-2026" isn't. */
    private const val CODE = """(?<![₹\$]\s?|(?:rs|inr)\.?\s?|\d[/.,-])\b\d{4,8}\b(?![/.,-]\d)"""
    private val codeBeside = Regex("""(?i)$CODE_WORD[^\n]{0,40}?$CODE|$CODE[^\n]{0,40}?$CODE_WORD""")
    private val timeWords = Regex(
        """(?i)\b(at \d{1,2}(:\d{2})?|\d{1,2}:\d{2}|\d{1,2}(:\d{2})?\s?(am|pm)|tonight|today|tomorrow|now|asap|soon|this (morning|afternoon|evening)|in \d+ ?(min|mins|minutes|hours?|h))\b""",
    )
    private val requested = Regex(
        """(?i)\b(requested|requests|requesting|request(s|ed)? (for|of)|asked you for|is asking for|sent you a request|payment request|money request|collect request|reminder to pay)\b""",
    )
    private val requester = Regex("""(?i)^(.+?)\s+(?:has\s+)?(?:requested|requests|is requesting|sent you a request|sent you a reminder|asked you for|is asking for)""")
    private val requestFrom = Regex("""(?i)request(?: (?:of|for) \S+)? from (.+?)[.!]?$""")
    private val requestedBy = Regex("""(?i)requested by (.+?)(?: before| on| for| via|[.!]|$)""")
    /** Lines that say nothing about what the money was for. */
    private val boilerplate = Regex("""(?i)^(tap to .*|payment request( from .*)?|payment reminder|venmo|paypal|google pay|gpay|phonepe|paytm|cash app)$""")

    /** A ride, order or booking went through: placed, confirmed, accepted, a driver on the way. Delivery tracking isn't one. */
    private val booked = Regex(
        """(?i)\b(ride booked|booked|booking (is )?(confirmed|successful)|(ride|order|booking|trip|reservation|ticket|table)s?\b[^.!\n]{0,40}\b(is |are |has been |have been )?(confirmed|placed|accepted|scheduled)|""" +
            """order (placed|confirmed|confirm|accepted|received)|ordered|on the way|arriving in|is \d+ min(ute)?s? away|driver (assigned|is)|""" +
            """(captain|driver|rider|partner) (assigned|found|mil gaya|is arriving)|confirm(ed)? ho gaya|accept kar liya)\b""",
    )

    /** You paid or sent money: an app's receipt, or a bank's debit text ("Sent Rs.450 … To ISHAAN", "debited … trf to MUMMY"). */
    private val paid = Regex(
        """(?i)\b(paid to|paid\s?(₹|rs\.?|inr|\$|€|£)\s?[\d,.]+ to|payment (successful|sent|complete|completed|done)|you paid|you sent|money sent|""" +
            """transfer(red)? successful(ly)?|(sent|transferred)\s+(₹|rs\.?|inr|\$|€|£|usd)\s?[\d,.]+[^.\n]{0,60}\bto\b|debited\b[^\n]{0,80}\b(credited|trf to|to vpa|upi)\b)""",
    )

    /** A payment that didn't go through is still owed: a failed charge, a bounced EMI, a trip that couldn't be charged. "Pending" is still going through. */
    private val failedPayment = Regex(
        """(?i)\b((payment|emi|charge|auto-?debit|autopay|mandate|subscription|renewal|instalment|installment)\b[^.\n]{0,30}\b(failed|declined|unsuccessful|bounced|not debited)|""" +
            """could ?(n['’]?t|not) (be )?(process|charge|debit|collect)\w*|insufficient (balance|funds))\b""",
    )

    fun classify(signal: Signal, settings: Settings, stats: Stats = Stats()): Classification {
        if (signal.isOwn && !settings.demoMode) return Classification.Skip("own notification")
        if (signal.isOngoing) return Classification.Skip("ongoing")
        if (signal.isGroupSummary) return Classification.Skip("group summary")

        val body = listOfNotNull(signal.title, signal.text).joinToString(" ")
        val lastMessage = signal.messages.lastOrNull()
        // A ride app's confirmation carries the code you tell the driver, so it is a booking, not a login code.
        val bookedApp = lastMessage == null && signal.category !in notApps && !isReminder(signal) &&
            booked.containsMatchIn(body) && !failedPayment.containsMatchIn(body)
        if (bookedApp && !paid.containsMatchIn(body)) return Classification.Booked(signal.packageName)
        if (lastMessage == null && isOneTimeCode(body)) return Classification.Skip("one-time code")

        // Dialers post a missed call as a chat from the caller, so it is told apart before messages are (ADR 0029).
        if (isMissedCall(signal)) return callBack(signal)
        if (signal.category == CATEGORY_EMAIL) return classifyEmail(signal)
        if (isReminder(signal)) return reminder(signal)
        if (lastMessage != null || signal.category == CATEGORY_MESSAGE) return classifyMessage(signal, settings, stats)

        val details = listOfNotNull(signal.title, signal.bigText ?: signal.text).joinToString(" ")
        val owed = MoneyParser.find(details)?.takeIf { it.amount.signum() > 0 }
        if (owed != null && failedPayment.containsMatchIn(details)) return openBill(signal, owed, details, biller = signal.appLabel)
        if (paid.containsMatchIn(body)) return Classification.Paid(signal.packageName, MoneyParser.find(body))
        if (bookedApp) return Classification.Booked(signal.packageName)
        val money = MoneyParser.find(body)
        if (money != null && requested.containsMatchIn(body)) return openPayment(signal, money)
        if (owed != null && isBillDue(details)) return openBill(signal, owed, details, biller = signal.appLabel)
        return Classification.Skip("not a loop")
    }

    private fun classifyMessage(signal: Signal, settings: Settings, stats: Stats): Classification {
        val messages = signal.messages.map(::untag)
        val last = messages.lastOrNull()
        val text = last?.text ?: signal.text.orEmpty()
        if (isOneTimeCode(text)) return Classification.Skip("one-time code")
        if (last?.isBot == true) {
            // Slackbot and assistants deliver the reminders you set yourself: "Reminder: submit timesheet."
            val what = botReminder.find(text.trim())?.groupValues?.get(1) ?: return Classification.Skip("bot")
            return reminder(signal, what)
        }
        val conversation = signal.conversationKey ?: signal.key
        if (last?.fromUser == true) return Classification.Replied(conversation)
        val sender = (last?.sender ?: signal.title)?.trim()
        if (sender != null && businessSender.matches(sender)) return fromBusiness(signal, text, sender)
        // A business account on WhatsApp writes like one: "Reply 'Menu' to see more options".
        if (sender != null && menuPrompt.containsMatchIn(text)) return fromBusiness(signal, text, sender)
        if (sender != null && phoneNumber.matches(sender) && scam.containsMatchIn(text)) return Classification.Skip("looks like a scam")
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
            detail = "“${Gist.of(quote.text)}”",
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
        val who = requestedBy.find(title)?.groupValues?.get(1)?.trim()
            ?: requestedBy.find(text)?.groupValues?.get(1)?.trim()
            ?: requester.find(title)?.groupValues?.get(1)?.trim()
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
                amount = money,
            ),
        )
    }

    /**
     * "Missed call", "Missed voice call", "2 missed calls" or "Missed call from Mom", as a whole line, so a chat
     * message such as "sorry I missed your call" stays a message.
     */
    private val missedCall = Regex("""(?i)^[^\p{L}\p{N}]*(\d+\s+)?missed\s+(group\s+)?(voice\s+|video\s+)?calls?\b""")
    private val callerIn = Regex("""(?i)missed\s+(?:group\s+)?(?:voice\s+|video\s+)?calls?\s+from\s+(.+)$""")

    /** A carrier's text about calls missed while the phone was off: "Missed Call Alert: 98200 55617 tried to call you 2 times". */
    private val triedToCall = Regex("""(?i)(?:missed call alert\s*:\s*)?(\+?[\w][\w .()-]{2,40}?)\s+(?:tried to call|called) you""")

    /** The dialer or Truecaller already says it's spam. */
    private val spamCaller = Regex("""(?i)\b(spam|scam|fraud|telemarket\w*|suspected|robocall|likely (spam|fraud))\b""")
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
        if (lines.any { spamCaller.containsMatchIn(it) }) return Classification.Skip("spam call")
        val texted = signal.messages.lastOrNull()?.text?.let { triedToCall.find(it)?.groupValues?.get(1) }
        val named = texted
            ?: signal.messages.lastOrNull { !it.fromUser }?.sender
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
    private val automated = Regex(
        """(?i)\b(no[-_. ]?reply|do[-_. ]?not[-_. ]?reply|notifications?|newsletters?|digest|alerts?|mailer|support|team|updates?|calendar|billing|receipts?|orders?|jobs|careers|offers|deals)\b""",
    )

    /** "Aisha Khan (Google Docs)", "Rahul via LinkedIn": a service writing on a person's behalf. */
    private val onBehalf = Regex("""(?i)\(\s*(via\s+)?[^)]+\)\s*$|\bvia\s+\S+""")

    /** Subjects that a calendar, a code host or a shared document writes. */
    private val notice = Regex("""(?i)^((updated )?invitation|accepted|declined|tentative|cancell?ed( event)?|new event)\s*:|^\[[^\]]+]""")

    /** Words that only newsletters, offers and cold sales emails use. */
    private val marketing = Regex(
        """(?i)\b(unsubscribe|view (this|it) in (your )?browser|webinar|masterclass|register (now|today|here)|seats are (filling|limited)|limited[- ]time|""" +
            """% off|cashback|coupon|promo code|free trial|enrol(l)?( now)?\b|(book|schedule) a (demo|call)|\d+[- ]?min(ute)? (call|chat)|open to a (quick )?(call|chat)|""" +
            """could help (you|your)|auto-?generated|last chance|your cart|shop now|don'?t miss|deal of the day|offer (ends|valid)|(please )?do not reply|mentioned you in a comment|commented on|shared an? (doc|document|file|folder|sheet)|invited you to)""",
    )

    /** Where an email stops being the sender's own words: a quoted reply or a forwarded message. */
    private val quotedMail = Regex("""(?m)^\s*(-{2,}\s*forwarded message|on .{5,120}wrote:|>|from:\s)""", RegexOption.IGNORE_CASE)

    /**
     * An email is a loop when it asks you something or is urgent, by the same reading as a message (ADR 0027), or
     * when it is a bill. A no-reply sender, a service writing for someone, a calendar invitation or a newsletter is
     * not someone waiting on you, so only its bill counts. Quoted replies and forwarded mail are someone else's words.
     */
    private fun classifyEmail(signal: Signal): Classification {
        val sender = signal.title?.let(::person)?.takeIf { it.isNotEmpty() } ?: return Classification.Skip("no sender")
        val whole = signal.bigText ?: signal.text.orEmpty()
        val text = quotedMail.find(whole)?.let { whole.substring(0, it.range.first) }?.takeIf { it.isNotBlank() } ?: whole
        // Gmail and Outlook put the subject on the text's first line.
        val subject = signal.text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: text
        if (isOneTimeCode(text)) return Classification.Skip("one-time code")
        val owed = MoneyParser.find(text)?.takeIf { it.amount.signum() > 0 }
        if (owed != null && (isBillDue(text) || failedPayment.containsMatchIn(text))) return openBill(signal, owed, text, biller = sender)
        if (booked.containsMatchIn(subject) && !hasQuestionMark(subject)) return Classification.Booked(signal.packageName)
        if (automated.containsMatchIn(sender) || onBehalf.containsMatchIn(sender)) return Classification.Skip("automated email")
        if (notice.containsMatchIn(subject) || marketing.containsMatchIn(text)) return Classification.Skip("automated email")
        val reading = Urgency.read(listOf(Message(sender, text, signal.postedAt)), inGroup = false)
        if (!reading.urgent && !Urgency.asks(text)) return Classification.Skip("email asks nothing")
        return Classification.Open(
            Loop(
                id = LoopId("email:${signal.conversationKey ?: signal.key}"),
                kind = LoopKind.Email,
                task = "Reply to $sender's email",
                who = sender,
                detail = "“${Gist.of(subject)}”",
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

    /** "Reminder: submit timesheet." from Slackbot or an assistant. */
    private val botReminder = Regex("""(?i)^(?:reminder|remind me)\s*:\s*(.+)$""")

    /** A reminder you set goes off when you meant to do it, so it is fully due as it fires; its words are the task. */
    private fun reminder(signal: Signal, said: String? = null): Classification {
        val what = (listOfNotNull(said) + listOfNotNull(signal.title, signal.text)).map { gaps.replace(it, " ").trim().trimEnd('.') }
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
                why = listOf(if (signal.category == CATEGORY_EVENT) "event" else "reminder"),
            ),
        )
    }

    /** An Indian sender ID such as AX-AIRTEL or VM-HDFCBK-S, or a short code such as 56767. */
    private val businessSender = Regex("""^([A-Z]{2}-[A-Z0-9]{3,9}(-[A-Z])?|\d{3,6})$""")

    /**
     * A text from a business is never a reply (ADR 0029). It can be a bill that is due, or a failed payment, which is
     * still owed; or a bank's debit text, which says you paid; or an order going through.
     */
    private fun fromBusiness(signal: Signal, text: String, sender: String): Classification {
        val name = sender.replace(senderPrefix, "").replace(senderSuffix, "").takeUnless { it.all(Char::isDigit) }
        val money = MoneyParser.find(text)?.takeIf { it.amount.signum() > 0 }
        if (money != null && (isBillDue(text) || failedPayment.containsMatchIn(text))) return openBill(signal, money, text, biller = name)
        if (paid.containsMatchIn(text)) return Classification.Paid(signal.packageName, MoneyParser.find(text))
        if (booked.containsMatchIn(text) && !isOneTimeCode(text)) return Classification.Booked(signal.packageName)
        return Classification.Skip("text from a business")
    }

    private val senderPrefix = Regex("""^[A-Z]{2}-""")
    private val senderSuffix = Regex("""-[A-Z]$""")

    /** "Reply 'Menu'", "type HI": a business account's bot, not a person. */
    private val menuPrompt = Regex("""(?i)\b(reply|type|send)\s+["'‘“]?(menu|stop|hi|help|yes|no|\d)["'’”]?\s+(to|for)\b""")

    /** A sender with no name, only a number: nobody you've saved. */
    private val phoneNumber = Regex("""^\+?\(?\d[\d\s().-]{6,}\d$""")

    /** What phishing texts from unknown numbers say: an account blocked, a KYC to update, a prize, a link to click. */
    private val scam = Regex(
        """(?i)\b(dear (customer|consumer|user|valued)|kyc|pan card|(account|connection|power|electricity|sim|card|number|yono)\b[^.\n]{0,40}\b(blocked|suspended|disconnected|deactivated|expired?|frozen)|""" +
            """lottery|you have won|prize|click (here|the link|below)|verify (your )?(account|details|identity))\b""",
    )

    private val billNoun = Regex(
        """(?i)(?<![\p{L}])(bills?|statement|emi|premium|dues|rent|invoice|fees?|maintenance|credit card|instal(l)?ment|tuition|बिल|बील|प्रीमियम|शुल्क|किस्त|બિલ|বিল)(?![\p{L}\p{M}])""",
    )
    private val billDue = Regex(
        """(?i)(?<![\p{L}])(is due|are due|due (on|by|date|today|tomorrow|in|hai|h)|tak due|overdue|pay by|pay before|last date|payment due|amount due|total due|""" +
            """min(imum)? (amount )?due|देय|बकाया|भुगतान करें|ભરવાનું|বকেয়া)(?![\p{L}\p{M}])""",
    )

    /** A receipt for a bill also says when it was due. "Ignore if already paid" doesn't count: the bill may still be owed. */
    private val settled = Regex(
        """(?i)\b(received|successful(ly)?|thank(s| you) for (your )?pay(ment|ing)|has been paid|is paid|you paid|you have paid|no (payment|amount) (is )?(required|due))\b""",
    )
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
                amount = money,
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

    fun isOneTimeCode(text: String): Boolean = oneTimeCode.containsMatchIn(text) && codeDigits.containsMatchIn(text)

    /**
     * Stricter, for custom triggers: the word for a code beside its digits, on one line. The year or amount in "KYC
     * verification due by 31-10-2026" isn't one, so a trigger on "KYC" still fires.
     */
    fun quotesOneTimeCode(text: String): Boolean = codeBeside.containsMatchIn(text)

    /** Latin, Arabic and full-width question marks. */
    private const val QUESTION_MARKS = "?؟？"

    /** Android's `Notification.CATEGORY_*` values, spelled out because the core has no Android imports. */
    private const val CATEGORY_MESSAGE = "msg"
    private const val CATEGORY_MISSED_CALL = "missed_call"
    private const val CATEGORY_EMAIL = "email"
    private const val CATEGORY_REMINDER = "reminder"
    private const val CATEGORY_EVENT = "event"

    /** Categories whose notifications aren't an app reporting on a ride, order or payment. */
    private val notApps = setOf(CATEGORY_EMAIL, CATEGORY_MESSAGE, CATEGORY_MISSED_CALL, CATEGORY_REMINDER, CATEGORY_EVENT)

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
