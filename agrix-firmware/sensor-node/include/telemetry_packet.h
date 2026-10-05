#pragma once

#include <Arduino.h>

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
