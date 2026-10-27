package com.sweepiesoftie.earmark.listener

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.core.LoopId
import com.sweepiesoftie.earmark.ui.MainActivity

/**
 * The tap and the verb on the "Earmark is on" notification while it names the held guess (ADR 0034). A notification
 * may not start an activity through a receiver, so this invisible activity takes the tap and tells the engine. While
 * it's in front, Earmark may open the way back over it; it goes as soon as that's in front.
 */
class HandBackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_LOOP)
        if (id == null || !AppGraph.listener.value.connected) {
            AppGraph.debugLog.add("held hand-back: listener not connected; opening Earmark")
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        AppGraph.uiEvents.tryEmit(Event.HeldOpened(LoopId(id)))
        // If the way back never opens, don't stay in front, invisible, eating touches.
        window.decorView.postDelayed({ if (!isFinishing) finish() }, GIVE_UP_MILLIS)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    companion object {
        const val EXTRA_LOOP = "loop"
        private const val GIVE_UP_MILLIS = 2_000L
    }
}
