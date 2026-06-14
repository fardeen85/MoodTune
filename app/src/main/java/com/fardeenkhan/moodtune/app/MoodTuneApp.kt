package com.fardeenkhan.moodtune.app

import android.app.Application
import com.fardeenkhan.moodtune.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MoodTuneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@MoodTuneApp)
            modules(appModule)
        }
    }
}
