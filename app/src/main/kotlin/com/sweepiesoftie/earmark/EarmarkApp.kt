package com.sweepiesoftie.earmark

import android.app.Application

class EarmarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
