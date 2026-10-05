# AgriX ESP32 Field Hardware & Firmware

This directory contains the PlatformIO projects for the AgriX offline precision agriculture hardware subsystem:

1. **`gateway/`**: Solar-powered ESP32 central gateway deployed in the field.
2. **`sensor-node/`**: Ultra-low-power ESP32 sensor nodes connected to irrigation lines and soil probes.

---

## Architecture Overview

```
[ Sensor Node 1 ] ──(LoRa 868MHz / ESP-NOW)──┐
  • YF-S201 Water Flow                       │
  • YF-S201 Fert Flow                        ▼
  • RS485 Modbus 7-in-1 Soil Probe   [ Solar ESP32 Gateway ] ──(BLE / SoftAP WiFi)──► [ AgriX Android App ]
                                       • INA219 Solar Monitor                             • On-device Gemma LLM
[ Sensor Node N ] ──(LoRa 868MHz / ESP-NOW)──┘ • SX1276 LoRa Bridge                              • IoTGatewayManager
  • Range: Up to 10 km                         • SoftAP Server: 192.168.4.1/telemetry             • Precision Advisory
```

---

## 1. Gateway Firmware (`agrix-firmware/gateway`)

### Features
- **ESP-NOW & LoRa SX1276 Multi-Protocol Bridge**: Receives telemetry from nearby nodes (<200m via ESP-NOW) and remote nodes (up to 10km via LoRa 868MHz).
- **Dual Offline Phone Connectivity**:
  - **BLE GATT Service**: `4fafc201-1fb5-459e-8fcc-c5c9c331914b` with notification streaming.
  - **WiFi SoftAP**: SSID `AgriX-SolarGateway`, serves REST JSON at `http://192.168.4.1/telemetry`.
- **Solar Charging Management**: Monitors solar panel bus voltage, charging current, and battery capacity via I2C INA219.
- **Over-The-Air (OTA) Updates**: Supports wireless field updates over WiFi SoftAP.

### Pinout (ESP32 DevKit V1)
| Peripheral | Pin | Description |
|---|---|---|
| LoRa SX1276 SCK | GPIO 18 | SPI Clock |
| LoRa SX1276 MISO | GPIO 19 | SPI Master In |
| LoRa SX1276 MOSI | GPIO 23 | SPI Master Out |
| LoRa SX1276 NSS | GPIO 5 | Chip Select |
| LoRa SX1276 RST | GPIO 14 | Reset |
| LoRa SX1276 DIO0 | GPIO 2 | Interrupt / Packet Ready |
| INA219 SDA | GPIO 21 | I2C Data |
| INA219 SCL | GPIO 22 | I2C Clock |
| Status LED | GPIO 2 | Connection Indicator |

---

## 2. Sensor Node Firmware (`agrix-firmware/sensor-node`)

### Features
- **YF-S201 Pulse Counting**: Measures precision irrigation water flow and liquid fertilizer dosing rates in liters/min and cumulative volume.
- **RS485 Modbus RTU Master**: Interfaces with industrial 7-in-1 soil probes (measuring Nitrogen, Phosphorus, Potassium in mg/kg, pH, EC in µS/cm, soil moisture %, and soil temperature).
- **SX1276 Long-Range LoRa**: 20 dBm high-power transmission penetrating dense crop canopies.
- **Ultra-Low Power Deep Sleep**: ESP32 deep sleep between measurement cycles with RTC memory retention, allowing 1+ year battery operation on 18650 cells.

### Pinout (ESP32 Node)
| Sensor | Pin | Description |
|---|---|---|
| Water Flow Meter (YF-S201) | GPIO 34 | Pulse signal input (pull-up) |
| Fertilizer Flow Meter (YF-S201) | GPIO 35 | Pulse signal input (pull-up) |
| MAX485 RX | GPIO 16 | Serial2 RX |
| MAX485 TX | GPIO 17 | Serial2 TX |
| MAX485 DE / RE | GPIO 4 | RS485 Direction Control |
| Battery Voltage Divider | GPIO 36 (ADC1_0) | Battery monitoring |
| LoRa SX1276 | GPIO 5, 14, 2, 18, 19, 23 | SPI interface |

---

## Build Instructions (PlatformIO)

```bash
# Build gateway
cd agrix-firmware/gateway
pio run

# Flash gateway
pio run --target upload

# Build sensor node
cd ../sensor-node
pio run

# Flash sensor node
pio run --target upload
```
