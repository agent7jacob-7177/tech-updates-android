package com.code7.techupdates

import android.app.Application
import androidx.annotation.VisibleForTesting

class TechUpdatesApp : Application() {
    lateinit var container: AppContainer
        @VisibleForTesting internal set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
