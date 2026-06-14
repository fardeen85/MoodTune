package com.fardeenkhan.moodtune.core.database.mapper

import com.fardeenkhan.moodtune.core.database.entity.SongEntity
import com.fardeenkhan.moodtune.core.database.entity.SongExplanationEntity
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        reason = reason,
        imageUrl = imageUrl,
        externalUrl = externalUrl,
        mood = mood,
        energy = energy,
        explanation = explanation.toEntity()
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
        energy = energy,
        explanation = explanation.toDomain(id)
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
