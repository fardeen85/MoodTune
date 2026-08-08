package com.fardeenkhan.moodtune.core.database.di

import androidx.room.Room
import com.fardeenkhan.moodtune.core.database.MoodTuneDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            MoodTuneDatabase::class.java,
            "moodtune_database"
        ).addMigrations(
            MoodTuneDatabase.MIGRATION_2_3,
            MoodTuneDatabase.MIGRATION_3_4,
            MoodTuneDatabase.MIGRATION_4_5,
            MoodTuneDatabase.MIGRATION_5_6
        )
            // NOTE: any DB version bump without a matching Migration silently wipes all local
            // data (playlists, cached songs, lyrics) instead of crashing. Intentional for now
            // since there's no user data worth preserving across an unmigrated schema break,
            // but revisit this if that stops being true.
            .fallbackToDestructiveMigration().build()
    }

    single { get<MoodTuneDatabase>().songDao() }
    single { get<MoodTuneDatabase>().playlistDao() }
}
