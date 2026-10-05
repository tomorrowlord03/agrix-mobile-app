package com.protoprojects.agrix.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ai.DownloadState
import com.protoprojects.agrix.ai.HFModelSource
import com.protoprojects.agrix.ai.ModelDownloadManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Whether the model file that finished downloading has actually been loaded into memory yet. */
sealed class ModelLoadState {
    object NotStarted : ModelLoadState()
    object Loading : ModelLoadState()
    object Ready : ModelLoadState()
    data class Failed(val message: String) : ModelLoadState()
}

/** Loading a multi-hundred-MB model into memory on a phone CPU can genuinely take a while — this is a ceiling, not a target. */
private const val MODEL_LOAD_TIMEOUT_MS = 120_000L

class ModelSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotStarted)
    val loadState: StateFlow<ModelLoadState> = _loadState

    private val downloadManager = ModelDownloadManager(app)
    private val agriXApp get() = getApplication<AgriXApp>()

    /** Called once the Hugging Face sign-in WebView reports a session cookie. */
    fun downloadAfterSignIn(cookieHeader: String) {
        _downloadState.value = null
        _loadState.value = ModelLoadState.NotStarted
        viewModelScope.launch {
            downloadManager.downloadFromUrl(
                url = HFModelSource.MODEL_URL,
                fileName = HFModelSource.MODEL_FILE_NAME,
                cookieHeader = cookieHeader
            ).collect { _downloadState.value = it }
        }
    }

    /** Fallback path: farmer already has a .task file on their device and picks it manually. */
    fun importLocalFile(uri: Uri) {
        _downloadState.value = null
        _loadState.value = ModelLoadState.NotStarted
        viewModelScope.launch {
            downloadManager.importLocalFile(uri, HFModelSource.MODEL_FILE_NAME).collect { _downloadState.value = it }
        }
    }

    fun resetState() {
        _downloadState.value = null
        _loadState.value = ModelLoadState.NotStarted
    }

    /** Instant offline demo mode: allows immediate exploration without downloading 550MB model or Hugging Face sign-in. */
    fun launchDemoMode() {
        _downloadState.value = null
        _loadState.value = ModelLoadState.Loading
        viewModelScope.launch {
            try {
                agriXApp.gemma.load("demo_mode")
                agriXApp.prefs.setModelInstalled("demo_mode")
                _loadState.value = ModelLoadState.Ready
            } catch (t: Throwable) {
                _loadState.value = ModelLoadState.Failed(t.message ?: "Failed to start demo mode")
            }
        }
    }

    /**
     * Loads the freshly installed model into the inference engine and persists
     * its path. This is a real, awaitable step: the setup screen should only
     * advance to onboarding once [loadState] reaches [ModelLoadState.Ready],
     * not the instant the download finishes — otherwise a farmer can land on
     * a feature screen and submit a query before the model has actually
     * finished loading into memory, which is what used to produce no output.
     *
     * Wrapped in a timeout so a genuine hang (rather than a clean failure)
     * still surfaces as a recoverable error state instead of leaving the
     * setup screen stuck on its loading spinner forever.
     *
     * Safe to call more than once (e.g. from a recomposition) — it no-ops if
     * loading already started or finished.
     */
    fun finishSetup() {
        if (_loadState.value is ModelLoadState.Loading || _loadState.value is ModelLoadState.Ready) return
        val done = _downloadState.value as? DownloadState.Done ?: return

        _loadState.value = ModelLoadState.Loading
        viewModelScope.launch {
            try {
                withTimeout(MODEL_LOAD_TIMEOUT_MS) {
                    agriXApp.gemma.load(done.path)
                }
                agriXApp.prefs.setModelInstalled(done.path)
                _loadState.value = ModelLoadState.Ready
            } catch (e: TimeoutCancellationException) {
                agriXApp.gemma.close()
                _loadState.value = ModelLoadState.Failed(
                    "Loading the model took too long and was stopped. This phone may not have enough " +
                        "free memory right now — try closing other apps and retrying."
                )
            } catch (t: Throwable) {
                // Throwable, not Exception: OutOfMemoryError and similar JVM-level
                // Errors are catchable and MUST be caught here — otherwise a
                // low-memory failure during model load crashes the whole app
                // instead of showing a recoverable "try again" message. (A true
                // native-level crash below the JVM can't be caught by any Kotlin
                // code — GemmaInferenceEngine.load() is written to avoid the
                // known triggers for that instead: see its class doc comment.)
                agriXApp.gemma.close()
                _loadState.value = ModelLoadState.Failed(t.message ?: "Couldn't load the model into memory")
            }
        }
    }
}
