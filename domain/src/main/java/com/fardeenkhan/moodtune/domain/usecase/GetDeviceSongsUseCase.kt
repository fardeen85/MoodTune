package com.fardeenkhan.moodtune.domain.usecase

import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.repo.SongsRepository
import kotlinx.coroutines.flow.Flow

class GetDeviceSongsUseCase(private val songsRepository: SongsRepository) {
    operator fun invoke(): Flow<List<Song>> {
        return songsRepository.getDeviceFile()
    }
}
