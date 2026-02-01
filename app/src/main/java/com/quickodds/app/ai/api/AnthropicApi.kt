package com.quickodds.app.ai.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for AI Analysis via Firebase Function.
 * The Firebase Function proxies requests to Anthropic Claude API,
 * keeping the API key secure on the server side.
 */
interface AnthropicApi {

    companion object {
        const val BASE_URL = "https://us-central1-quick-odds.cloudfunctions.net/"
        const val API_VERSION = "2023-06-01"

        // ═══════════════════════════════════════════════════════════════════════════════
        // MODEL CONFIGURATION - Cost Optimization Strategy
        // ═══════════════════════════════════════════════════════════════════════════════
        //
        // SONNET (High Capability, Higher Cost):
        //   - Use for: Advanced Quantitative Analysis, Multi-Agent Ensemble
        //   - Cost: ~$3/M input, ~$15/M output tokens
        //
        // HAIKU (Fast, Low Cost):
        //   - Use for: Result Settlement, Basic Data Formatting, Simple Queries
        //   - Cost: ~$0.25/M input, ~$1.25/M output tokens
        //   - ~90% cost reduction for routine tasks
        //
        // ═══════════════════════════════════════════════════════════════════════════════

        // Premium model for complex analysis
        const val MODEL_SONNET = "claude-sonnet-4-20250514"

        // Cost-efficient model for routine tasks
        const val MODEL_HAIKU = "claude-3-5-haiku-20241022"

        // Legacy model IDs (for reference)
        const val MODEL_SONNET_LEGACY = "claude-3-5-sonnet-20241022"
        const val MODEL_HAIKU_LEGACY = "claude-3-haiku-20240307"
    }

    @POST("analyzeMatch")
    suspend fun createMessage(
        @Header("content-type") contentType: String = "application/json",
        @Body request: MessageRequest
    ): Response<MessageResponse>
}

/**
 * Request body for Claude API.
 */
data class MessageRequest(
    @SerializedName("model")
    val model: String = AnthropicApi.MODEL_SONNET,

    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,

    @SerializedName("system")
    val system: String,

    @SerializedName("messages")
    val messages: List<Message>
)

data class Message(
    @SerializedName("role")
    val role: String,       // "user" or "assistant"

    @SerializedName("content")
    val content: String
)

/**
 * Response from Claude API.
 */
data class MessageResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("role")
    val role: String,

    @SerializedName("content")
    val content: List<ContentBlock>,

    @SerializedName("model")
    val model: String,

    @SerializedName("stop_reason")
    val stopReason: String?,

    @SerializedName("stop_sequence")
    val stopSequence: String?,

    @SerializedName("usage")
    val usage: Usage
) {
    /**
     * Extract text content from response.
     */
    fun getTextContent(): String {
        return content
            .filterIsInstance<ContentBlock>()
            .filter { it.type == "text" }
            .joinToString("") { it.text ?: "" }
    }
}

data class ContentBlock(
    @SerializedName("type")
    val type: String,

    @SerializedName("text")
    val text: String?
)

data class Usage(
    @SerializedName("input_tokens")
    val inputTokens: Int,

    @SerializedName("output_tokens")
    val outputTokens: Int
)

/**
 * Error response from API.
 */
data class ApiError(
    @SerializedName("type")
    val type: String,

    @SerializedName("error")
    val error: ErrorDetails
)

data class ErrorDetails(
    @SerializedName("type")
    val type: String,

    @SerializedName("message")
    val message: String
)
