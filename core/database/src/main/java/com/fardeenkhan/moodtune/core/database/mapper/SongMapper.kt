package com.fardeenkhan.moodtune.core.database.mapper

import com.fardeenkhan.moodtune.core.database.entity.SongEntity
import com.fardeenkhan.moodtune.core.database.entity.SongExplanationEntity
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation

/**
 * [lyrics] isn't part of the [Song] domain model (it's fetched/cached lazily), so this always
 * maps to null - callers re-inserting an existing song must `.copy(lyrics = ...)` the result
 * with the previously cached value first, or a re-save silently wipes cached lyrics.
 */
fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        reason = reason,
        imageUrl = imageUrl,
        externalUrl = externalUrl,
        mood = mood,
        explanation = explanation.toEntity(),
        durationMs = durationMs,
        lyrics = null,
        localAlbumArtPath = localAlbumArtPath
    )
}

fun SongExplanation.toEntity(): SongExplanationEntity {
    return SongExplanationEntity(
        about = about,
        mood = mood,
        context = context
    )
}

fun SongEntity.toDomain(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        reason = reason,
        imageUrl = imageUrl,
        externalUrl = externalUrl,
        mood = mood,
        explanation = explanation.toDomain(id),
        durationMs = durationMs,
        localAlbumArtPath = localAlbumArtPath
    )
}

fun SongExplanationEntity.toDomain(songId: String): SongExplanation {
    return SongExplanation(
        songId = songId,
        about = about,
        mood = mood,
        context = context
    )
}
