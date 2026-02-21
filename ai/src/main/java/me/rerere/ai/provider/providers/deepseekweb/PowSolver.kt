package me.rerere.ai.provider.providers.deepseekweb

import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import me.rerere.ai.util.json
import java.math.BigInteger
import java.security.MessageDigest

private const val TAG = "PowSolver"
private val MAX_HASH = BigInteger.ONE.shiftLeft(256)

class PowSolver {

    fun solve(challenge: JsonObject): String {
        val algorithm = challenge["algorithm"]?.jsonPrimitive?.content ?: "DeepSeekHashV1"
        val challengeStr = challenge["challenge"]?.jsonPrimitive?.content
            ?: error("PoW challenge missing 'challenge' field")
        val salt = challenge["salt"]?.jsonPrimitive?.content
            ?: error("PoW challenge missing 'salt' field")
        val signature = challenge["signature"]?.jsonPrimitive?.content
            ?: error("PoW challenge missing 'signature' field")
        val difficulty = challenge["difficulty"]?.jsonPrimitive?.long
            ?: error("PoW challenge missing 'difficulty' field")
        val targetPath = challenge["target_path"]?.jsonPrimitive?.content
            ?: "/api/v0/chat/completion"

        Log.i(TAG, "Solving PoW: algorithm=$algorithm, difficulty=$difficulty")
        require(algorithm == "DeepSeekHashV1") { "Unsupported PoW algorithm: $algorithm" }
        require(difficulty > 0) { "PoW difficulty must be positive, got $difficulty" }

        val startTime = System.currentTimeMillis()

        val answer = findNonce(challengeStr, salt, difficulty)

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "PoW solved in ${elapsed}ms")

        val result = buildJsonObject {
            put("algorithm", algorithm)
            put("challenge", challengeStr)
            put("salt", salt)
            put("answer", answer)
            put("signature", signature)
            put("target_path", targetPath)
        }

        val resultJson = json.encodeToString(result)
        return Base64.encodeToString(resultJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun findNonce(challenge: String, salt: String, difficulty: Long): String {
        val target = MAX_HASH.divide(BigInteger.valueOf(difficulty))
        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = "${challenge}_${salt}_".toByteArray(Charsets.UTF_8)

        for (nonce in 0 until MAX_NONCE) {
            val nonceBytes = nonce.toString().toByteArray(Charsets.UTF_8)
            digest.reset()
            digest.update(prefix)
            digest.update(nonceBytes)
            val hash = digest.digest()
            val hashInt = BigInteger(1, hash)

            if (hashInt < target) {
                return nonce.toString()
            }
        }

        error("Failed to solve PoW within $MAX_NONCE attempts")
    }

    companion object {
        private const val MAX_NONCE = 10_000_000
    }
}
