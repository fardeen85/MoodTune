package com.fardeenkhan.moodtune.core.database.mapper

import com.fardeenkhan.moodtune.core.database.entity.PlaylistEntity
import com.fardeenkhan.moodtune.core.database.entity.PlaylistWithSongs
import com.fardeenkhan.moodtune.core.database.entity.SongEntity
import com.fardeenkhan.moodtune.domain.model.Playlist

fun Playlist.toEntity(): PlaylistEntity {
    return PlaylistEntity(
        id = id,
        mood = mood,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
    )
}

fun PlaylistWithSongs.toDomain(): Playlist {
    return Playlist(
        id = playlist.id,
        mood = playlist.mood,
        songs = songs.map { it.toDomain() },
        createdAt = playlist.createdAt,
        lastPlayedAt = playlist.lastPlayedAt
    )
}

// Both functions below map a raw LEFT JOIN result (playlist with zero or more songs). Room's
// generated DAO code already omits an entry for a playlist row whose joined song columns are
// all null (empty playlist case), so every SongEntity reaching here is a genuine song - no
// extra null-filtering needed.

fun Map<PlaylistEntity, List<SongEntity>>.toDomainList(): List<Playlist> {
    return this.map { (playlistEntity, songs) ->
        Playlist(
            id = playlistEntity.id,
            mood = playlistEntity.mood,
            songs = songs.map { it.toDomain() },
            createdAt = playlistEntity.createdAt,
            lastPlayedAt = playlistEntity.lastPlayedAt
        )
    }
}

fun Map<PlaylistEntity, List<SongEntity>>.toDomainSingle(): Playlist? {
    return this.entries.firstOrNull()?.let { (playlistEntity, songs) ->
        Playlist(
            id = playlistEntity.id,
            mood = playlistEntity.mood,
            songs = songs.map { it.toDomain() },
            createdAt = playlistEntity.createdAt,
            lastPlayedAt = playlistEntity.lastPlayedAt
        )
    }
}
