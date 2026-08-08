package com.fardeenkhan.moodtune.infrastructure.datasource

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.fardeenkhan.moodtune.domain.model.Song
import com.fardeenkhan.moodtune.domain.model.SongExplanation
import java.io.File
import java.security.MessageDigest

/**
 * Scans the device's MediaStore for local audio files. Each scan also extracts any embedded
 * album art to [artDir], keyed by an MD5 hash of the file path so repeat scans reuse the same
 * cached JPEG instead of re-extracting on every call, and prunes art for files no longer present
 * (see [deleteOrphanedArt]). [Song.imageUrl] is the MediaStore album-art `content://` URI, which
 * can be stale or empty depending on when the store last indexed the file; [Song.localAlbumArtPath]
 * is the more reliable source since it's decoded directly from the file's own embedded metadata.
 */
class LocalSongDataSource(private val context: Context) {

    private val artDir = File(context.filesDir, "album_art").also { it.mkdirs() }

    fun fetchDeviceSongs(): List<Song> {
        val songList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val cursor = context.contentResolver.query(uri, projection, null, null, null)

        val activeHashes = mutableSetOf<String>()

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn).toString()
                val title = it.getString(titleColumn) ?: "Unknown"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"
                val path = it.getString(dataColumn)
                val albumId = it.getLong(albumIdColumn)

                val albumArtUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    albumId
                )

                val localArtPath = saveEmbeddedArt(path, activeHashes)

                songList.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        reason = "Device File",
                        imageUrl = albumArtUri.toString(),
                        externalUrl = path,
                        mood = null,
                        explanation = SongExplanation(id, "Local file from device", "Unknown", "Local"),
                        localAlbumArtPath = localArtPath
                    )
                )
            }
        }

        deleteOrphanedArt(activeHashes)

        return songList
    }

    // Returns the saved file path, or null if no embedded art exists
    private fun saveEmbeddedArt(filePath: String?, activeHashes: MutableSet<String>): String? {
        if (filePath == null) return null

        val hash = md5(filePath)
        activeHashes.add(hash)

        val artFile = File(artDir, "$hash.jpg")
        if (artFile.exists()) return artFile.absolutePath

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val bytes = retriever.embeddedPicture ?: return null
            artFile.outputStream().use { out ->
                // Decode and re-encode at 90% quality to normalise format
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    ?: out.write(bytes) // fallback: write raw bytes if decode fails
            }
            artFile.absolutePath
        } catch (e: Exception) {
            artFile.delete()
            null
        } finally {
            retriever.release()
        }
    }

    private fun deleteOrphanedArt(activeHashes: Set<String>) {
        artDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in activeHashes) {
                file.delete()
            }
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
