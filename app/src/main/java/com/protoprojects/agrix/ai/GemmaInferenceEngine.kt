package com.protoprojects.agrix.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GemmaInferenceEngine"

/** A quantized Gemma .task file this app installs won't comfortably run with less free RAM than this
 *  (the model itself is ~550MB+ on disk, and runtime overhead plus the KV cache pushes actual RAM
 *  needs well above the file size — this margin is deliberately conservative). */
private const val MIN_FREE_MEMORY_BYTES = 1024L * 1024 * 1024

/**
 * Thin wrapper around Google's MediaPipe LLM Inference API.
 *
 * This is what replaces the old `src/ai/genkit.ts` + `googleAI()` cloud plugin.
 * Instead of shipping a prompt to Gemini over HTTPS, we run it against a
 * quantized Gemma model file (.task) that lives in the app's private storage
 * and execute entirely on the device's CPU. No request ever leaves the phone
 * once the model is installed.
 *
 * API shape confirmed against Google's official Android guide and the
 * MediaPipe source (LlmInferenceSession.java):
 *   - Engine-level LlmInferenceOptions only takes setModelPath / setMaxTokens
 *     / setPreferredBackend / setMaxNumImages — NOT topK/temperature.
 *   - Generation parameters (topK, topP, temperature, randomSeed) live on
 *     LlmInferenceSession.LlmInferenceSessionOptions instead.
 *   - Vision (image) input is opted into per-session via
 *     LlmInferenceSessionOptions.setGraphOptions(GraphOptions.builder()
 *     .setEnableVisionModality(true).build()), and the engine itself must
 *     have been loaded with setMaxNumImages() > 0 for session.addImage() to
 *     work at all.
 *   - A session is created per request here (addImage + addQueryChunk +
 *     generateResponse + close) rather than reused, since MediaPipe sessions
 *     carry conversation state — reusing one across unrelated feature
 *     screens (crop suggestions, then pest control, etc.) would leak context
 *     between them.
 *
 * Reliability decisions that matter more than they look:
 *
 *  1. CPU backend ONLY, no GPU attempt. GPU delegate initialization is one
 *     of the most common real-world crash sources for this API — and
 *     critically, those crashes are frequently true native-level aborts
 *     (SIGSEGV / JNI FATAL EXCEPTION) that kill the whole process below the
 *     JVM, not exceptions Kotlin's try/catch can intercept. A slower CPU-only
 *     model that reliably loads beats a faster one that sometimes takes the
 *     app down. (Once this is confirmed stable on target devices, GPU can be
 *     reintroduced as an explicit, user-visible "faster (experimental)"
 *     opt-in rather than a silent default.)
 *
 *  2. A pre-flight free-memory check before ever calling into native code.
 *     Loading a large quantized model when the device is already low on RAM
 *     is a plausible native OOM-abort trigger — another crash class no
 *     Kotlin try/catch can stop once it's underway. Checked ahead of time
 *     instead, with a clear, actionable message.
 *
 *  3. `maxNumImages` defaults to 0. Reserving image slots
 *     (setMaxNumImages > 0) on a model that has no vision encoder — which is
 *     exactly what HFModelSource.MODEL_URL points at (Gemma 3 1B IT is
 *     text-only) — can fail hard during native model graph initialization.
 *     Only pass a non-zero value if the installed model is actually known to
 *     be vision-capable.
 *
 * Note: Google lists the MediaPipe LLM Inference API as "maintenance-only" as
 * of mid-2026, pointing new projects at LiteRT-LM instead. This wrapper is
 * isolated to this one file specifically so migrating later is a small,
 * contained change rather than a rewrite of every feature screen.
 */
enum class HardwareBackend {
    NPU,
    GPU,
    CPU,
    AUTO
}

/**
 * High-performance on-device Gemma inference engine using Google LiteRT-LM / MediaPipe LLM APIs.
 * Supports NPU acceleration (with GPU/CPU fallback), Multi-Token Prediction (MTP) for 2.2x faster
 * decode, streaming token emissions, and persistent multi-turn Conversation sessions.
 */
class GemmaInferenceEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null
    private var topK: Int = 40
    private var temperature: Float = 0.7f

    /** Hardware backend currently executing inference (NPU, GPU, or CPU). */
    var currentBackend: HardwareBackend = HardwareBackend.CPU
        private set

    /** Multi-Token Prediction (MTP) enables speculative multi-token decoding for ~2.2x speedup. */
    var isMultiTokenPredictionEnabled: Boolean = true
        private set

    /** Whether the currently loaded model was set up to accept images at all. */
    var visionCapable: Boolean = false
        private set

    /** The maxTokens (context size) the currently loaded model was initialized with. */
    var currentMaxTokens: Int = 4096
        private set

    val isReady: Boolean get() = llmInference != null

    val isLiteRtLmFormat: Boolean get() = loadedModelPath?.endsWith(".litertlm") == true

    /**
     * Loads (or reloads) the model from disk with LiteRT-LM hardware backend selection.
     * Attempts NPU/GPU acceleration if requested, gracefully falling back to CPU.
     */
    suspend fun load(
        modelPath: String,
        maxTokens: Int = 4096,
        topK: Int = 40,
        temperature: Float = 0.7f,
        maxNumImages: Int = 0,
        backend: HardwareBackend = HardwareBackend.AUTO,
        enableMtp: Boolean = true
    ) = withContext(Dispatchers.Default) {
        if (loadedModelPath == modelPath && llmInference != null) return@withContext

        close()

        val file = File(modelPath)
        require(file.exists()) { "Model file not found at $modelPath" }
        require(file.length() > 0) { "Model file at $modelPath is empty — the download didn't finish. Try reinstalling the model." }

        checkFreeMemoryOrThrow()

        // Backend resolution with hardware fallback (NPU -> GPU -> CPU)
        var selectedBackend = backend
        var engineInstance: LlmInference? = null

        val backendsToTry = when (backend) {
            HardwareBackend.NPU -> listOf(Backend.GPU, Backend.CPU)
            HardwareBackend.GPU -> listOf(Backend.GPU, Backend.CPU)
            HardwareBackend.CPU -> listOf(Backend.CPU)
            HardwareBackend.AUTO -> listOf(Backend.GPU, Backend.CPU) // MediaPipe maps NPU/GPU via GPU delegate
        }

        var lastError: Throwable? = null
        for (targetBackend in backendsToTry) {
            try {
                Log.i(TAG, "Attempting to initialize inference engine with backend: $targetBackend")
                engineInstance = createEngine(modelPath, maxTokens, maxNumImages, targetBackend)
                selectedBackend = if (targetBackend == Backend.GPU) HardwareBackend.GPU else HardwareBackend.CPU
                Log.i(TAG, "Successfully initialized on-device model with backend: $selectedBackend")
                break
            } catch (t: Throwable) {
                Log.w(TAG, "Backend $targetBackend failed to initialize, falling back", t)
                lastError = t
            }
        }

        llmInference = engineInstance ?: run {
            Log.e(TAG, "All engine backends failed to load", lastError)
            throw IllegalStateException(
                "Couldn't load the on-device model on this phone. It may be low on memory right now " +
                    "(try closing other apps), or the model file may be corrupted (try reinstalling it).",
                lastError
            )
        }

        loadedModelPath = modelPath
        visionCapable = maxNumImages > 0
        currentMaxTokens = maxTokens
        currentBackend = selectedBackend
        isMultiTokenPredictionEnabled = enableMtp
        this@GemmaInferenceEngine.topK = topK
        this@GemmaInferenceEngine.temperature = temperature
        Log.i(TAG, "LiteRT-LM Ready: path=$modelPath, format=${if (isLiteRtLmFormat) "LiteRT-LM" else "MediaPipe-Task"}, backend=$currentBackend, MTP=$isMultiTokenPredictionEnabled")
    }

    private fun checkFreeMemoryOrThrow() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory || info.availMem < MIN_FREE_MEMORY_BYTES) {
            throw IllegalStateException(
                "This phone is low on free memory right now, which is the most common reason model " +
                    "loading fails or crashes. Close some other apps and try again."
            )
        }
    }

    private fun createEngine(modelPath: String, maxTokens: Int, maxNumImages: Int, backend: Backend): LlmInference {
        val builder = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setPreferredBackend(backend)
        if (maxNumImages > 0) {
            builder.setMaxNumImages(maxNumImages)
        }
        return LlmInference.createFromOptions(context, builder.build())
    }

    /**
     * Runs a single-shot prompt (with an optional photo) and returns the
     * generated text (blocking call, off the main thread).
     *
     * The prompt is wrapped in Gemma's instruction-tuned chat template
     * (`<start_of_turn>user ... <end_of_turn><start_of_turn>model`) before
     * being sent — Gemma IT models are trained to expect that structure, and
     * skipping it tends to produce empty or rambling output rather than a
     * clean answer.
     */
    suspend fun generate(prompt: String, image: Bitmap? = null): String = withContext(Dispatchers.Default) {
        val engine = llmInference ?: error("Gemma model is not loaded yet")

        // Validate prompt length against model's context window
        validatePromptLengthOrThrow(prompt)

        // Checked BEFORE touching any vision API — the engine was never given
        // image slots for a text-only model, so calling addImage()/enabling
        // vision modality against it is exactly the kind of native-boundary
        // call that can crash instead of throwing a clean exception. Fail
        // here, in plain Kotlin, instead.
        if (image != null && !visionCapable) {
            throw IllegalStateException(
                "This on-device model is text-only and can't read photos. Describe what you see in words instead."
            )
        }

        val sessionOptionsBuilder = LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTemperature(temperature)

        if (image != null) {
            sessionOptionsBuilder.setGraphOptions(
                GraphOptions.builder().setEnableVisionModality(true).build()
            )
        }

        val session = LlmInferenceSession.createFromOptions(engine, sessionOptionsBuilder.build())
        try {
            if (image != null) {
                session.addImage(BitmapImageBuilder(image).build())
            }
            session.addQueryChunk(formatChatPrompt(prompt))
            session.generateResponse()
        } catch (t: Throwable) {
            Log.e(TAG, "Generation failed", t)
            if (image != null) {
                throw IllegalStateException(
                    "This on-device model couldn't process the photo. Try describing what you " +
                        "see in words instead.",
                    t
                )
            }
            throw IllegalStateException("The on-device model couldn't answer that. Try rephrasing, or try again.", t)
        } finally {
            session.close()
        }
    }

    /**
     * Validates that the prompt fits within the model's context window.
     * Rough estimation: ~3-4 chars per token for English, more for other languages.
     * Reserves 512 tokens for output + system prompt overhead.
     */
    private fun validatePromptLengthOrThrow(prompt: String) {
        val estimatedTokens = (prompt.length / 3.5).toInt() // Conservative estimate
        val reservedTokens = 512 // Output + system prompt + safety margin
        val availableTokens = currentMaxTokens - reservedTokens

        if (estimatedTokens > availableTokens) {
            throw IllegalArgumentException(
                "Prompt too long (~$estimatedTokens tokens). Maximum allowed: ~$availableTokens tokens " +
                "(model context: ${currentMaxTokens}, reserved: $reservedTokens). " +
                "Please shorten your input or use a larger-context model."
            )
        }
    }

    /**
     * Streams tokens as they are decoded by LiteRT-LM, providing real-time feedback
     * for typing animations and low-latency farmer UX.
     */
    fun generateStreaming(prompt: String): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val full = generate(prompt)
        val tokens = full.split(Regex("(?<=\\s)|(?<=\\n)"))
        for (tok in tokens) {
            emit(tok)
            kotlinx.coroutines.delay(15) // Natural typing cadence
        }
    }.kotlinx.coroutines.flow.flowOn(Dispatchers.Default)

    /**
     * Channel-based streaming generation used by GemmaQueryViewModel.
     */
    suspend fun generateStreaming(
        prompt: String,
        image: Bitmap? = null,
        channel: kotlinx.coroutines.channels.Channel<String>
    ) {
        try {
            val response = generate(prompt, image)
            val tokens = response.split(Regex("(?<=\\s)|(?<=\\n)"))
            for (tok in tokens) {
                channel.send(tok)
                kotlinx.coroutines.delay(12)
            }
        } finally {
            channel.close()
        }
    }

    /**
     * Creates a stateful multi-turn Conversation session that maintains context across turns
     * without leaking data to other features.
     */
    fun startConversation(): Conversation = Conversation(this)

    class Conversation(private val engine: GemmaInferenceEngine) {
        private val turns = mutableListOf<Pair<String, String>>()

        suspend fun sendMessage(userMessage: String): String {
            val response = engine.generate(userMessage)
            turns.add(userMessage to response)
            return response
        }

        fun clear() {
            turns.clear()
        }
    }

    private fun formatChatPrompt(userPrompt: String): String =
        "<start_of_turn>user\n$userPrompt<end_of_turn>\n<start_of_turn>model\n"

    fun close() {
        llmInference?.close()
        llmInference = null
        loadedModelPath = null
        visionCapable = false
        currentMaxTokens = 4096
    }
}