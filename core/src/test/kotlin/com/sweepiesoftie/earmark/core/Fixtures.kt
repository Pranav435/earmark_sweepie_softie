package com.sweepiesoftie.earmark.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Shared fixtures that mirror the demo stories. Tuesday 13 October 2026, as in the demo videos. */
object Fixtures {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    const val OWN = "com.sweepiesoftie.earmark"
    const val CHATS = "com.whatsapp"
    const val PAY = "com.google.android.apps.nbu.paisa.user"
    const val FEED = "com.instagram.android"
    const val RIDE = "com.ubercab"
    const val CLOCK = "com.google.android.deskclock"

    fun at(hour: Int, minute: Int, second: Int = 0, day: Int = 13): Instant =
        ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, zone).toInstant()

    val settings: Settings = Settings(ownPackage = OWN, zone = zone, clockPackage = CLOCK)

    fun rahul(at: Instant, text: String = "free at 5?", key: String = "0|com.whatsapp|1|rahul|10123"): Signal = Signal(
        key = key,
        packageName = CHATS,
        appLabel = "Chats",
        postedAt = at,
        title = "Rahul",
        text = text,
        messages = listOf(
            Message("Rahul", "did you see the match??", at.minusSeconds(70_000)),
            Message(null, "that last over was unreal", at.minusSeconds(69_000), fromUser = true),
            Message("Rahul", text, at),
        ),
        conversationKey = "hash-rahul",
        hasReplyAction = true,
        category = "msg",
    )

    fun priya(at: Instant, text: String = "hey", key: String = "0|com.whatsapp|2|priya|10123"): Signal = Signal(
        key = key,
        packageName = CHATS,
        appLabel = "Chats",
        postedAt = at,
        title = "Priya",
        text = text,
        messages = listOf(Message("Priya", text, at)),
        conversationKey = "hash-priya",
        hasReplyAction = true,
        category = "msg",
    )

    /** A WhatsApp group: each pair is a sender and their message, a minute apart, ending at [at]. */
    fun family(at: Instant, vararg messages: Pair<String, String>, key: String = "0|com.whatsapp|9|family|10123"): Signal = Signal(
        key = key,
        packageName = CHATS,
        appLabel = "Chats",
        postedAt = at,
        title = "Family",
        text = messages.last().second,
        messages = messages.mapIndexed { i, (sender, text) -> Message(sender, text, at.minusSeconds(60L * (messages.size - 1 - i))) },
        conversationKey = "hash-family",
        isGroupConversation = true,
        conversationTitle = "Family",
        hasReplyAction = true,
        category = "msg",
    )

    /** An Instagram DM: Instagram is a feed app as well as where the chat lives. */
    fun zoya(at: Instant, text: String = "hey", key: String = "0|com.instagram.android|5|zoya|10123"): Signal = Signal(
        key = key,
        packageName = FEED,
        appLabel = "Instagram",
        postedAt = at,
        title = "Zoya",
        text = text,
        messages = listOf(Message("Zoya", text, at)),
        conversationKey = "hash-zoya",
        hasReplyAction = true,
        category = "msg",
    )

    fun aisha(at: Instant, key: String = "0|com.google.android.apps.nbu.paisa.user|7|null|10200"): Signal = Signal(
        key = key,
        packageName = PAY,
        appLabel = "Pay",
        postedAt = at,
        title = "Aisha requested \$12.00",
        text = "Dinner at Nori",
    )

    const val DIALER = "com.google.android.dialer"
    const val MAIL = "com.microsoft.office.outlook"
    const val KEEP = "com.google.android.keep"
    const val SMS = "com.google.android.apps.messaging"

    /** Google's dialer, as the API 36 emulator posts it: a MessagingStyle "Missed call" from the caller, in the missed_call category. */
    fun missedCall(at: Instant, from: String = "Mom", key: String = "0|com.google.android.dialer|1|MissedCall_content://call_log/calls/1|10142"): Signal = Signal(
        key = key,
        packageName = DIALER,
        appLabel = "Phone",
        postedAt = at,
        title = from,
        text = "Missed call",
        messages = listOf(Message(from, "Missed call", at)),
        conversationKey = "hash-call-${from.lowercase()}",
        category = "missed_call",
    )

    /** An Outlook email, as the A34 posts it: big text, the sender as the title, and the subject on the text's first line. */
    fun email(at: Instant, from: String = "Prof. Mehta", subject: String = "Lab report", body: String = "Can you send it to me by Friday?"): Signal = Signal(
        key = "0|com.microsoft.office.outlook|2|mehta|10298",
        packageName = MAIL,
        appLabel = "Outlook",
        postedAt = at,
        title = from,
        text = "$subject\n$body",
        bigText = "$subject\n$body",
        conversationKey = "hash-mail-mehta",
        category = "email",
    )

    fun reminder(at: Instant, title: String = "Submit lab report", text: String? = null, category: String? = "reminder"): Signal = Signal(
        key = "0|com.google.android.keep|4|null|10300",
        packageName = KEEP,
        appLabel = "Keep",
        postedAt = at,
        title = title,
        text = text,
        conversationKey = "hash-keep-4",
        category = category,
    )

    /** A text from a business, which comes from a sender ID such as AX-AIRTEL rather than a person or a number. */
    fun airtel(at: Instant, text: String = "Your Airtel postpaid bill of Rs 499 is due on 30-Oct. Pay now at airtel.in/pay"): Signal = Signal(
        key = "0|com.google.android.apps.messaging|7|airtel|10200",
        packageName = SMS,
        appLabel = "Messages",
        postedAt = at,
        title = "AX-AIRTEL",
        text = text,
        messages = listOf(Message("AX-AIRTEL", text, at)),
        conversationKey = "hash-airtel",
        category = "msg",
    )
}
