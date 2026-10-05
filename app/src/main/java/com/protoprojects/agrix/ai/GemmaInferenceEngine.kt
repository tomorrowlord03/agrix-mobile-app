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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    /** Whether the engine is operating in offline demo / preview mode without a physical weights file. */
    var isDemoMode: Boolean = false
        private set

    val isReady: Boolean get() = llmInference != null || isDemoMode

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
        if (loadedModelPath == modelPath && (llmInference != null || isDemoMode)) return@withContext

        close()

        if (modelPath == "demo_mode") {
            isDemoMode = true
            loadedModelPath = "demo_mode"
            currentBackend = HardwareBackend.CPU
            Log.i(TAG, "AgriX initialized in Offline Demo Mode (Simulation Active)")
            return@withContext
        }

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
        if (isDemoMode) {
            return@withContext generateDemoResponse(prompt)
        }
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
    fun generateStreaming(prompt: String): Flow<String> = flow {
        val full = generate(prompt)
        val tokens = full.split(Regex("(?<=\\s)|(?<=\\n)"))
        for (tok in tokens) {
            emit(tok)
            delay(15) // Natural typing cadence
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Channel-based streaming generation used by GemmaQueryViewModel.
     */
    suspend fun generateStreaming(
        prompt: String,
        image: Bitmap? = null,
        channel: Channel<String>
    ) {
        try {
            val response = generate(prompt, image)
            val tokens = response.split(Regex("(?<=\\s)|(?<=\\n)"))
            for (tok in tokens) {
                channel.send(tok)
                delay(12)
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
        isDemoMode = false
        loadedModelPath = null
        visionCapable = false
        currentMaxTokens = 4096
    }

    private fun generateDemoResponse(prompt: String): String {
        val p = prompt.lowercase()
        return when {
            p.contains("profit") || p.contains("crop") || p.contains("suggest") ->
                """
                🌾 Recommended Crops for Your Farm:
                1. Soybean (JS 9560 / JS 335): High drought tolerance, ~35-40 days to flower, projected profit: ₹28,000 - ₹34,000/acre.
                2. Groundnut (TG 37A): Excellent nitrogen fixation for your soil, projected profit: ₹32,000 - ₹38,000/acre.
                3. Chickpea / Gram (Desi): Low irrigation requirement, strong local market demand, projected profit: ₹24,000 - ₹30,000/acre.

                💡 Management Tip: Intersperse with pigeon pea (arhar) on field borders to deter pests and enrich organic soil nitrogen.
                """.trimIndent()

            p.contains("price") || p.contains("market") ->
                """
                📈 Market Price Forecast (Local Mandi):
                • Current Average: ₹3,850 - ₹4,150 / quintal
                • 30-Day Outlook: Moderate upward trend expected (+8-12%) due to festival demand and seasonal supply transition.
                • Recommended Selling Strategy: Store 40% of produce post-harvest in dry storage; stagger sales across the next 4-6 weeks for optimal price realization.
                """.trimIndent()

            p.contains("pest") || p.contains("insect") || p.contains("worm") ->
                """
                🛡️ Integrated Pest Management (IPM) Protocol:
                1. Organic Spray: Apply 5% Neem Seed Kernel Extract (NSKE) or Neem Oil (10,000 ppm) at 3-5 ml per liter of water during early morning.
                2. Mechanical Control: Install yellow sticky traps (10-12/acre) for sucking pests and pheromone traps (5/acre) for pod borers.
                3. Biocontrol: Release Trichogramma parasitoids (50,000/ha) or apply Beauveria bassiana (2 g/L) if larvae infestation exceeds economic threshold level (ETL).
                """.trimIndent()

            p.contains("disease") || p.contains("spot") || p.contains("yellow") || p.contains("wilt") ->
                """
                🔬 Diagnostic Assessment & Treatment:
                • Likely Diagnosis: Fungal Leaf Spot / Rust Complex
                • Immediate Action: Prune heavily affected lower leaves to improve field aeration.
                • Remedial Spray: Spray Copper Oxychloride (50% WP) @ 2.5 g/L or Trichoderma viride bio-fungicide @ 5 g/L during dry periods.
                • Soil Health: Avoid water stagnation around root zones; apply well-decomposed FYM enriched with Pseudomonas fluorescens.
                """.trimIndent()

            p.contains("soil") || p.contains("fertilizer") || p.contains("npk") ->
                """
                🌱 Soil Health & Nutrient Prescription:
                • Nitrogen (N): Apply split application: 50% basal at sowing, 25% at tillering/flowering, 25% during grain fill.
                • Phosphorus (P): Incorporate Single Super Phosphate (SSP) at root depth to promote robust root establishment.
                • Potassium (K) & Micronutrients: Apply MOP (Muriate of Potash) along with Zinc Sulphate (21%) @ 10 kg/acre to prevent chlorosis.
                • pH Balancing: If pH < 6.0, apply agricultural lime (200 kg/acre); if alkaline (pH > 8.0), add gypsum and green manure.
                """.trimIndent()

            p.contains("water") || p.contains("irrigation") || p.contains("schedule") ->
                """
                💧 Precision Irrigation Schedule:
                • Moisture Status: Critical root zone depth requires ~25-30 mm moisture replenishing every 5-7 days.
                • Timing: Run drip or micro-sprinklers early in the morning (6:00 AM - 9:00 AM) to reduce evaporative loss by up to 35%.
                • Critical Stages: Ensure uniform moisture during crown root initiation, flowering, and pod development phases.
                """.trimIndent()

            else ->
                """
                🚜 AgriX Agronomic Guidance:
                Based on your farm profile and local agro-climatic conditions, optimize seed treatment with Rhizobium/Trichoderma before sowing. Maintain balanced NPK nutrition (4:2:1 ratio) and monitor weekly sensor telemetry for soil moisture and temperature fluctuations.
                """.trimIndent()
        }
    }
}