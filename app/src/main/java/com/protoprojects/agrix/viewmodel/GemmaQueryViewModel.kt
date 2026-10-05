package com.protoprojects.agrix.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ai.JsonExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class QueryUiState {
    object Idle : QueryUiState()
    object Loading : QueryUiState()
    data class Success(val raw: String, val json: JSONObject?) : QueryUiState()
    data class Error(val message: String) : QueryUiState()
}

/**
 * Generic "send a prompt (with an optional photo) to the on-device Gemma
 * model, get structured JSON back" ViewModel. Every feature screen (crop
 * suggester, price predictor, pest control, etc.) reuses this instead of
 * talking to any network API.
 */
class GemmaQueryViewModel(app: Application) : AndroidViewModel(app) {

    private val agriXApp get() = getApplication<AgriXApp>()

    private val _state = MutableStateFlow<QueryUiState>(QueryUiState.Idle)
    val state: StateFlow<QueryUiState> = _state

    fun ask(prompt: String, image: Bitmap? = null) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            _state.value = QueryUiState.Error("Please enter a question or prompt.")
            return
        }

        // Token estimation (~3.5 chars per token for conservative check)
        val estimatedTokens = (trimmedPrompt.length / 3.5).toInt()
        val maxTokens = agriXApp.gemma.currentMaxTokens
        val maxAllowed = maxTokens - 512
        if (estimatedTokens > maxAllowed) {
            _state.value = QueryUiState.Error(
                "Your prompt is too long (~$estimatedTokens tokens). The maximum allowed input is ~$maxAllowed tokens. Please shorten your question."
            )
            return
        }

        _state.value = QueryUiState.Loading
        viewModelScope.launch {
            try {
                check(agriXApp.gemma.isReady) { "Model isn't loaded yet — go back to the dashboard and wait a moment" }
                val (raw, json) = generateWithJsonRetry(trimmedPrompt, image)
                _state.value = QueryUiState.Success(raw, json)
            } catch (t: Throwable) {
                _state.value = QueryUiState.Error(t.message ?: "Something went wrong running the on-device model")
            }
        }
    }

    /**
     * Runs the prompt and, if the model didn't return parseable JSON on the
     * first try, retries exactly once with a sharper reminder appended.
     * Small on-device models occasionally wrap the answer in a sentence
     * despite instructions — one retry recovers most of those cases without
     * doubling latency on the common path where the first answer is clean.
     */
    private suspend fun generateWithJsonRetry(prompt: String, image: Bitmap?): Pair<String, JSONObject?> {
        val firstRaw = agriXApp.gemma.generate(prompt, image)
        val firstJson = JsonExtractor.extractJson(firstRaw)
        if (firstJson != null) return firstRaw to firstJson

        val retryPrompt = "$prompt\n\nYour previous answer wasn't valid JSON. Reply with ONLY the JSON object this time — no sentence before or after it, no markdown fences."
        val retryRaw = agriXApp.gemma.generate(retryPrompt, image)
        val retryJson = JsonExtractor.extractJson(retryRaw)
        return retryRaw to retryJson
    }

    fun reset() {
        _state.value = QueryUiState.Idle
    }
}
