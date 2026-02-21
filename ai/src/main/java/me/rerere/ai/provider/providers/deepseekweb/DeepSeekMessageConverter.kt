package me.rerere.ai.provider.providers.deepseekweb

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

object DeepSeekMessageConverter {

    fun convertMessages(messages: List<UIMessage>): String {
        val merged = mergeConsecutiveRoles(messages)
        return buildString {
            merged.forEachIndexed { index, msg ->
                val text = extractText(msg)
                if (text.isBlank()) return@forEachIndexed

                when (msg.role) {
                    MessageRole.SYSTEM, MessageRole.USER -> {
                        if (index > 0) append("<|User|>")
                        append(text)
                    }
                    MessageRole.ASSISTANT -> {
                        append("<|Assistant|>")
                        append(text)
                        append("<|end_of_sentence|>")
                    }
                    else -> {
                        // TOOL messages treated as user context
                        if (index > 0) append("<|User|>")
                        append(text)
                    }
                }
            }
        }
    }

    data class ModelConfig(
        val thinkingEnabled: Boolean,
        val searchEnabled: Boolean,
    )

    fun getModelConfig(modelId: String): ModelConfig {
        val isReasoner = modelId.contains("reasoner", ignoreCase = true)
        val isSearch = modelId.contains("search", ignoreCase = true)
        return ModelConfig(
            thinkingEnabled = isReasoner,
            searchEnabled = isSearch,
        )
    }

    private fun mergeConsecutiveRoles(messages: List<UIMessage>): List<UIMessage> {
        if (messages.isEmpty()) return emptyList()

        val result = mutableListOf<UIMessage>()
        var current = messages.first()

        for (i in 1 until messages.size) {
            val next = messages[i]
            if (current.role == next.role) {
                current = current.copy(
                    parts = current.parts + UIMessagePart.Text("\n\n") + next.parts
                )
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private fun extractText(message: UIMessage): String {
        return message.parts.joinToString("") { part ->
            when (part) {
                is UIMessagePart.Text -> part.text
                is UIMessagePart.Image -> convertImageRef(part.url)
                is UIMessagePart.Reasoning -> part.reasoning
                is UIMessagePart.Document -> "[Document: ${part.fileName}]"
                else -> ""
            }
        }
    }

    private fun convertImageRef(url: String): String {
        return if (url.startsWith("data:")) {
            ""  // Skip inline base64 images (not supported by DeepSeek Web)
        } else {
            "![]($url)"
        }
    }
}
