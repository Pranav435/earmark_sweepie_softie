package com.sweepiesoftie.earmark.listener

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.Playback

/**
 * Watches every active media session through the listener's own grant. A new track while
 * playing is the best signal we get for a reel boundary; it is best-effort by design.
 */
class PauseSensor(
    context: Context,
    private val listener: ComponentName,
    private val log: DebugLog,
    private val dispatch: (Event) -> Unit,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<MediaSession, MediaController.Callback>()
    private var last: Playback = Playback.None

    private data class MediaSession(val controller: MediaController) {
        override fun equals(other: Any?): Boolean = other is MediaSession && other.controller.sessionToken == controller.sessionToken
        override fun hashCode(): Int = controller.sessionToken.hashCode()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers -> track(controllers.orEmpty()) }

    fun start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            track(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            log.error("PauseSensor start", e)
        }
    }

    fun stop() {
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (e: RuntimeException) {
            log.error("PauseSensor stop", e)
        }
        callbacks.forEach { (session, callback) -> session.controller.unregisterCallback(callback) }
        callbacks.clear()
    }

    private fun track(controllers: List<MediaController>) {
        val now = controllers.map { MediaSession(it) }.toSet()
        (callbacks.keys - now).forEach { gone -> callbacks.remove(gone)?.let { gone.controller.unregisterCallback(it) } }
        (now - callbacks.keys).forEach { session ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (session.controller.playbackState?.state == PlaybackState.STATE_PLAYING) dispatch(Event.PlaybackChanged(Playback.TrackChanged))
                }

                override fun onSessionDestroyed() = publish()
            }
            session.controller.registerCallback(callback, handler)
            callbacks[session] = callback
        }
        publish()
    }

    private fun publish() {
        val states = callbacks.keys.map { it.controller.playbackState?.state }
        val playback = when {
            states.any { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING } -> Playback.Playing
            states.isEmpty() -> Playback.None
            else -> Playback.Paused
        }
        if (playback != last) {
            last = playback
            dispatch(Event.PlaybackChanged(playback))
        }
    }
}
