package com.fardeenkhan.moodtune.wear

import android.app.Application
import com.fardeenkhan.moodtune.wear.di.wearModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** The watch app's `Application` class - starts Koin with [wearModule]. */
class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WearApplication)
            modules(wearModule)
        }
    }
}
