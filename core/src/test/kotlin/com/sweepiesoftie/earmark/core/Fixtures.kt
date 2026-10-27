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

    fun aisha(at: Instant, key: String = "0|com.google.android.apps.nbu.paisa.user|7|null|10200"): Signal = Signal(
        key = key,
        packageName = PAY,
        appLabel = "Pay",
        postedAt = at,
        title = "Aisha requested \$12.00",
        text = "Dinner at Nori",
    )
}
