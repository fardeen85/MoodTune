package com.fardeenkhan.moodtune.infrastructure.remote

import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<Content>
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: GeminiContent
)

@Serializable
data class GeminiContent(
    val parts: List<Part>
)

@Serializable
data class RecommendedSong(
    val cat: String,
    val song: String,
    val artist: String,
    val q: String,
    val about: String,
    val mood: String,
    val context: String
)

@Serializable
data class YouTubeSearchResponse(
    val items: List<YouTubeVideoItem>
)

@Serializable
data class YouTubeVideoItem(
    val id: YouTubeVideoId,
    val snippet: YouTubeVideoSnippet
)

@Serializable
data class YouTubeVideoId(
    val videoId: String
)

@Serializable
data class YouTubeVideoSnippet(
    val title: String,
    val description: String,
    val thumbnails: YouTubeThumbnails,
    val channelTitle: String
)

@Serializable
data class YouTubeThumbnails(
    val default: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null
)

@Serializable
data class YouTubeThumbnail(
    val url: String
)
