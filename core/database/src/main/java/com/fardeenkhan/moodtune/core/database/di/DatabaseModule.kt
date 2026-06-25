package com.fardeenkhan.moodtune.core.database.di

import androidx.room.Room
import com.fardeenkhan.moodtune.core.database.MoodTuneDatabase
import com.fardeenkhan.moodtune.core.database.di.databaseModule

import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import com.fardeenkhan.moodtune.domain.usecase.GetDeviceSongsUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            MoodTuneDatabase::class.java,
            "moodtune_database"
        ).addMigrations(MoodTuneDatabase.MIGRATION_2_3, MoodTuneDatabase.MIGRATION_3_4, MoodTuneDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration().build()
    }

    single { get<MoodTuneDatabase>().songDao() }
    single { get<MoodTuneDatabase>().playlistDao() }
}
