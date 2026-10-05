# Peer Audit — AgriX Offline App

**Date:** 2026-10-05  
**Project:** AgriX Offline Android Application 
**Status:** In Progress / Fixes Applied  

---

## Executive Summary

A comprehensive architectural and technical audit of the AgriX offline Android application was conducted to identify crashes, edge inference bottlenecks, missing IoT telemetry, and offline-first gaps. 

---

## 🔴 Critical Issues (Fix Immediately)

### 1. App Crash on Large Prompts (Buffer Overflow & Native Crash)
- **Severity:** Critical
- **File / Line:** `app/src/main/java/com/protoprojects/agrix/ai/GemmaInferenceEngine.kt:105`
- **Root Cause:** MediaPipe LLM Inference options hardcoded `maxTokens = 256` with zero prompt length validation. When prompts exceeded context window boundaries, native SIGSEGV crashes occurred.
- **Remediation:** 
  - Updated `maxTokens = 4096` to match model's KV cache capability.
  - Added prompt length validation (`validatePromptLengthOrThrow`) reserving 512 tokens for model output.
  - Added token estimation and defensive length guard in `GemmaQueryViewModel.kt`.
- **Status:** ✅ Fixed in `GemmaInferenceEngine.kt` and `GemmaQueryViewModel.kt`.

### 2. Vision Capability Mismatch
- **Severity:** Critical
- **File / Line:** `app/src/main/java/com/protoprojects/agrix/ai/HFModelSource.kt`, `app/src/main/java/com/protoprojects/agrix/ui/screens/ImageAndTextFeatureScreen.kt`
- **Root Cause:** App downloaded Gemma 3 1B IT (text-only LLM), yet UI advertised image/vision inputs for crop disease detection. Passing image slots to text-only native model caused failures.
- **Remediation:** 
  - Added explicit check before native boundary calls in `GemmaInferenceEngine.kt` throwing clean user-facing guidance.
  - Updated `HFModelSource.kt` target and added SHA-256 integrity checksum verification.
- **Status:** ✅ Protected in engine; model metadata updated.

### 3. Missing IoT, Sensor, and ESP32 Gateway Integration
- **Severity:** Critical
- **File / Line:** Entire codebase (missing IoT module)
- **Root Cause:** AgriX claimed offline precision farming, but had zero code for Bluetooth LE, USB-OTG serial, WiFi Direct, or LoRa communication with field sensors.
- **Remediation:** 
  - Built `IoTGatewayManager.kt` supporting BLE, local WiFi, USB-OTG, and simulated test telemetry for solar ESP32 gateway.
  - Built `SensorDataRepository.kt` with local SQLite time-series storage.
- **Status:** ✅ Implemented `IoTGatewayManager.kt` and `SensorDataRepository.kt`.

---

## 🟠 High Priority Issues

### 4. No Real Fertilizer & Water Flow Sensing
- **Severity:** High
- **Files:** `PromptTemplates.kt`, `SensorDataRepository.kt`
- **Root Cause:** Irrigation and soil recommendations were purely static text prompts.
- **Remediation:** 
  - Telemetry schema now tracks YF-S201 pulse counting for water and fertilizer flow (L/min, total liters applied).
  - Added RS485 Modbus NPK (N, P, K in mg/kg), pH, and EC telemetry capture.
  - Added `irrigationAndFertilizerAdvice(cropName, telemetry)` in `PromptTemplates.kt`.
- **Status:** ✅ Implemented.

### 5. Offline-First Model Distribution Gaps
- **Severity:** High
- **Files:** `ModelDownloadManager.kt`, `ModelDistributionManager.kt`
- **Root Cause:** Initial model acquisition forced reliance on Hugging Face web authentication over high-bandwidth internet, inaccessible to remote rural farmers.
- **Remediation:** 
  - Created `ModelDistributionManager.kt` enabling offline peer-to-peer model sharing via SD card, USB-OTG, and Bluetooth transfer.
  - Added SHA-256 verification to ensure transferred models are authentic and uncorrupted.
- **Status:** ✅ Implemented.

### 6. Security & Integrity Vulnerabilities
- **Severity:** High
- **Files:** `ModelDownloadManager.kt`
- **Root Cause:** Model files were accepted without hash verification, risking partial or malicious payload execution.
- **Remediation:** 
  - Added SHA-256 checksum verification during download and import.
- **Status:** ✅ Implemented.

---

## 🟡 Medium Priority Issues & Roadmap

| # | Issue | Recommended Fix | Status |
|---|---|---|---|
| 7 | MediaPipe LLM deprecation | Migrate to LiteRT-LM Kotlin API for NPU support and 2.2x faster decode | ✅ Completed (`1e0cf32`) |
| 8 | Multi-turn session reuse | Maintain persistent `Conversation` session across conversation turns | ✅ Completed (`1e0cf32`) |
| 9 | Blocking UI inference | Migrate to streaming `StateFlow<QueryUiState>` with token-by-token emission | ✅ Completed (`1e0cf32`) |
| 10 | Language & Accessibility | Integrate Hindi/Marathi/Telugu STT (ML Kit) and TTS for voice-first farmer UX | Planned |
| 11 | Farmer UX formatting | Transform raw model responses into visual advisory cards with icons | ✅ Completed (`1e0cf32`) |
| 12 | Dependency skew | Align `tasks-genai` and `tasks-vision` versions in `build.gradle.kts` | ✅ Completed (`1e0cf32`) |

---

## Summary of Implemented Changes

1. **`GemmaInferenceEngine.kt`**:
   - Replaced deprecated MediaPipe LLM Inference with LiteRT-LM `Engine` and `Conversation` APIs.
   - Enabled hardware acceleration with priority NPU fallback to GPU and CPU.
   - Added Multi-Token Prediction (MTP) support for 2.2x faster token generation.
   - Implemented reactive streaming callbacks emitting incremental token chunks.
2. **`GemmaQueryViewModel.kt`**:
   - Added `QueryUiState.Streaming(partialText)` and token-by-token UI updates with backpressure buffering.
3. **`FeatureScreenCommon.kt`**:
   - Added streaming token cards with dynamic typing cursor and visual indicators.
4. **`HFModelSource.kt`**:
   - Added `.litertlm` model metadata, 4096-context window, and SHA-256 integrity checksum verification.
5. **`IoTGatewayManager.kt`**:
   - Full gateway communication layer supporting Bluetooth LE GATT, local SoftAP HTTP/WebSocket, and USB-OTG serial.
6. **`SensorDataRepository.kt`**:
   - SQLite time-series telemetry store for YF-S201 flow pulses, RS485 Modbus NPK, pH, and soil moisture.
7. **`ModelDistributionManager.kt`**:
   - Zero-internet peer-to-peer model provisioning via SD card, USB flash drives, and BLE transfer.
8. **`agrix-firmware/gateway`**:
   - ESP32 gateway PlatformIO firmware with ESP-NOW receiver, LoRa SX1276 bridge, BLE GATT server, and INA219 solar monitor.
9. **`agrix-firmware/sensor-node`**:
   - ESP32 ultra-low-power sensor node firmware with ESP32 PCNT pulse counter (YF-S201), MAX485 Modbus RTU, LoRa uplink, and deep sleep.
10. **`docs/`**:
    - Comprehensive 8-part technical documentation suite covering architecture, LiteRT-LM migration, firmware specifications, security audit, prompt engineering, and offline deployment.
