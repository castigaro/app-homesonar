package com.castigaro.homesonar.data

import android.content.Context

/** Provider- und API-Key-Einstellungen (app-privater Speicher). */
object AppSettings {

    const val PROVIDER_ANTHROPIC = "anthropic"
    const val PROVIDER_OPENAI = "openai"

    const val DEFAULT_MODEL_ANTHROPIC = "claude-opus-4-8"
    const val DEFAULT_MODEL_OPENAI = "gpt-4o-mini"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getProvider(context: Context): String =
        prefs(context).getString("provider", PROVIDER_ANTHROPIC)!!

    fun getApiKey(context: Context): String =
        prefs(context).getString("apiKey", "")!!

    fun getModel(context: Context): String {
        val stored = prefs(context).getString("model", "")!!
        if (stored.isNotBlank()) return stored
        return defaultModel(getProvider(context))
    }

    fun defaultModel(provider: String): String =
        if (provider == PROVIDER_OPENAI) DEFAULT_MODEL_OPENAI else DEFAULT_MODEL_ANTHROPIC

    fun save(context: Context, provider: String, apiKey: String, model: String) {
        prefs(context).edit()
            .putString("provider", provider)
            .putString("apiKey", apiKey.trim())
            .putString("model", model.trim())
            .apply()
    }

    fun isConfigured(context: Context): Boolean = getApiKey(context).isNotBlank()
}
