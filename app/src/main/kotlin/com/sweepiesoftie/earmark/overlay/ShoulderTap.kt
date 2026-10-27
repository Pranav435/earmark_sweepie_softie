package com.sweepiesoftie.earmark.overlay

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.sweepiesoftie.earmark.DebugLog

/**
 * The pill arrives with two soft taps, like a friend's on your shoulder (ADR 0032). The chip stays silent: it shows
 * at most unlocks. It goes out as a notification vibration, so a phone on silent, or with notification vibration off,
 * stays still. Phones with rich haptics get two ticks; others a short two-pulse waveform.
 */
class ShoulderTap(context: Context, private val log: DebugLog) {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    fun tap() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect(vibrator), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect(vibrator), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
        } catch (e: RuntimeException) {
            log.error("shoulder tap", e)
        }
    }

    private fun effect(vibrator: Vibrator): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, GAP_MILLIS)
                .compose()
        } else if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(longArrayOf(0, PULSE_MILLIS, GAP_MILLIS.toLong(), PULSE_MILLIS), intArrayOf(0, 110, 0, 180), -1)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, PULSE_MILLIS, GAP_MILLIS.toLong(), PULSE_MILLIS), -1)
        }

    private companion object {
        const val GAP_MILLIS = 90
        const val PULSE_MILLIS = 22L
    }
}
