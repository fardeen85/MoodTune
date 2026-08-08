package com.fardeenkhan.moodtune.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import java.io.ByteArrayOutputStream

class AlbumArtFetcher(
    private val data: Uri,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Primary: try MediaStore albumart cache
        try {
            context.contentResolver.openInputStream(data)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty()) {
                    return SourceFetchResult(
                        source = ImageSource(
                            source = Buffer().apply { write(bytes) },
                            fileSystem = FileSystem.SYSTEM
                        ),
                        mimeType = "image/jpeg",
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("AlbumArtFetcher", "albumart cache miss for $data: ${e.message}")
        }

        // Fallback: albumart cache was empty or missing (e.g. art embedded after last scan).
        // Find the song content URI for this album and read the embedded picture directly.
        val albumId = data.lastPathSegment?.toLongOrNull() ?: return null
        val songContentUri = querySongContentUriForAlbum(albumId) ?: return null
        val bitmap = getEmbeddedAlbumArt(songContentUri) ?: return null

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)

        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(out.toByteArray()) },
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    // Uses context + content URI so it works on all Android versions including 10+ scoped storage
    private fun getEmbeddedAlbumArt(songUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, songUri)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else {
                Log.d("AlbumArtFetcher", "no embedded picture in $songUri")
                null
            }
        } catch (e: Exception) {
            Log.e("AlbumArtFetcher", "MediaMetadataRetriever failed for $songUri", e)
            null
        } finally {
            retriever.release()
        }
    }

    private fun querySongContentUriForAlbum(albumId: Long): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.ALBUM_ID} = ?",
            arrayOf(albumId.toString()),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    it.getLong(0)
                )
            } else null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val segments = data.pathSegments
            if (data.scheme == "content" &&
                data.authority == "media" &&
                segments.size >= 3 &&
                segments[1] == "audio" &&
                segments[2] == "albums"
            ) {
                return AlbumArtFetcher(data, context)
            }
            return null
        }
    }
}
