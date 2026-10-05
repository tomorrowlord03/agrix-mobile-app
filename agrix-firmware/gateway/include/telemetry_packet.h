#pragma once

#include <Arduino.h>

/**
 * Binary struct exchanged between in-field ESP-NOW / LoRa sensor nodes
 * and the solar-powered gateway. Packed to minimize airtime on 868MHz.
 */
struct __attribute__((packed)) SensorNodePacket {
    uint8_t nodeId;
    uint32_t packetSeq;
    float waterFlowRateLpm;
    float totalWaterLitres;
    float fertilizerFlowRateLpm;
    float totalFertilizerLitres;
    float soilMoisturePercent;
    float soilPh;
    float soilEcMicroSiemens;
    float nitrogenMgKg;
    float phosphorusMgKg;
    float potassiumMgKg;
    float ambientTempCelsius;
    float ambientHumidityPercent;
    float nodeBatteryVolts;
};

struct GatewayAggregatedTelemetry {
    SensorNodePacket lastNodeData;
    float solarBusVoltage;
    float solarCurrentMa;
    float batteryVoltage;
    int8_t loraRssi;
    int8_t loraSnr;
    uint32_t totalPacketsReceived;
};
