package com.fardeenkhan.moodtune.config.di

import com.fardeenkhan.moodtune.config.ConfigDataStore
import com.fardeenkhan.moodtune.config.ConfigRepository
import com.fardeenkhan.moodtune.config.ConnectivityChecker
import com.fardeenkhan.moodtune.config.RemoteConfigManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val configModule = module {
    single { ConfigDataStore(androidContext()) }
    single { RemoteConfigManager() }
    single { ConnectivityChecker(androidContext()) }
    single { ConfigRepository(get(), get(), get()) }
}
