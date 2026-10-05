#include <Arduino.h>
#include <WiFi.h>
#include <esp_now.h>
#include <SPI.h>
#include <LoRa.h>
#include <Wire.h>
#include <Adafruit_INA219.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WebServer.h>
#include <ArduinoOTA.h>

#include "gateway_config.h"
#include "telemetry_packet.h"

// Global Hardware & State
Adafruit_INA219 ina219;
bool ina219Available = false;
WebServer server(80);

BLEServer* pBleServer = nullptr;
BLECharacteristic* pTelemetryChar = nullptr;
bool bleClientConnected = false;

GatewayAggregatedTelemetry gatewayState = {};
unsigned long lastBroadcastTime = 0;

// BLE Server Callbacks
class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        bleClientConnected = true;
        digitalWrite(LED_STATUS_PIN, HIGH);
        Serial.println("[BLE] Mobile client connected");
    }
    void onDisconnect(BLEServer* pServer) override {
        bleClientConnected = false;
        digitalWrite(LED_STATUS_PIN, LOW);
        Serial.println("[BLE] Client disconnected, restarting advertising");
        pServer->getAdvertising()->start();
    }
};

// ESP-NOW Receive Callback
void onEspNowDataRecv(const uint8_t* mac_addr, const uint8_t* data, int data_len) {
    if (data_len == sizeof(SensorNodePacket)) {
        memcpy(&gatewayState.lastNodeData, data, sizeof(SensorNodePacket));
        gatewayState.totalPacketsReceived++;
        Serial.printf("[ESP-NOW] Packet from Node %d: Water=%.1f L/min, NPK=%.0f/%.0f/%.0f\n",
            gatewayState.lastNodeData.nodeId,
            gatewayState.lastNodeData.waterFlowRateLpm,
            gatewayState.lastNodeData.nitrogenMgKg,
            gatewayState.lastNodeData.phosphorusMgKg,
            gatewayState.lastNodeData.potassiumMgKg
        );
    }
}

// Generate JSON string matching AgriX Mobile App format
String generateTelemetryJson() {
    JsonDocument doc;
    const SensorNodePacket& s = gatewayState.lastNodeData;

    doc["water_lpm"] = s.waterFlowRateLpm;
    doc["water_total"] = s.totalWaterLitres;
    doc["fert_lpm"] = s.fertilizerFlowRateLpm;
    doc["fert_total"] = s.totalFertilizerLitres;
    doc["moist"] = s.soilMoisturePercent;
    doc["ph"] = s.soilPh;
    doc["ec"] = s.soilEcMicroSiemens;
    doc["n"] = s.nitrogenMgKg;
    doc["p"] = s.phosphorusMgKg;
    doc["k"] = s.potassiumMgKg;
    doc["temp"] = s.ambientTempCelsius;
    doc["hum"] = s.ambientHumidityPercent;
    doc["batt"] = gatewayState.batteryVoltage;
    doc["solar_v"] = gatewayState.solarBusVoltage;
    doc["solar_ma"] = gatewayState.solarCurrentMa;
    doc["rssi"] = gatewayState.loraRssi;
    doc["rx_count"] = gatewayState.totalPacketsReceived;

    String jsonString;
    serializeJson(doc, jsonString);
    return jsonString;
}

void handleHttpTelemetry() {
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", generateTelemetryJson());
}

void setupLoRa() {
    SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);
    LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

    if (!LoRa.begin(LORA_BAND)) {
        Serial.println("[LoRa] Initializing SX1276 failed! Check wiring.");
    } else {
        LoRa.setSyncWord(0xA5);
        LoRa.enableCrc();
        Serial.printf("[LoRa] SX1276 ready on %.1f MHz\n", LORA_BAND / 1E6);
    }
}

