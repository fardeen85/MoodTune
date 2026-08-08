package com.fardeenkhan.moodtune.core.ui.util

import com.fardeenkhan.moodtune.domain.model.Song
import java.io.File

/**
 * Coil-compatible art source for this song: locally extracted embedded art when available
 * (see `LocalSongDataSource`), falling back to the remote/MediaStore [Song.imageUrl] otherwise.
 * Nullable receiver so callers with an optional "current song" (e.g. nothing playing) can call
 * this directly without an extra null check.
 */
fun Song?.albumArtModel(): Any? = this?.localAlbumArtPath?.let { File(it) } ?: this?.imageUrl
