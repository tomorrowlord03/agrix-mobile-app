# Offline Deployment & Model Distribution

## 1. The Offline Reality of Rural Agriculture

In rural agricultural heartlands (e.g. Chhattisgarh, Madhya Pradesh, Vidarbha), cellular networks are frequently 2G-only, intermittent, or absent in crop fields. A mobile AI solution that relies on cloud APIs (such as OpenAI, Gemini Cloud, or Claude) fails completely in these environments.

AgriX is engineered with a **True Offline Architecture**:
* **Zero Cloud Calls:** All inference runs on-device using quantized Gemma models via LiteRT-LM.
* **Zero Network Dependency for Features:** Crop advisory, price prediction, fertilizer scheduling, disease diagnosis, and pest control run entirely from local weights and local SQLite databases.

---

## 2. Peer-to-Peer Model Distribution (`ModelDistributionManager`)

Because quantized LLM weights range between 550 MB and 2.5 GB, farmers cannot easily download models over cellular connections. AgriX solves this via three peer-to-peer distribution pathways:

### Pathway 1: Physical MicroSD Card / USB-OTG
* Agriculture extension workers, village centers, or farmer cooperatives preload the verified `gemma-model-v1.2.task` or `.litertlm` file onto MicroSD cards or dual USB-C flash drives.
* Farmers plug in the drive and use the app's internal file selector (`importLocalFile()`).

### Pathway 2: Local Bluetooth & WiFi Direct Sideloading
* A farmer whose phone already has the model installed can act as a local distribution node.
* Using `exportModelToFile()`, the model is packaged and transferred peer-to-peer to neighboring farmer devices over WiFi Direct or high-speed Bluetooth transfers.

### Pathway 3: SHA-256 Integrity Verification
* To prevent corrupted or malicious model files from crashing the mobile NPU, `ModelDistributionManager` computes the SHA-256 hash in 64 KB blocks before moving the file to `files/models/`.
* If the checksum mismatches, the temporary download is purged, and a clear error is shown.

---

## 3. Field Deployment Checklist

- [ ] Flash ESP32 Solar Gateway with `agrix-firmware/gateway`.
- [ ] Mount solar panel facing South with a 15-20° inclination.
- [ ] Connect YF-S201 flow sensors in-line with drip irrigation and Venturi injector tubes.
- [ ] Insert 7-in-1 Modbus soil probe at root depth (15-25 cm below soil surface).
- [ ] Sideload AgriX APK and Gemma weights onto farmer devices.
- [ ] Verify BLE telemetry connection in the AgriX dashboard.
