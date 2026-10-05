# Security & Model Integrity Audit

## 1. Vulnerability Assessment & Remediation

During the October peer audit of the AgriX codebase, critical vulnerabilities and hardening requirements were evaluated:

### Vulnerability 1: Native SIGSEGV Buffer Overflow on Unbounded Prompts
* **Risk:** The MediaPipe/LiteRT native C++ inference library crashed with native SIGSEGV when input token counts exceeded the allocated context buffer (`maxTokens = 256`).
* **Exploitability:** Farmers or automated sensor inputs entering detailed reports caused abrupt application termination without catching Java exceptions.
* **Remediation:** 
  1. Set `maxTokens = 4096` to match the model's baked KV cache.
  2. Implemented `validatePromptLengthOrThrow()` in `GemmaInferenceEngine.kt`, estimating token consumption (~3.5 chars/token) and reserving 512 tokens for output + system overhead.
  3. Added input length validation and token bounds checking in `GemmaQueryViewModel.kt`.

### Vulnerability 2: Unverified Model Execution
* **Risk:** Model weights were written directly to disk and initialized without hash verification, opening vectors for truncated downloads, partial file corruption, or tampered native model graphs.
* **Remediation:** Enforced SHA-256 verification in both `ModelDownloadManager.kt` and `ModelDistributionManager.kt` before any model is moved into active private app storage.

### Vulnerability 3: Native Vision Modality Desynchronization
* **Risk:** Passing bitmap image pointers to a text-only Gemma 1B model caused native JNI aborts.
* **Remediation:** Explicit `visionCapable` boolean flag checked in pure Kotlin before touching any native JNI boundary in `GemmaInferenceEngine.kt`.

---

## 2. Best Practices for Mobile On-Device AI

1. **Storage Isolation:** Models are stored exclusively in internal app-private storage (`context.filesDir/models/`) with `MODE_PRIVATE` permissions, inaccessible to other apps without root access.
2. **Encrypted Storage:** Farmer profiles and sensor records are secured locally via Android DataStore and SQLite with optional SQLCipher encryption.
3. **Hardware Boundary Defense:** All native engine operations validate memory limits (`checkFreeMemoryOrThrow`) using `ActivityManager.MemoryInfo` to prevent OOM aborts.