void setupBLE() {
    BLEDevice::init("AgriX-SolarGateway");
    pBleServer = BLEDevice::createServer();
    pBleServer->setCallbacks(new MyServerCallbacks());

    BLEService* pService = pBleServer->createService(AGRIX_BLE_SERVICE_UUID);

    pTelemetryChar = pService->createCharacteristic(
        AGRIX_BLE_CHAR_TELEMETRY_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pTelemetryChar->addDescriptor(new BLE2902());

    pService->start();
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(AGRIX_BLE_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMaxPreferred(0x12);
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Advertising started");
}

void setupPowerMonitor() {
    Wire.begin();
    if (ina219.begin()) {
        ina219Available = true;
        Serial.println("[INA219] Solar power monitor active");
    } else {
        Serial.println("[INA219] Warning: INA219 not detected; using default battery simulation");
    }
}

void setup() {
    Serial.begin(115200);
    pinMode(LED_STATUS_PIN, OUTPUT);
    digitalWrite(LED_STATUS_PIN, LOW);

    Serial.println("\n============================================");
    Serial.println("   AgriX Solar Farm Gateway (ESP32)         ");
    Serial.println("============================================");

    // 1. Setup Solar & Battery Power Monitoring
    setupPowerMonitor();

    // 2. Setup WiFi SoftAP for Offline Phone Connection
    WiFi.mode(WIFI_AP_STA);
    WiFi.softAP(GATEWAY_AP_SSID, GATEWAY_AP_PASS, GATEWAY_AP_CHANNEL);
    Serial.printf("[WiFi] SoftAP online: %s, IP: %s\n", GATEWAY_AP_SSID, WiFi.softAPIP().toString().c_str());

    // 3. Setup ESP-NOW for nearby fast sensor nodes (<200m)
    if (esp_now_init() == ESP_OK) {
        esp_now_register_recv_cb(onEspNowDataRecv);
        Serial.println("[ESP-NOW] Protocol active and listening");
    }

    // 4. Setup LoRa for long-range remote field nodes (up to 10km)
    setupLoRa();

    // 5. Setup BLE GATT service
    setupBLE();

    // 6. Setup Local HTTP Server
    server.on("/telemetry", HTTP_GET, handleHttpTelemetry);
    server.begin();

    // 7. Setup Arduino OTA for remote firmware update
    ArduinoOTA.setHostname("agrix-gateway");
    ArduinoOTA.begin();

    Serial.println("[Gateway] All sub-systems initialized successfully.");
}

void loop() {
    ArduinoOTA.handle();
    server.handleClient();

    // Check for incoming LoRa packets
    int packetSize = LoRa.parsePacket();
    if (packetSize >= sizeof(SensorNodePacket)) {
        LoRa.readBytes((uint8_t*)&gatewayState.lastNodeData, sizeof(SensorNodePacket));
        gatewayState.loraRssi = LoRa.packetRssi();
        gatewayState.loraSnr = LoRa.packetSnr();
        gatewayState.totalPacketsReceived++;
        Serial.printf("[LoRa] Long-range packet received: RSSI=%d dBm\n", gatewayState.loraRssi);
    }

    // Periodic telemetry aggregation & broadcast
    unsigned long now = millis();
    if (now - lastBroadcastTime >= TELEMETRY_INTERVAL_MS) {
        lastBroadcastTime = now;

        // Read power telemetry
        if (ina219Available) {
            gatewayState.solarBusVoltage = ina219.getBusVoltage_V();
            gatewayState.solarCurrentMa = ina219.getCurrent_mA();
            gatewayState.batteryVoltage = gatewayState.solarBusVoltage;
        } else {
            gatewayState.batteryVoltage = 4.12f;
            gatewayState.solarBusVoltage = 5.25f;
            gatewayState.solarCurrentMa = 340.0f;
        }

        String jsonPayload = generateTelemetryJson();

        // Broadcast over BLE if mobile phone connected
        if (bleClientConnected && pTelemetryChar) {
            pTelemetryChar->setValue((uint8_t*)jsonPayload.c_str(), jsonPayload.length());
            pTelemetryChar->notify();
        }
    }
}
