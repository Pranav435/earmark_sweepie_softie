package com.sweepiesoftie.earmark.listener

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.ListenerState
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * "Earmark this" in Quick Settings (ADR 0035). A tap holds the app in front as an Own loop, so if you drift into a
 * feed the pill brings you back to it; tapped again, it lets go. It's a toggle, so TalkBack says on or off, and its
 * subtitle names the app it holds or would hold, from what the engine last published.
 */
class EarmarkTile : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        scope?.cancel()
        scope = MainScope().also { it.launch { AppGraph.listener.collect(::render) } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        AppGraph.uiEvents.tryEmit(Event.Earmarked)
    }

    private fun render(listener: ListenerState) {
        val tile = qsTile ?: return
        val holding = listener.tile.holding
        val canHold = listener.tile.canHold
        val feed = listener.tile.feed
        val ignored = listener.tile.ignored
        val (state, subtitle) = when {
            !listener.connected -> Tile.STATE_UNAVAILABLE to getString(R.string.tile_off)
            holding != null -> Tile.STATE_ACTIVE to holding
            canHold != null -> Tile.STATE_INACTIVE to canHold
            // Greyed out, so say why: it won't hold a feed (ADR 0037).
            feed != null -> Tile.STATE_UNAVAILABLE to getString(R.string.tile_feed, feed)
            ignored != null -> Tile.STATE_UNAVAILABLE to getString(R.string.tile_ignored, ignored)
            else -> Tile.STATE_UNAVAILABLE to getString(R.string.tile_nothing)
        }
        tile.state = state
        tile.label = getString(R.string.tile_label)
        tile.subtitle = subtitle
        tile.updateTile()
    }
}
