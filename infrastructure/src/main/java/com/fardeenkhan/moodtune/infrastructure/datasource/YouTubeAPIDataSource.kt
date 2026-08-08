package com.fardeenkhan.moodtune.infrastructure.datasource

import com.fardeenkhan.moodtune.config.ConfigRepository
import com.fardeenkhan.moodtune.infrastructure.remote.YouTubeSearchResponse
import com.fardeenkhan.moodtune.infrastructure.remote.YouTubeVideoItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class YouTubeAPIDataSource(
    private val client: HttpClient,
    private val configRepository: ConfigRepository
) {
    private val baseUrl = "https://www.googleapis.com/youtube/v3/search"

    suspend fun searchSong(query: String): YouTubeVideoItem? {
        val config = configRepository.ensureConfig { it.youtubeKey.isNotBlank() }

        val response: YouTubeSearchResponse = client.get(baseUrl) {
            parameter("part", "snippet")
            parameter("q", query)
            parameter("type", "video")
            parameter("maxResults", 1)
            parameter("key", config.youtubeKey)
        }.body()

        return response.items.firstOrNull()
    }
}
