# ESP32 Solar Gateway Firmware

## 1. System Overview

The **AgriX Solar Gateway** acts as the central field hub connecting distributed wireless sensor nodes to the farmer's offline mobile device. It is designed to run self-sufficiently from a 5W-10W solar panel and 18650 Li-ion battery pack.

```
[ LoRa SX1276 (868MHz) ] ──┐
                           ├──► [ ESP32 Dual Core ] ──► [ BLE GATT / WiFi SoftAP ] ──► [ AgriX App ]
[ ESP-NOW (2.4GHz) ]     ──┘            │
                                        ▼
                           [ INA219 Solar & Battery ]
```

---

## 2. Firmware Architecture (`agrix-firmware/gateway`)

* **Framework:** Arduino / ESP-IDF on PlatformIO (`platform = espressif32`).
* **Source Location:** `agrix-firmware/gateway/src/main.cpp`.

### Key Components

1. **Dual Wireless Ingestion Engine**:
   - **ESP-NOW**: Ingests packets from nearby (<200m) high-frequency sensor nodes with zero connection handshake latency.
   - **LoRa (SX1276 @ 868MHz)**: Provides long-range link (2km-10km) across open fields and dense crop foliage using high link budget and CRC verification.

2. **Mobile Connectivity**:
   - **BLE Server**: Advertises service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b`. Telemetry characteristic `beb5483e-36e1-4688-b7f5-ea07361b26a8` pushes live telemetry updates via BLE notifications.
   - **WiFi SoftAP**: Starts an open access point `AgriX-SolarGateway`. A lightweight embedded web server handles HTTP GET requests at `/telemetry`, returning the JSON payload.

3. **Solar & Battery Power Telemetry**:
   - Interfaces via I2C with the Texas Instruments **INA219** current/power monitor.
   - Captures solar panel bus voltage, current in mA, and battery capacity, sending power telemetry alongside sensor values.

4. **ArduinoOTA Updates**:
   - Supports wireless over-the-air firmware upgrades without physical disassembly in the field.

---

## 3. JSON Telemetry Format

The gateway broadcasts telemetry matching `IoTGatewayManager.kt`:

```json
{
  "water_lpm": 12.4,
  "water_total": 1540.0,
  "fert_lpm": 0.82,
  "fert_total": 45.2,
  "moist": 38.5,
  "ph": 6.8,
  "ec": 820.0,
  "n": 145.0,
  "p": 42.0,
  "k": 188.0,
  "temp": 28.4,
  "hum": 65.0,
  "batt": 4.12,
  "solar_v": 5.25,
  "solar_ma": 340.0,
  "rssi": -68,
  "rx_count": 1240
}
```

---

## 4. Build and Flash Instructions

```bash
cd agrix-firmware/gateway
pio run
pio run --target upload
```
