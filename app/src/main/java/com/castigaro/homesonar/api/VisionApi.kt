package com.castigaro.homesonar.api

import android.content.Context
import com.castigaro.common.llm.ModelPricing
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.homesonar.data.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Anbindung an die Vision-Modelle von Anthropic (Messages API) und
 * OpenAI (Chat Completions API). Welcher Anbieter genutzt wird,
 * entscheidet die Einstellung des Nutzers.
 */
object VisionApi {

    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    class ApiException(message: String) : IOException(message)

    data class DetectedObject(val name: String)

    /** Erkennt die Gegenstände auf einem Foto. */
    suspend fun analyze(context: Context, photo: File): List<DetectedObject> =
        withContext(Dispatchers.IO) {
            val prompt = """
                Erkenne alle klar identifizierbaren Gegenstände auf diesem Foto.
                Nenne jeden Gegenstand mit einem kurzen, prägnanten deutschen Namen
                (z. B. "Schere", "Ladekabel", "Blaue Tasse"). Ignoriere Hintergrund,
                Möbel-Oberflächen und Unscharfes. Maximal 15 Gegenstände.
                Antworte ausschließlich mit JSON in exakt dieser Form:
                {"objects":[{"name":"..."},{"name":"..."}]}
            """.trimIndent()

            val text = requestVision(context, photo, prompt)
            val json = extractJson(text)
            val result = mutableListOf<DetectedObject>()
            val arr = json.optJSONArray("objects") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val name = arr.getJSONObject(i).optString("name").trim()
                if (name.isNotEmpty()) result.add(DetectedObject(name))
            }
            result
        }

    /**
     * Prüft, welcher der gespeicherten Gegenstände auf dem Foto zu sehen ist.
     * Liefert den Index in [items] oder null, wenn nichts passt.
     */
    suspend fun findMatch(context: Context, photo: File, items: List<Item>): Int? =
        withContext(Dispatchers.IO) {
            val list = items.mapIndexed { i, item -> "${i + 1}. ${item.name}" }
                .joinToString("\n")
            val prompt = """
                Auf dem Foto ist ein Gegenstand zu sehen. Hier ist die Liste der
                bekannten Gegenstände:
                $list

                Welcher Eintrag aus der Liste passt am besten zu dem Gegenstand
                auf dem Foto? Antworte ausschließlich mit JSON in exakt dieser
                Form: {"match": N} — wobei N die Nummer aus der Liste ist,
                oder {"match": 0}, wenn kein Eintrag passt.
            """.trimIndent()

            val text = requestVision(context, photo, prompt)
            val match = extractJson(text).optInt("match", 0)
            if (match in 1..items.size) match - 1 else null
        }

    private fun requestVision(context: Context, photo: File, prompt: String): String {
        val config = ProviderSettings.activeConfig(context)
            ?: throw ApiException("Kein aktiver API-Key — in den Einstellungen hinterlegen oder aktivieren.")
        val imageBase64 = com.castigaro.homesonar.util.Photos.toBase64(photo)

        return if (config.provider == ProviderSettings.PROVIDER_OPENAI) {
            requestOpenAi(context, config.apiKey, config.model, imageBase64, prompt)
        } else {
            requestAnthropic(context, config.apiKey, config.model, imageBase64, prompt)
        }
    }

    /** Bucht die geschätzten Kosten eines Aufrufs auf den Zähler des Anbieters. */
    private fun trackCost(
        context: Context,
        provider: String,
        model: String,
        inputTokens: Long,
        outputTokens: Long,
    ) {
        val micros = ModelPricing.estimateCostMicros(provider, model, inputTokens, outputTokens)
        ProviderSettings.addCostMicros(context, provider, micros)
    }

    private fun requestAnthropic(
        context: Context,
        apiKey: String,
        model: String,
        imageBase64: String,
        prompt: String,
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", imageBase64)
                        })
                    })
                    .put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                )
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(apiErrorMessage(responseText, response.code))
            }
            val json = JSONObject(responseText)
            json.optJSONObject("usage")?.let { usage ->
                trackCost(
                    context, ProviderSettings.PROVIDER_ANTHROPIC, model,
                    inputTokens = usage.optLong("input_tokens", 0L),
                    outputTokens = usage.optLong("output_tokens", 0L),
                )
            }
            if (json.optString("stop_reason") == "refusal") {
                throw ApiException("Die Anfrage wurde vom Modell abgelehnt.")
            }
            val content = json.getJSONArray("content")
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.getString("type") == "text") {
                    return block.getString("text")
                }
            }
            throw ApiException("Leere Antwort vom Modell.")
        }
    }

    private fun requestOpenAi(
        context: Context,
        apiKey: String,
        model: String,
        imageBase64: String,
        prompt: String,
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    .put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$imageBase64")
                        })
                    })
                )
            }))
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(apiErrorMessage(responseText, response.code))
            }
            val json = JSONObject(responseText)
            json.optJSONObject("usage")?.let { usage ->
                trackCost(
                    context, ProviderSettings.PROVIDER_OPENAI, model,
                    inputTokens = usage.optLong("prompt_tokens", 0L),
                    outputTokens = usage.optLong("completion_tokens", 0L),
                )
            }
            return json
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    private fun apiErrorMessage(responseText: String, code: Int): String {
        val detail = runCatching {
            JSONObject(responseText).getJSONObject("error").getString("message")
        }.getOrNull()
        return when {
            detail != null -> "API-Fehler ($code): $detail"
            code == 401 -> "API-Key ungültig (401)."
            code == 429 -> "Rate-Limit erreicht (429) — bitte kurz warten."
            else -> "API-Fehler ($code)."
        }
    }

    /** Zieht das JSON-Objekt aus der Modellantwort (toleriert ```-Zäune und Fließtext). */
    private fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw ApiException("Antwort nicht lesbar: $text")
        return JSONObject(text.substring(start, end + 1))
    }
}
