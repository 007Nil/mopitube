package com.nil.mopitube

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application-level initialization can go here in the future if needed.
        // MopidyClient is now correctly managed within the UI lifecycle in AppNav.kt.
    }
}
