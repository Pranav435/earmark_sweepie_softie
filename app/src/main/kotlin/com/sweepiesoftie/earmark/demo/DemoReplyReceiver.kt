package com.sweepiesoftie.earmark.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/** Receives a reply sent through the demo notification's RemoteInput, as a chat app would. */
class DemoReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(DemoNotifier.KEY_REPLY)?.toString() ?: return
        DemoNotifier.reply(context, text)
    }
}
