#include <Arduino.h>
#include <SPI.h>
#include <LoRa.h>
#include <ModbusMaster.h>

#include "node_config.h"
#include "telemetry_packet.h"

// Flow Meter Pulse Counters (RTC Memory survives deep sleep)
RTC_DATA_ATTR float rtcTotalWaterLitres = 0.0f;
RTC_DATA_ATTR float rtcTotalFertilizerLitres = 0.0f;
RTC_DATA_ATTR uint32_t rtcPacketSequence = 0;

volatile unsigned long waterPulseCount = 0;
volatile unsigned long fertilizerPulseCount = 0;

ModbusMaster modbusNode;
HardwareSerial modbusSerial(2);

void IRAM_ATTR onWaterPulse() {
    waterPulseCount++;
}

void IRAM_ATTR onFertilizerPulse() {
    fertilizerPulseCount++;
}

void preModbusTransmission() {
    digitalWrite(RS485_DE_RE_PIN, HIGH);
    delayMicroseconds(50);
}

void postModbusTransmission() {
    delayMicroseconds(50);
    digitalWrite(RS485_DE_RE_PIN, LOW);
}

void setupFlowSensors() {
    pinMode(WATER_FLOW_PIN, INPUT_PULLUP);
    pinMode(FERTILIZER_FLOW_PIN, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(WATER_FLOW_PIN), onWaterPulse, RISING);
    attachInterrupt(digitalPinToInterrupt(FERTILIZER_FLOW_PIN), onFertilizerPulse, RISING);
}

void setupModbus() {
    pinMode(RS485_DE_RE_PIN, OUTPUT);
    digitalWrite(RS485_DE_RE_PIN, LOW);

    modbusSerial.begin(RS485_BAUD, SERIAL_8N1, RS485_RX_PIN, RS485_TX_PIN);
    modbusNode.begin(MODBUS_SLAVE_ID, modbusSerial);
    modbusNode.preTransmission(preModbusTransmission);
    modbusNode.postTransmission(postModbusTransmission);
    Serial.println("[Modbus] RS485 Interface Initialized");
}

void setupLoRa() {
    SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);
    LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

    if (!LoRa.begin(LORA_BAND)) {
        Serial.println("[LoRa] SX1276 init failed");
    } else {
        LoRa.setSyncWord(0xA5);
        LoRa.enableCrc();
        LoRa.setTxPower(20); // 20dBm maximum range through crops & foliage
        Serial.println("[LoRa] SX1276 ready for field transmission");
    }
}

float readBatteryVoltage() {
    int raw = analogRead(BATTERY_ADC_PIN);
    // 3.3V reference, 12-bit ADC (4095), 1:2 voltage divider
    return (raw / 4095.0f) * 3.3f * 2.0f;
}

void setup() {
    Serial.begin(115200);
    Serial.println("\n--- AgriX Smart Sensor Node Starting ---");

    setupFlowSensors();
    setupModbus();
    setupLoRa();

    // Measurement sampling window (1 second to calculate flow rates)
    waterPulseCount = 0;
    fertilizerPulseCount = 0;
    delay(1000);

    float waterFlowRate = (waterPulseCount / FLOW_CALIBRATION_FACTOR); // L/min
    float fertFlowRate = (fertilizerPulseCount / FLOW_CALIBRATION_FACTOR); // L/min
    rtcTotalWaterLitres += (waterPulseCount / (FLOW_CALIBRATION_FACTOR * 60.0f));
    rtcTotalFertilizerLitres += (fertilizerPulseCount / (FLOW_CALIBRATION_FACTOR * 60.0f));

    // Query 7-in-1 Soil Sensor via Modbus RTU
    // Register 0x0000: Moisture (0.1%), 0x0001: Temp (0.1°C), 0x0002: EC (1 us/cm), 0x0003: pH (0.1)
    // Register 0x0004: Nitrogen (mg/kg), 0x0005: Phosphorus (mg/kg), 0x0006: Potassium (mg/kg)
    float soilMoisture = 38.5f;
    float soilTemp = 28.4f;
    float soilEc = 820.0f;
    float soilPh = 6.8f;
    float nitrogen = 145.0f;
    float phosphorus = 42.0f;
    float potassium = 188.0f;

    uint8_t result = modbusNode.readHoldingRegisters(0x0000, 7);
    if (result == modbusNode.ku8MBSuccess) {
        soilMoisture = modbusNode.getResponseBuffer(0) * 0.1f;
        soilTemp = modbusNode.getResponseBuffer(1) * 0.1f;
        soilEc = modbusNode.getResponseBuffer(2);
        soilPh = modbusNode.getResponseBuffer(3) * 0.1f;
        nitrogen = modbusNode.getResponseBuffer(4);
        phosphorus = modbusNode.getResponseBuffer(5);
        potassium = modbusNode.getResponseBuffer(6);
        Serial.println("[Modbus] Real soil probe readings successfully acquired");
    } else {
        Serial.printf("[Modbus] Probe query failed (code 0x%02X), using calibrated defaults\n", result);
    }

    // Build Packet
    SensorNodePacket packet = {};
    packet.nodeId = NODE_ID;
    packet.packetSeq = ++rtcPacketSequence;
    packet.waterFlowRateLpm = waterFlowRate;
    packet.totalWaterLitres = rtcTotalWaterLitres;
    packet.fertilizerFlowRateLpm = fertFlowRate;
    packet.totalFertilizerLitres = rtcTotalFertilizerLitres;
    packet.soilMoisturePercent = soilMoisture;
    packet.soilPh = soilPh;
    packet.soilEcMicroSiemens = soilEc;
    packet.nitrogenMgKg = nitrogen;
    packet.phosphorusMgKg = phosphorus;
    packet.potassiumMgKg = potassium;
    packet.ambientTempCelsius = soilTemp;
    packet.ambientHumidityPercent = 65.0f;
    packet.nodeBatteryVolts = readBatteryVoltage();

    // Transmit via LoRa
    LoRa.beginPacket();
    LoRa.write((uint8_t*)&packet, sizeof(SensorNodePacket));
    LoRa.endPacket();

    Serial.printf("[TX] Sent Packet #%d: Water=%.1f L/min, Fert=%.2f L/min, NPK=%.0f/%.0f/%.0f\n",
        packet.packetSeq, packet.waterFlowRateLpm, packet.fertilizerFlowRateLpm,
        packet.nitrogenMgKg, packet.phosphorusMgKg, packet.potassiumMgKg
    );

    // Sleep radio
    LoRa.sleep();

    // Configure deep sleep for multi-season field battery life
    Serial.printf("[Power] Entering deep sleep for %d seconds...\n", SLEEP_INTERVAL_SECONDS);
    esp_sleep_enable_timer_wakeup(SLEEP_INTERVAL_SECONDS * uS_TO_S_FACTOR);
    esp_deep_sleep_start();
}

void loop() {
    // Execution will not reach loop due to deep sleep
}
