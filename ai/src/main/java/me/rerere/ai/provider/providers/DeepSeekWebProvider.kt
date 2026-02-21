package me.rerere.ai.provider.providers

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekMessageConverter
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekSSEParser
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebApiException
import me.rerere.ai.provider.providers.deepseekweb.DeepSeekWebClient
import me.rerere.ai.provider.providers.deepseekweb.PowSolver
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.HttpException
import me.rerere.ai.util.parseErrorDetail
import me.rerere.ai.util.stringSafe
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "DeepSeekWebProvider"

class DeepSeekWebProvider(
    private val client: OkHttpClient,
) : Provider<ProviderSetting.DeepSeekWeb> {

    private val webClient = DeepSeekWebClient(client)
    private val powSolver = PowSolver()
    private val tokenCache = ConcurrentHashMap<Uuid, String>()

    override suspend fun listModels(providerSetting: ProviderSetting.DeepSeekWeb): List<Model> {
        return DEEPSEEK_WEB_MODELS
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.DeepSeekWeb,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val prompt = DeepSeekMessageConverter.convertMessages(messages)
        val config = DeepSeekMessageConverter.getModelConfig(params.model.modelId)

        executeWithRetry(providerSetting) { token ->
            val sessionId = webClient.createSession(token)
            val challenge = webClient.createPowChallenge(token)
            val powResponse = powSolver.solve(challenge)

            val request = webClient.buildCompletionRequest(
                token = token,
                sessionId = sessionId,
                powResponse = powResponse,
                prompt = prompt,
                thinkingEnabled = config.thinkingEnabled,
                searchEnabled = config.searchEnabled,
            )

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw DeepSeekWebApiException(
                    statusCode = response.code,
                    message = "DeepSeek completion failed (${response.code})",
                )
            }

            val body = response.body?.string() ?: error("Empty response body")
            var state = DeepSeekSSEParser.ParseState()
            val allParts = mutableListOf<me.rerere.ai.provider.providers.deepseekweb.ContentPart>()

            body.lineSequence()
                .filter { it.startsWith("data: ") }
                .map { it.removePrefix("data: ").trim() }
                .forEach { data ->
                    val result = DeepSeekSSEParser.parseLine(data, state)
                    state = result.state
                    allParts.addAll(result.parts)
                    if (result.errorMessage != null) {
                        error("DeepSeek error: ${result.errorMessage}")
                    }
                }

            DeepSeekSSEParser.toFinalMessageChunk(allParts, params.model.modelId)
        }
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.DeepSeekWeb,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        val prompt = DeepSeekMessageConverter.convertMessages(messages)
        val config = DeepSeekMessageConverter.getModelConfig(params.model.modelId)

        Log.i(TAG, "streamText: model=${params.model.modelId}, thinking=${config.thinkingEnabled}, search=${config.searchEnabled}")

        return callbackFlow {
            var state = DeepSeekSSEParser.ParseState()

            val listener = object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    val result = DeepSeekSSEParser.parseLine(data, state)
                    state = result.state

                    if (result.errorMessage != null) {
                        close(HttpException("DeepSeek error: ${result.errorMessage}"))
                        return
                    }

                    if (result.parts.isNotEmpty()) {
                        val chunk = DeepSeekSSEParser.toMessageChunk(result.parts, params.model.modelId)
                        trySend(chunk)
                    }

                    if (result.stop) {
                        trySend(DeepSeekSSEParser.toStopChunk(params.model.modelId))
                        close()
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    var exception: Throwable? = t
                    Log.e(TAG, "SSE failure: ${t?.javaClass?.name} ${t?.message}")

                    val bodyRaw = response?.body?.stringSafe()
                    try {
                        if (!bodyRaw.isNullOrBlank()) {
                            val bodyElement = Json.parseToJsonElement(bodyRaw)
                            exception = bodyElement.parseErrorDetail()
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to parse error body", e)
                    } finally {
                        close(exception ?: HttpException("DeepSeek stream failed"))
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            }

            // Prepare request with token retry, inside callbackFlow but before SSE starts
            val request = try {
                executeWithRetry(providerSetting) { token ->
                    val sessionId = webClient.createSession(token)
                    val challenge = webClient.createPowChallenge(token)
                    val powResponse = powSolver.solve(challenge)

                    webClient.buildCompletionRequest(
                        token = token,
                        sessionId = sessionId,
                        powResponse = powResponse,
                        prompt = prompt,
                        thinkingEnabled = config.thinkingEnabled,
                        searchEnabled = config.searchEnabled,
                    )
                }
            } catch (t: Throwable) {
                close(t)
                return@callbackFlow
            }

            val eventSource = EventSources.createFactory(client)
                .newEventSource(request, listener)

            awaitClose {
                Log.d(TAG, "Closing SSE connection")
                eventSource.cancel()
            }
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult {
        error("DeepSeek Web does not support image generation")
    }

    private suspend fun ensureToken(setting: ProviderSetting.DeepSeekWeb): String {
        tokenCache[setting.id]?.let { return it }

        if (setting.token.isNotBlank()) {
            tokenCache[setting.id] = setting.token
            return setting.token
        }

        return login(setting)
    }

    private suspend fun login(setting: ProviderSetting.DeepSeekWeb): String {
        require(setting.email.isNotBlank() || setting.mobile.isNotBlank()) {
            "DeepSeek Web requires email or mobile number"
        }
        require(setting.password.isNotBlank()) {
            "DeepSeek Web requires password"
        }

        val token = webClient.login(setting.email, setting.mobile, setting.password)
        tokenCache[setting.id] = token
        return token
    }

    /**
     * Execute an operation with automatic token retry on auth failure.
     * Only retries on 401/403 responses; rethrows all other exceptions.
     */
    private suspend fun <T> executeWithRetry(
        setting: ProviderSetting.DeepSeekWeb,
        block: suspend (token: String) -> T,
    ): T {
        val token = ensureToken(setting)
        return try {
            block(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DeepSeekWebApiException) {
            if (e.statusCode !in AUTH_FAILURE_CODES) throw e
            if (setting.password.isBlank() || (setting.email.isBlank() && setting.mobile.isBlank())) throw e
            Log.w(TAG, "Auth failure (${e.statusCode}), retrying with fresh token")
            tokenCache.remove(setting.id)
            val newToken = login(setting)
            block(newToken)
        }
    }

    fun invalidateToken(providerId: Uuid) {
        tokenCache.remove(providerId)
    }

    companion object {
        private val AUTH_FAILURE_CODES = setOf(401, 403)

        val DEEPSEEK_WEB_MODELS = listOf(
            Model(
                id = Uuid.parse("c5a1e000-0001-0001-0001-000000000001"),
                modelId = "deepseek-chat",
                displayName = "DeepSeek V3",
                inputModalities = listOf(Modality.TEXT),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL),
            ),
            Model(
                id = Uuid.parse("c5a1e000-0002-0002-0002-000000000002"),
                modelId = "deepseek-reasoner",
                displayName = "DeepSeek R1",
                inputModalities = listOf(Modality.TEXT),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.REASONING),
            ),
            Model(
                id = Uuid.parse("c5a1e000-0003-0003-0003-000000000003"),
                modelId = "deepseek-chat-search",
                displayName = "DeepSeek V3 (Search)",
                inputModalities = listOf(Modality.TEXT),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL),
            ),
            Model(
                id = Uuid.parse("c5a1e000-0004-0004-0004-000000000004"),
                modelId = "deepseek-reasoner-search",
                displayName = "DeepSeek R1 (Search)",
                inputModalities = listOf(Modality.TEXT),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.REASONING),
            ),
        )
    }
}
