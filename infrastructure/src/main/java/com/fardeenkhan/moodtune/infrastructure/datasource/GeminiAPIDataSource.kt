package com.fardeenkhan.moodtune.infrastructure.datasource

import com.fardeenkhan.moodtune.infrastructure.remote.GeminiRequest
import com.fardeenkhan.moodtune.infrastructure.remote.GeminiResponse
import com.fardeenkhan.moodtune.infrastructure.remote.Content
import com.fardeenkhan.moodtune.infrastructure.remote.Part
import com.fardeenkhan.moodtune.infrastructure.remote.LyricLineDto
import com.fardeenkhan.moodtune.infrastructure.remote.RecommendedSong
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import io.ktor.http.ContentType
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*

class GeminiAPIDataSource(
    private val client: HttpClient,
    private val apiKey: String
) {
    // Corrected model name from gemini-2.5-flash to gemini-1.5-flash
    private val baseUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent"

    suspend fun getSongRecommendations(mood: String): List<RecommendedSong> {
        val prompt = """
            List 5 Bollywood & 5 Hollywood songs for $mood mood. 
            Output strictly JSON: [{"cat":"Bolly/Holly","song":"","artist":"","q":"Artist - Song Official Music Video", "about": "short brief in one line about the song", "mood": "one line why it fits $mood", "context": "one line about what situation or film this song belongs to"}]
        """.trimIndent()

        val requestBody = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt)
                    )
                )
            )
        )

        val response: GeminiResponse = client.post("$baseUrl?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            // Added explicit timeouts to prevent SocketTimeoutException
            timeout {
                requestTimeoutMillis = 60_000 // 60 seconds
                connectTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }.body()

        val textResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        
        // Clean up the response if Gemini wraps it in ```json ... ```
        val jsonString = textResponse.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        return Json.decodeFromString<List<RecommendedSong>>(jsonString)
    }

    suspend fun getLyrics(title: String, artist: String, durationMs: Long): List<LyricLineDto> {
        val prompt = """
            Generate timestamped lyrics for "$title" by "$artist" (duration: ${durationMs}ms).
            Return ONLY a JSON array, no markdown fences. Each element: {"ms": <milliseconds>, "line": "<lyric line>"}.
            Space lines proportionally across the full duration. Start with {"ms":0,"line":"♪ Intro ♪"}.
            Example: [{"ms":0,"line":"♪ Intro ♪"},{"ms":15000,"line":"First verse line here"}]
        """.trimIndent()

        val requestBody = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        val response: GeminiResponse = client.post("$baseUrl?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            timeout {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }.body()

        val textResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        val jsonString = textResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return Json.decodeFromString<List<LyricLineDto>>(jsonString)
    }
}
