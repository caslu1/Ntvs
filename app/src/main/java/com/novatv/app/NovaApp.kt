package com.novatv.app

import android.app.Application
import com.novatv.app.data.Prefs

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
