package com.fardeenkhan.moodtune.wear.di

import org.koin.dsl.module

/**
 * Central Koin wiring for the watch app - mirrors the phone's `app/di/AppModules.kt` pattern.
 *
 * TODO(§2.4): register `WearDownloadStore`, `LocalBackend`, `RemoteMirrorBackend`,
 * `WearPlaybackController`, and the screens' view models here as they're implemented.
 *
 * See `implementation/WearPlan.md` §5.
 */
val wearModule = module {
}
