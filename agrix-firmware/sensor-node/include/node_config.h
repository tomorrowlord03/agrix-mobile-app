#pragma once

#include <Arduino.h>

#define NODE_ID 1

// YF-S201 Flow Sensor Pins (Connected to ESP32 PCNT hardware)
#define WATER_FLOW_PIN       34  // PCNT Unit 0
#define FERTILIZER_FLOW_PIN  35  // PCNT Unit 1

// Calibration: YF-S201 produces ~450 pulses per liter (7.5 Hz per L/min)
#define FLOW_CALIBRATION_FACTOR 7.5f

// RS485 MAX485 Serial Interface (for Modbus 7-in-1 NPK/Soil Probe)
#define RS485_RX_PIN  16
#define RS485_TX_PIN  17
#define RS485_DE_RE_PIN 4
#define RS485_BAUD    9600
#define MODBUS_SLAVE_ID 1

// LoRa Pins
#ifndef LORA_SS
#define LORA_SS 5
#endif
#ifndef LORA_RST
#define LORA_RST 14
#endif
#ifndef LORA_DIO0
#define LORA_DIO0 2
#endif

// Power / Battery ADC
#define BATTERY_ADC_PIN 36

// Deep Sleep Wake Interval (seconds)
#define SLEEP_INTERVAL_SECONDS 30
#define uS_TO_S_FACTOR 1000000ULL
