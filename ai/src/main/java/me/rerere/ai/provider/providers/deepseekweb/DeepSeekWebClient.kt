package me.rerere.ai.provider.providers.deepseekweb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.util.json
import me.rerere.common.http.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "DeepSeekWebClient"

class DeepSeekWebApiException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class DeepSeekWebClient(private val client: OkHttpClient) {
    companion object {
        const val DEEPSEEK_HOST = "chat.deepseek.com"
        private const val LOGIN_URL = "https://chat.deepseek.com/api/v0/users/login"
        private const val CREATE_SESSION_URL = "https://chat.deepseek.com/api/v0/chat_session/create"
        private const val CREATE_POW_URL = "https://chat.deepseek.com/api/v0/chat/create_pow_challenge"
        private const val COMPLETION_URL = "https://chat.deepseek.com/api/v0/chat/completion"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun baseHeaders(): Map<String, String> = mapOf(
            "User-Agent" to "DeepSeek/1.6.11 Android/35",
            "Accept" to "application/json",
            "x-client-platform" to "android",
            "x-client-version" to "1.6.11",
            "x-client-locale" to "zh_CN",
            "accept-charset" to "UTF-8",
        )
    }

    suspend fun login(email: String, mobile: String, password: String): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                if (email.isNotBlank()) {
                    put("email", email)
                    put("area_code", "")
                } else {
                    put("mobile", mobile)
                    put("area_code", "+86")
                }
                put("password", password)
            }

            val request = buildRequest(LOGIN_URL)
                .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).await()
            val responseBody = response.body?.string()
                ?: error("Login response body is null")

            if (!response.isSuccessful) {
                Log.e(TAG, "Login failed: code=${response.code}")
                throw DeepSeekWebApiException(
                    statusCode = response.code,
                    message = "DeepSeek login failed (${response.code})",
                )
            }

            val responseJson = json.parseToJsonElement(responseBody).jsonObject
            val data = responseJson["data"]?.jsonObject
                ?: error("Login response missing 'data' field")
            val token = data["user"]?.jsonObject?.get("token")?.jsonPrimitive?.content
                ?: data["token"]?.jsonPrimitive?.content
                ?: error("Login response missing token")

            Log.i(TAG, "Login successful")
            token
        }

    suspend fun createSession(token: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("agent", "chat")
        }

        val request = buildAuthRequest(CREATE_SESSION_URL, token)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).await()
        val responseBody = response.body?.string()
            ?: error("Create session response body is null")

        if (!response.isSuccessful) {
            Log.e(TAG, "Create session failed: code=${response.code}")
            throw DeepSeekWebApiException(
                statusCode = response.code,
                message = "Failed to create session (${response.code})",
            )
        }

        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        val bizData = responseJson["data"]?.jsonObject?.get("biz_data")?.jsonObject
            ?: error("Session response missing biz_data")
        val sessionId = bizData["id"]?.jsonPrimitive?.content
            ?: error("Session response missing id")

        Log.i(TAG, "Session created")
        sessionId
    }

    suspend fun createPowChallenge(token: String): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("target_path", "/api/v0/chat/completion")
        }

        val request = buildAuthRequest(CREATE_POW_URL, token)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).await()
        val responseBody = response.body?.string()
            ?: error("PoW challenge response body is null")

        if (!response.isSuccessful) {
            Log.e(TAG, "PoW challenge failed: code=${response.code}")
            throw DeepSeekWebApiException(
                statusCode = response.code,
                message = "Failed to get PoW challenge (${response.code})",
            )
        }

        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        val bizData = responseJson["data"]?.jsonObject?.get("biz_data")?.jsonObject
            ?: error("PoW response missing biz_data")

        Log.i(TAG, "PoW challenge received")
        bizData
    }

    fun buildCompletionRequest(
        token: String,
        sessionId: String,
        powResponse: String,
        prompt: String,
        thinkingEnabled: Boolean,
        searchEnabled: Boolean,
    ): Request {
        val body = buildJsonObject {
            put("chat_session_id", sessionId)
            put("prompt", prompt)
            put("ref_file_ids", kotlinx.serialization.json.JsonArray(emptyList()))
            put("thinking_enabled", thinkingEnabled)
            put("search_enabled", searchEnabled)
        }

        return buildAuthRequest(COMPLETION_URL, token)
            .addHeader("x-ds-pow-response", powResponse)
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        baseHeaders().forEach { (k, v) -> builder.addHeader(k, v) }
        return builder
    }

    private fun buildAuthRequest(url: String, token: String): Request.Builder {
        return buildRequest(url)
            .addHeader("Authorization", "Bearer $token")
    }
}
