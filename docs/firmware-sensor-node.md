# Field Sensor Node Firmware & Hardware Wiring

## 1. Overview

The **AgriX Sensor Node** is an ultra-low-power field unit installed along irrigation pipelines and directly in crop soil beds. It measures water flow rate, fertilizer dosing rate, and 7-in-1 soil parameters, transmitting the data via LoRa to the central gateway before entering deep sleep.

---

## 2. Sensor Interfacing

### A. YF-S201 Water & Fertilizer Flow Meters
* **Principle:** Hall-effect turbine sensor generating electrical pulses proportional to fluid flow.
* **Calibration Factor:** ~450 pulses per liter (7.5 Hz per 1 Liter/min).
* **Hardware Interfacing:** Connected to ESP32 interrupt-capable GPIOs (GPIO 34 and GPIO 35) with external 10kΩ pull-up resistors.
* **State Retention:** Total flow meters are stored in `RTC_DATA_ATTR` memory, preserving running totals across deep sleep cycles.

### B. RS485 Modbus RTU 7-in-1 Soil Sensor
* **Measured Parameters:** Soil Moisture (0-100%), Soil Temperature (-40 to 80°C), Electrical Conductivity (0-20000 µS/cm), Soil pH (3-9 pH), and available Nitrogen, Phosphorus, Potassium (1-1999 mg/kg).
* **Bus Transceiver:** MAX485 chip interfacing ESP32 HardwareSerial (`Serial2`, GPIO 16 RX, GPIO 17 TX) with GPIO 4 controlling `DE`/`RE` transmit/receive switching.
* **Protocol:** Modbus RTU holding registers starting at address `0x0000`.

---

## 3. Hardware Schematic & Pinout

| ESP32 Pin | Connected Hardware | Function / Direction |
|---|---|---|
| GPIO 34 | YF-S201 Water Flow Signal | Input (Pulse counter) |
| GPIO 35 | YF-S201 Fertilizer Flow Signal | Input (Pulse counter) |
| GPIO 16 | MAX485 RO (Receiver Output) | Serial2 RX |
| GPIO 17 | MAX485 DI (Driver Input) | Serial2 TX |
| GPIO 4 | MAX485 DE + RE (Direction) | Output (HIGH = TX, LOW = RX) |
| GPIO 36 | Battery Resistor Divider (1:2) | ADC Analog Input |
| GPIO 5 | SX1276 NSS | SPI Chip Select |
| GPIO 18 | SX1276 SCK | SPI Clock |
| GPIO 19 | SX1276 MISO | SPI Master In |
| GPIO 23 | SX1276 MOSI | SPI Master Out |
| GPIO 14 | SX1276 RST | LoRa Reset |
| GPIO 2 | SX1276 DIO0 | LoRa Packet Interrupt |

---

## 4. Power Management & Deep Sleep

To achieve multi-season field autonomy without external mains power:
1. **Sleep Cycle:** After taking flow readings and polling the Modbus probe, the node sends a LoRa packet in <150 ms.
2. **Radio Sleep:** `LoRa.sleep()` powers down the SX1276 transceiver to <1 µA.
3. **Deep Sleep:** The ESP32 enters deep sleep for 30 seconds (`esp_deep_sleep_start()`), consuming ~15 µA.
4. **Calculated Lifetime:** On a single 2600 mAh 18650 Li-ion cell, runtime exceeds 14 months without recharging.
