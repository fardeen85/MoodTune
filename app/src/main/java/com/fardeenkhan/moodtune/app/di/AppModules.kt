package com.fardeenkhan.moodtune.app.di

import com.fardeenkhan.moodtune.config.di.configModule
import com.fardeenkhan.moodtune.core.database.di.databaseModule
import com.fardeenkhan.moodtune.core.network.di.networkModule
import com.fardeenkhan.moodtune.core.utils.MusicPlayerManager
import com.fardeenkhan.moodtune.data.repo.PlaylistRepositoryImpl
import com.fardeenkhan.moodtune.data.repo.SongsRepositoryImpl
import com.fardeenkhan.moodtune.domain.repo.PlaylistRepository
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import com.fardeenkhan.moodtune.domain.usecase.GeneratePlaylistUseCase
import com.fardeenkhan.moodtune.domain.usecase.GetDeviceSongsUseCase
import com.fardeenkhan.moodtune.feature.home.ui.HomeViewModel
import com.fardeenkhan.moodtune.feature.home.ui.SearchViewModel
import com.fardeenkhan.moodtune.feature.playlist.ui.AllSongsViewModel
import com.fardeenkhan.moodtune.feature.playlist.ui.PlaylistViewModel
import com.fardeenkhan.moodtune.feature.songdetail.ui.SongDetailViewModel
import com.fardeenkhan.moodtune.infrastructure.datasource.GeminiAPIDataSource
import com.fardeenkhan.moodtune.infrastructure.datasource.LocalSongDataSource
import com.fardeenkhan.moodtune.infrastructure.datasource.YouTubeAPIDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    includes(databaseModule)
    includes(networkModule)
    includes(configModule)

    // Infrastructure
    single { LocalSongDataSource(androidContext()) }
    single { MusicPlayerManager(androidContext(), get()) }
    single { GeminiAPIDataSource(get(), get()) }
    single { YouTubeAPIDataSource(get(), get()) }

    // Data
    single<SongsRepository> { SongsRepositoryImpl(get(), get(), get(), get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get(), get()) }

    // Domain
    single { GetDeviceSongsUseCase(get()) }
    factoryOf(::GeneratePlaylistUseCase)

    // Feature
    viewModelOf(::PlaylistViewModel)
    viewModelOf(::AllSongsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModel { SongDetailViewModel(androidContext(), get(), get()) }
}
