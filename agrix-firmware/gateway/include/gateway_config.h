#pragma once

#include <Arduino.h>

// WiFi SoftAP Configuration
#define GATEWAY_AP_SSID "AgriX-SolarGateway"
#define GATEWAY_AP_PASS "agrixoffline2026"
#define GATEWAY_AP_CHANNEL 6

// BLE UUIDs for AgriX Mobile App
#define AGRIX_BLE_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define AGRIX_BLE_CHAR_TELEMETRY_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define AGRIX_BLE_CHAR_COMMAND_UUID   "1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e"

// Hardware Pins (LoRa SX1276)
#ifndef LORA_SS
#define LORA_SS 5
#endif
#ifndef LORA_RST
#define LORA_RST 14
#endif
#ifndef LORA_DIO0
#define LORA_DIO0 2
#endif

// Status LED
#define LED_STATUS_PIN 2

// Telemetry Broadcast Interval (milliseconds)
#define TELEMETRY_INTERVAL_MS 2000
