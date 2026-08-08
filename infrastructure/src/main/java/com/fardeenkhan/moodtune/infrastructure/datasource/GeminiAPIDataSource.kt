package com.fardeenkhan.moodtune.infrastructure.datasource

import com.fardeenkhan.moodtune.config.ConfigRepository
import com.fardeenkhan.moodtune.infrastructure.remote.Content
import com.fardeenkhan.moodtune.infrastructure.remote.GeminiRequest
import com.fardeenkhan.moodtune.infrastructure.remote.GeminiResponse
import com.fardeenkhan.moodtune.infrastructure.remote.LyricLineDto
import com.fardeenkhan.moodtune.infrastructure.remote.Part
import com.fardeenkhan.moodtune.infrastructure.remote.RecommendedSong
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

private const val GEMINI_TIMEOUT_MS = 60_000L

class GeminiAPIDataSource(
    private val client: HttpClient,
    private val configRepository: ConfigRepository
) {
    suspend fun getSongRecommendations(mood: String): List<RecommendedSong> {
        val prompt = """
            List 5 Bollywood & 5 Hollywood songs for $mood mood.
            Output strictly JSON: [{"cat":"Bolly/Holly","song":"","artist":"","q":"Artist - Song Official Music Video", "about": "short brief in one line about the song", "mood": "one line why it fits $mood", "context": "one line about what situation or film this song belongs to"}]
        """.trimIndent()

        return Json.decodeFromString(promptGemini(prompt))
    }

    suspend fun getLyrics(title: String, artist: String, durationMs: Long, movie: String? = null): List<LyricLineDto> {
        val movieContext = if (!movie.isNullOrBlank()) " from the movie/soundtrack \"$movie\"" else ""
        val prompt = """
            Generate timestamped lyrics for "$title" by "$artist"$movieContext (duration: ${durationMs}ms).
            Return ONLY a JSON array, no markdown fences. Each element: {"ms": <milliseconds>, "line": "<lyric line>"}.
            Space lines proportionally across the full duration. Start with {"ms":0,"line":"♪ Intro ♪"}.
            Example: [{"ms":0,"line":"♪ Intro ♪"},{"ms":15000,"line":"First verse line here"}]
        """.trimIndent()

        return Json.decodeFromString(promptGemini(prompt))
    }

    /**
     * Sends [prompt] to Gemini and returns its response text as a raw JSON string, ready for
     * the caller to [Json.decodeFromString] into whatever shape it expects. Strips the
     * ```json ... ``` (or bare ``` ... ```) markdown fence Gemini often wraps its output in
     * despite being asked for strict JSON.
     */
    private suspend fun promptGemini(prompt: String): String {
        val config = configRepository.ensureConfig { it.geminiKey.isNotBlank() }

        val requestBody = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        val response: GeminiResponse = client.post("${config.geminiUrl}?key=${config.geminiKey}") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            timeout {
                requestTimeoutMillis = GEMINI_TIMEOUT_MS
                connectTimeoutMillis = GEMINI_TIMEOUT_MS
                socketTimeoutMillis = GEMINI_TIMEOUT_MS
            }
        }.body()

        val textResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        return textResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
