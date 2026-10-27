package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant

internal enum class Phase { Idle, Guessed, InFeed, Drifting, PillShown, HandedBack, Left, Dismissed }

/**
 * Why the guess is the guess, which the pill says: you came for it ("You came here to"), it came in while you
 * were using the phone ("Before you forget", ADR 0020), or it was waiting after one you dealt with ("Next up", ADR 0027).
 */
internal enum class Origin { Unlock, Arrival, NextUp }

/** The drift state for one unlock session. */
internal data class Drift(
    val phase: Phase = Phase.Idle,
    val loopId: LoopId? = null,
    val loopPackage: String? = null,
    val origin: Origin = Origin.Unlock,
    val feedSince: Instant? = null,
    val playback: Playback = Playback.None,
    val blockers: Set<Blocker> = emptySet(),
    /** The guess this one took over for a detour, such as a chat opened from its own notification. It comes back after. */
    val resume: Drift? = null,
)

internal sealed interface DriftInput {
    data class Foreground(val packageName: String) : DriftInput
    data class Media(val playback: Playback) : DriftInput
    data class Block(val blocker: Blocker, val active: Boolean) : DriftInput
    data object Tick : DriftInput
}

/** What the engine knows that the machine needs: which apps are feeds, how long is drift, why this loop may not get a pill. */
internal data class DriftRules(
    val feedApps: Set<String>,
    val driftAfter: Duration,
    /** Why the guess can't have a pill now, for DebugLog, or null when it can. */
    val pillHeld: String? = null,
)

internal data class DriftStep(val drift: Drift, val showPill: Boolean = false, val reason: String? = null)

/**
 * Idle → Guessed → InFeed → Drifting → PillShown → HandedBack | Dismissed.
 * The pill waits for the first pause after drift starts; blockers and playing media hold it back.
 * The engine moves HandedBack to Left when you leave the loop's app with its chat not open.
 */
internal object DriftMachine {
    fun guess(drift: Drift, loopId: LoopId, loopPackage: String?, origin: Origin = Origin.Unlock): Drift =
        drift.copy(phase = Phase.Guessed, loopId = loopId, loopPackage = loopPackage, origin = origin, feedSince = null, resume = null)

    fun end(drift: Drift): Drift = Drift(playback = drift.playback, blockers = drift.blockers)

    /** Back to [previous] after a detour. Drift starts over, but a pill already shown or swiped away stays that way. */
    fun resume(drift: Drift, previous: Drift): Drift = previous.copy(
        phase = if (previous.phase in setOf(Phase.InFeed, Phase.Drifting, Phase.PillShown)) Phase.Guessed else previous.phase,
        feedSince = null,
        playback = drift.playback,
        blockers = drift.blockers,
        resume = null,
    )

    fun step(drift: Drift, input: DriftInput, now: Instant, rules: DriftRules): DriftStep {
        val d = when (input) {
            is DriftInput.Media -> drift.copy(playback = if (input.playback == Playback.TrackChanged) Playback.Playing else input.playback)
            is DriftInput.Block -> drift.copy(blockers = if (input.active) drift.blockers + input.blocker else drift.blockers - input.blocker)
            else -> drift
        }
        fun isFeed(pkg: String) = pkg in rules.feedApps && pkg != d.loopPackage

        return when (d.phase) {
            Phase.Guessed -> when {
                input is DriftInput.Foreground && isFeed(input.packageName) -> DriftStep(d.copy(phase = Phase.InFeed, feedSince = now))
                else -> DriftStep(d)
            }
            Phase.InFeed -> when (input) {
                is DriftInput.Foreground ->
                    if (isFeed(input.packageName)) DriftStep(d) else DriftStep(d.copy(phase = Phase.Guessed, feedSince = null))
                is DriftInput.Tick -> {
                    val since = d.feedSince ?: now
                    if (Duration.between(since, now) < rules.driftAfter) {
                        DriftStep(d)
                    } else if (d.playback == Playback.Playing) {
                        DriftStep(d.copy(phase = Phase.Drifting), reason = "drifting; waiting for a pause")
                    } else {
                        // Nothing is playing, so there is no pause to wait for (ADR 0005).
                        tryPill(d.copy(phase = Phase.Drifting), rules, "drift with nothing playing")
                    }
                }
                else -> DriftStep(d)
            }
            Phase.Drifting -> when (input) {
                is DriftInput.Foreground -> when {
                    input.packageName == d.loopPackage -> DriftStep(d.copy(phase = Phase.Guessed, feedSince = null), reason = "came back on their own")
                    else -> tryPill(d, rules, "app switch", pause = true)
                }
                is DriftInput.Media ->
                    if (input.playback == Playback.Playing) DriftStep(d) else tryPill(d, rules, "media ${input.playback}", pause = true)
                is DriftInput.Block ->
                    if (!input.active && d.playback != Playback.Playing) tryPill(d, rules, "blocker cleared") else DriftStep(d)
                is DriftInput.Tick ->
                    if (d.playback != Playback.Playing) tryPill(d, rules, "still drifting", quiet = true) else DriftStep(d)
            }
            else -> DriftStep(d)
        }
    }

    private fun tryPill(d: Drift, rules: DriftRules, why: String, pause: Boolean = false, quiet: Boolean = false): DriftStep {
        val held = when {
            d.blockers.isNotEmpty() -> "held: ${d.blockers.joinToString()}"
            !pause && d.playback == Playback.Playing -> "held: media playing"
            rules.pillHeld != null -> "held: ${rules.pillHeld}"
            else -> null
        }
        if (held != null) return DriftStep(d, reason = if (quiet) null else "$why, $held")
        return DriftStep(d.copy(phase = Phase.PillShown), showPill = true, reason = "pill at $why")
    }
}
