package com.sweepiesoftie.earmark.core

import java.time.Instant

/** A loop plus what has happened to it since it opened. */
internal data class Tracked(
    val loop: Loop,
    /** When the loop became the guess: a chip, or a flow adopted silently. Non-null makes it a thing on the recap. */
    val guessedAt: Instant? = null,
    val pillShown: Boolean = false,
    /** The pill was swiped away; offer it again as a chip at the next unlock. */
    val carryOver: Boolean = false,
    /** A pill or carry-over chip handed it back, so closing it counts as "brought back". */
    val handedBack: Boolean = false,
    /** The chip was swiped this session: don't track drift for it until the next unlock. */
    val ignored: Boolean = false,
    /** Its app cleared the notification because you opened the chat; it closes when you reply or leave. */
    val chatOpen: Boolean = false,
)

/** The open loops. Opening a loop with an existing id updates its evidence but keeps its history. */
internal class LoopBook {
    private val loops = LinkedHashMap<LoopId, Tracked>()

    val all: Collection<Tracked> get() = loops.values

    operator fun get(id: LoopId?): Tracked? = id?.let { loops[it] }

    fun open(loop: Loop): Tracked {
        val tracked = loops[loop.id]?.copy(loop = loop.copy(openedAt = loops.getValue(loop.id).loop.openedAt, started = loops.getValue(loop.id).loop.started))
            ?: Tracked(loop)
        loops[loop.id] = tracked
        return tracked
    }

    fun update(id: LoopId, change: (Tracked) -> Tracked): Tracked? = loops[id]?.let(change)?.also { loops[id] = it }

    fun remove(id: LoopId): Tracked? = loops.remove(id)

    fun byNotificationKey(key: String): Tracked? = loops.values.firstOrNull { it.loop.notificationKey == key }

    fun byConversation(conversation: String): Tracked? =
        loops.values.firstOrNull { it.loop.kind == LoopKind.Reply && it.loop.conversation == conversation }

    fun expired(now: Instant): List<Tracked> = loops.values.filter { !it.loop.expiresAt.isAfter(now) }
}
