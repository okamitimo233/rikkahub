package me.rerere.ai.provider.providers.deepseekweb

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.json

private const val TAG = "DeepSeekSSEParser"

object DeepSeekSSEParser {

    private val FRAGMENT_CONTENT_REGEX = Regex("^response/fragments/-?\\d+/content$")
    private val FRAGMENT_STATUS_REGEX = Regex("^response/fragments/-?\\d+/status$")

    private val SKIP_PATTERNS = listOf(
        "quasi_status",
        "elapsed_secs",
        "token_usage",
        "pending_fragment",
        "conversation_mode",
    )

    data class ParseState(
        val currentType: ContentType = ContentType.TEXT,
    )

    enum class ContentType {
        TEXT,
        THINKING,
    }

    fun parseLine(
        data: String,
        state: ParseState,
    ): ParseResult {
        if (data == "[DONE]") {
            return ParseResult(parsed = true, stop = true, state = state)
        }

        return try {
            val obj = json.parseToJsonElement(data).let { it as? JsonObject }
                ?: return ParseResult(parsed = false, state = state)

            val path = obj["p"]?.jsonPrimitive?.content ?: return ParseResult(parsed = false, state = state)
            val value = obj["v"]

            if (SKIP_PATTERNS.any { path.contains(it) }) {
                return ParseResult(parsed = true, state = state)
            }

            when {
                // Error must be checked first
                path == "response/error_msg" -> {
                    val errMsg = value?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                    ParseResult(
                        parsed = true,
                        stop = true,
                        errorMessage = errMsg,
                        state = state,
                    )
                }

                // Top-level status
                path == "response/status" -> {
                    val status = value?.jsonPrimitive?.contentOrNull
                    if (status == "FINISHED") {
                        ParseResult(parsed = true, stop = true, state = state)
                    } else {
                        ParseResult(parsed = true, state = state)
                    }
                }

                // Fragment-level status (skip)
                path.matches(FRAGMENT_STATUS_REGEX) -> {
                    ParseResult(parsed = true, state = state)
                }

                // Thinking content (exact match, before generic content)
                path == "response/thinking_content" -> {
                    val text = value?.jsonPrimitive?.contentOrNull ?: ""
                    ParseResult(
                        parsed = true,
                        parts = listOf(ContentPart(text, ContentType.THINKING)),
                        state = state.copy(currentType = ContentType.THINKING),
                    )
                }

                // Top-level content (exact match)
                path == "response/content" -> {
                    val text = value?.jsonPrimitive?.contentOrNull ?: ""
                    ParseResult(
                        parsed = true,
                        parts = listOf(ContentPart(text, ContentType.TEXT)),
                        state = state.copy(currentType = ContentType.TEXT),
                    )
                }

                // Fragment content (inherits current type from state)
                path.matches(FRAGMENT_CONTENT_REGEX) -> {
                    val text = value?.jsonPrimitive?.contentOrNull ?: ""
                    ParseResult(
                        parsed = true,
                        parts = listOf(ContentPart(text, state.currentType)),
                        state = state,
                    )
                }

                else -> {
                    Log.d(TAG, "Unhandled SSE path: $path")
                    ParseResult(parsed = true, state = state)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SSE line", e)
            ParseResult(parsed = false, state = state)
        }
    }

    private fun toMessageParts(parts: List<ContentPart>): List<UIMessagePart> {
        return parts.mapNotNull { part ->
            when (part.type) {
                ContentType.TEXT -> {
                    if (part.text.isEmpty()) null
                    else UIMessagePart.Text(part.text)
                }
                ContentType.THINKING -> {
                    if (part.text.isEmpty()) null
                    else UIMessagePart.Reasoning(
                        reasoning = part.text,
                        finishedAt = null,
                    )
                }
            }
        }
    }

    /**
     * Build a streaming delta chunk (used by streamText).
     */
    fun toMessageChunk(parts: List<ContentPart>, model: String): MessageChunk {
        val messageParts = toMessageParts(parts)
        return MessageChunk(
            id = "",
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = messageParts,
                    ),
                    message = null,
                    finishReason = null,
                )
            ),
        )
    }

    /**
     * Build a final message chunk (used by generateText).
     * Sets `message` field instead of `delta` for downstream consumers.
     */
    fun toFinalMessageChunk(parts: List<ContentPart>, model: String): MessageChunk {
        val messageParts = toMessageParts(parts)
        return MessageChunk(
            id = "",
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = messageParts,
                    ),
                    finishReason = "stop",
                )
            ),
        )
    }

    fun toStopChunk(model: String): MessageChunk {
        return MessageChunk(
            id = "",
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = emptyList(),
                    ),
                    message = null,
                    finishReason = "stop",
                )
            ),
        )
    }
}

data class ContentPart(
    val text: String,
    val type: DeepSeekSSEParser.ContentType,
)

data class ParseResult(
    val parsed: Boolean,
    val stop: Boolean = false,
    val parts: List<ContentPart> = emptyList(),
    val errorMessage: String? = null,
    val state: DeepSeekSSEParser.ParseState,
)
