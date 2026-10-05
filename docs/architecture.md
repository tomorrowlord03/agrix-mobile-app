# AgriX Offline System Architecture

## 1. High-Level System Architecture

AgriX is a completely offline-first precision agriculture platform designed for Indian smallholder farmers operating in remote environments with zero or intermittent internet connectivity.

```mermaid
flowchart TD
    subgraph FIELD_NODES["In-Field Sensor Nodes (Solar/Battery)"]
        N1["Sensor Node 1 (Irrigation Line)\n• YF-S201 Water Flow\n• YF-S201 Fertilizer Dosing"]
        N2["Sensor Node 2 (Soil Bed)\n• RS485 Modbus 7-in-1\n• NPK + pH + EC + Moisture"]
    end

    subgraph GATEWAY["AgriX ESP32 Solar Gateway"]
        GW_RX["SX1276 LoRa (868MHz, 20dBm)\n+ ESP-NOW Dual Receiver"]
        GW_PWR["INA219 Solar & Battery Monitor"]
        GW_TX["BLE GATT Server\n+ WiFi SoftAP (192.168.4.1)"]
        GW_RX --> GW_TX
        GW_PWR -.-> GW_TX
    end

    subgraph MOBILE_APP["AgriX Android Application"]
        subgraph IOT_LAYER["IoT & Data Layer"]
            IOT_MGR["IoTGatewayManager\n(BLE / WiFi Direct / USB-OTG)"]
            REPO["SensorDataRepository\n(Local SQLite Time-Series)"]
            DIST["ModelDistributionManager\n(P2P / SD Card / SHA-256)"]
        end

        subgraph AI_LAYER["Edge Inference Engine"]
            PROMPTS["PromptTemplates\n(Sensor-Integrated Agronomy)"]
            LITERT["GemmaInferenceEngine\n• Google LiteRT-LM\n• NPU Hardware Acceleration\n• Multi-Token Prediction (MTP)\n• 4096-Token KV Cache"]
            VM["GemmaQueryViewModel\n(Streaming StateFlow + Validation)"]
        end

        subgraph UI_LAYER["Compose UI Layer"]
            DASH["Precision Farming Dashboard"]
            SCREENS["Advisory Screens\n• Irrigation & Fertigation\n• Crop Suggestion\n• Pest & Disease Detector"]
            STREAM["ResultArea\n(Live Token Streaming Animation)"]
        end

        IOT_MGR --> REPO
        IOT_MGR --> PROMPTS
        REPO --> PROMPTS
        PROMPTS --> VM
        VM --> LITERT
        LITERT --> VM
        VM --> UI_LAYER
    end

    N1 -->|LoRa 868MHz / ESP-NOW| GW_RX
    N2 -->|LoRa 868MHz / ESP-NOW| GW_RX
    GW_TX -->|BLE Notifications / HTTP REST| IOT_MGR
```

---

## 2. Core Subsystems

### A. Edge AI Inference Engine (`com.protoprojects.agrix.ai`)
* **Model:** Gemma 3 1B Instruction-Tuned (int4-quantized, 4096 context window).
* **Runtime:** Google LiteRT-LM (with fallback to MediaPipe LLM Inference).
* **Hardware Acceleration:** Native NPU delegation with automatic GPU and CPU fallbacks.
* **Speedup Optimization:** Speculative Multi-Token Prediction (MTP) delivering ~2.2x faster token decode on mobile chips.
* **Context Preservation:** Stateful `Conversation` API allowing multi-turn agronomy dialogues without leaking state across unrelated features.

### B. IoT Gateway & Field Sensors (`com.protoprojects.agrix.iot`)
* **Transport:** Dual-mode Bluetooth Low Energy (GATT streaming) and local WiFi SoftAP (`http://192.168.4.1/telemetry`).
* **Sensor Capabilities:**
  - Water flow rate and cumulative volume (YF-S201).
  - Fertilizer dosing rate and cumulative volume (YF-S201).
  - Soil Nitrogen, Phosphorus, Potassium (NPK probe via RS485 Modbus).
  - Soil pH, electrical conductivity (EC), moisture %, and soil temperature.

### C. Local Persistence (`com.protoprojects.agrix.data`)
* **Sensor History:** SQLite time-series database (`SensorDataRepository`) for recording telemetry and computing trend lines.
* **Farmer Profile:** Preferences DataStore storing farm dimensions, soil baseline, primary crops, and language preference.
* **Model Distribution:** Local file storage (`models/`) managed by `ModelDistributionManager` supporting offline sideloading and P2P sharing with SHA-256 verification.
