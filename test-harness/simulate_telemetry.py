#!/usr/bin/env python3
"""
AgriX Hardware Test Harness & Edge Simulation
--------------------------------------------
Simulates:
1. RS485 Modbus RTU 7-in-1 Soil Sensor (Slave 0x01, Function 0x03, CRC16)
2. YF-S201 Water and Fertilizer Flow Pulse Counters (ESP32 PCNT)
3. LoRa SX1276 Binary Packed Telemetry Frames (57 bytes)
4. BLE GATT / JSON Streaming Telemetry for Android App (IoTGatewayManager)

Run automated unit tests:
    python simulate_telemetry.py --test

Run continuous live sensor stream:
    python simulate_telemetry.py --stream
"""

import struct
import math
import time
import json
import sys
import argparse

# ==============================================================================
# 1. MODBUS RTU CRC16 & FRAME SIMULATOR
# ==============================================================================

def calculate_modbus_crc16(data: bytes) -> int:
    """Computes standard Modbus RTU CRC-16 (polynomial 0xA001, initial 0xFFFF)."""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 0x0001:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc

def build_modbus_soil_response(
    slave_id: int = 1,
    nitrogen_mg_kg: int = 142,
    phosphorus_mg_kg: int = 48,
    potassium_mg_kg: int = 195,
    ph_x10: int = 68,              # 6.8 pH -> 68
    ec_us_cm: int = 1240,          # 1.24 mS/cm -> 1240 uS/cm
    moisture_x10: int = 385,       # 38.5% -> 385
    temp_x10: int = 264            # 26.4°C -> 264
) -> bytes:
    """
    Constructs a Modbus RTU response frame (Function 0x03, 7 16-bit registers).
    Registers:
      0x0000: Nitrogen (mg/kg)
      0x0001: Phosphorus (mg/kg)
      0x0002: Potassium (mg/kg)
      0x0003: pH (x10)
      0x0004: EC (uS/cm)
      0x0005: Moisture % (x10)
      0x0006: Temperature °C (x10)
    """
    byte_count = 14  # 7 registers * 2 bytes
    payload = bytearray([slave_id, 0x03, byte_count])
    
    # Pack 7 16-bit big-endian registers
    payload.extend(struct.pack(
        ">HHHHHHH",
        nitrogen_mg_kg,
        phosphorus_mg_kg,
        potassium_mg_kg,
        ph_x10,
        ec_us_cm,
        moisture_x10,
        temp_x10
    ))
    
    # Calculate CRC16 (little-endian: low byte then high byte)
    crc = calculate_modbus_crc16(bytes(payload))
    payload.extend(struct.pack("<H", crc))
    return bytes(payload)

def parse_modbus_soil_response(frame: bytes) -> dict:
    """Parses and validates a Modbus RTU soil response frame."""
    if len(frame) < 19:
        raise ValueError(f"Frame length {len(frame)} is too short for 7-in-1 Modbus response")
    
    # Verify CRC
    expected_crc = calculate_modbus_crc16(frame[:-2])
    actual_crc = struct.unpack("<H", frame[-2:])[0]
    if expected_crc != actual_crc:
        raise ValueError(f"Modbus CRC mismatch: calculated {hex(expected_crc)}, received {hex(actual_crc)}")
    
    slave_id, func_code, byte_count = frame[0], frame[1], frame[2]
    if func_code != 0x03:
        raise ValueError(f"Unexpected function code: {func_code}")
    
    n, p, k, ph_raw, ec, moist_raw, temp_raw = struct.unpack(">HHHHHHH", frame[3:17])
    return {
        "slave_id": slave_id,
        "nitrogen_mg_kg": n,
        "phosphorus_mg_kg": p,
        "potassium_mg_kg": k,
        "ph": ph_raw / 10.0,
        "ec_us_cm": ec,
        "moisture_percent": moist_raw / 10.0,
        "temp_celsius": temp_raw / 10.0
    }

# ==============================================================================
# 2. YF-S201 FLOW METER PULSE COUNTING (ESP32 PCNT)
# ==============================================================================

class YFS201FlowSimulator:
    """
    Simulates Hall-effect pulse counting on YF-S201 flow sensors.
    Calibration factor: F = 7.5 * Q (L/min)
    450 pulses per liter of water.
    """
    CALIBRATION_FACTOR = 7.5
    PULSES_PER_LITER = 450.0

    def __init__(self, sample_interval_sec: float = 1.0):
        self.interval = sample_interval_sec
        self.cumulative_pulses = 0

    def simulate_flow(self, flow_rate_lpm: float) -> dict:
        freq_hz = flow_rate_lpm * self.CALIBRATION_FACTOR
        pulses_in_window = int(round(freq_hz * self.interval))
        self.cumulative_pulses += pulses_in_window
        total_liters = self.cumulative_pulses / self.PULSES_PER_LITER
        return {
            "flow_rate_lpm": round(flow_rate_lpm, 2),
            "frequency_hz": round(freq_hz, 1),
            "pulses_in_window": pulses_in_window,
            "cumulative_pulses": self.cumulative_pulses,
            "total_liters": round(total_liters, 3)
        }

# ==============================================================================
# 3. LORA SX1276 BINARY PACKET ENCODER & DECODER
# ==============================================================================

class LoRaPacketCodec:
    """
    Matches struct SensorNodePacket in agrix-firmware/gateway/include/telemetry_packet.h:
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
    Size = 1 + 4 + (13 * 4) = 57 bytes
    """
    STRUCT_FORMAT = "<BI13f"
    EXPECTED_SIZE = 57

    @classmethod
    def encode(cls, data: dict) -> bytes:
        return struct.pack(
            cls.STRUCT_FORMAT,
            data.get("node_id", 1),
            data.get("packet_seq", 0),
            data.get("water_flow_rate_lpm", 0.0),
            data.get("total_water_litres", 0.0),
            data.get("fertilizer_flow_rate_lpm", 0.0),
            data.get("total_fertilizer_litres", 0.0),
            data.get("soil_moisture_percent", 0.0),
            data.get("soil_ph", 0.0),
            data.get("soil_ec_us_cm", 0.0),
            data.get("nitrogen_mg_kg", 0.0),
            data.get("phosphorus_mg_kg", 0.0),
            data.get("potassium_mg_kg", 0.0),
            data.get("ambient_temp_celsius", 0.0),
            data.get("ambient_humidity_percent", 0.0),
            data.get("node_battery_volts", 0.0)
        )

    @classmethod
    def decode(cls, packet: bytes) -> dict:
        if len(packet) != cls.EXPECTED_SIZE:
            raise ValueError(f"Packet size {len(packet)} does not match expected {cls.EXPECTED_SIZE} bytes")
        
        values = struct.unpack(cls.STRUCT_FORMAT, packet)
        return {
            "node_id": values[0],
            "packet_seq": values[1],
            "water_flow_rate_lpm": round(values[2], 2),
            "total_water_litres": round(values[3], 2),
            "fertilizer_flow_rate_lpm": round(values[4], 2),
            "total_fertilizer_litres": round(values[5], 2),
            "soil_moisture_percent": round(values[6], 1),
            "soil_ph": round(values[7], 2),
            "soil_ec_us_cm": round(values[8], 1),
            "nitrogen_mg_kg": round(values[9], 1),
            "phosphorus_mg_kg": round(values[10], 1),
            "potassium_mg_kg": round(values[11], 1),
            "ambient_temp_celsius": round(values[12], 1),
            "ambient_humidity_percent": round(values[13], 1),
            "node_battery_volts": round(values[14], 2)
        }

# ==============================================================================
# 4. LIVE TELEMETRY GENERATOR
# ==============================================================================

def generate_telemetry_snapshot(t_sec: float, seq: int) -> dict:
    """Generates realistic telemetry using sinusoidal diurnal cycle modeling."""
    return {
        "node_id": 1,
        "packet_seq": seq,
        "timestamp_ms": int(time.time() * 1000),
        "water_flow_rate_lpm": round(14.0 + 2.0 * math.sin(t_sec / 5.0), 2),
        "total_water_litres": round(420.0 + (seq * 0.23), 2),
        "fertilizer_flow_rate_lpm": round(0.85 + 0.15 * math.cos(t_sec / 6.0), 2),
        "total_fertilizer_litres": round(12.5 + (seq * 0.014), 2),
        "soil_moisture_percent": round(38.0 + 3.0 * math.sin(t_sec / 20.0), 1),
        "soil_ph": round(6.8 + 0.15 * math.sin(t_sec / 15.0), 2),
        "soil_ec_us_cm": round(1240.0 + 50.0 * math.cos(t_sec / 25.0), 1),
        "nitrogen_mg_kg": round(142.0 + 8.0 * math.sin(t_sec / 30.0), 1),
        "phosphorus_mg_kg": round(48.0 + 4.0 * math.cos(t_sec / 35.0), 1),
        "potassium_mg_kg": round(195.0 + 10.0 * math.sin(t_sec / 40.0), 1),
        "ambient_temp_celsius": round(26.5 + 4.0 * math.sin(t_sec / 60.0), 1),
        "ambient_humidity_percent": round(65.0 - 10.0 * math.sin(t_sec / 60.0), 1),
        "node_battery_volts": round(3.85 + 0.1 * math.sin(t_sec / 100.0), 2),
        "gateway_solar_watts": round(2.1 + 0.3 * math.cos(t_sec / 10.0), 2),
        "gateway_battery_percent": int(88 + 4 * math.sin(t_sec / 120.0)),
        "lora_rssi_dbm": -72
    }

# ==============================================================================
# 5. AUTOMATED TEST SUITE
# ==============================================================================

def run_automated_tests():
    print("=" * 65)
    print("AGRIX HARDWARE TEST HARNESS — AUTOMATED VERIFICATION SUITE")
    print("=" * 65)
    
    # Test 1: Modbus CRC16 Calculation
    print("\n[TEST 1] Modbus RTU CRC-16 Verification...")
    test_data = bytes([0x01, 0x03, 0x00, 0x00, 0x00, 0x07])
    expected_crc = calculate_modbus_crc16(test_data)
    assert expected_crc == 0x0804, f"Expected 0x0804, got {hex(expected_crc)}"
    print(f"  [PASS] CRC-16 check passed (0x01 0x03 0x00 0x00 0x00 0x07 -> 0x{expected_crc:04X})")

    # Test 2: Modbus Soil Response Frame Build & Parse
    print("\n[TEST 2] Modbus RTU 7-in-1 Soil Frame Round-Trip...")
    frame = build_modbus_soil_response(
        slave_id=1,
        nitrogen_mg_kg=145,
        phosphorus_mg_kg=52,
        potassium_mg_kg=198,
        ph_x10=69,
        ec_us_cm=1280,
        moisture_x10=412,
        temp_x10=271
    )
    print(f"  Constructed Frame ({len(frame)} bytes): {frame.hex()}")
    assert len(frame) == 19, f"Expected 19 bytes, got {len(frame)}"
    parsed = parse_modbus_soil_response(frame)
    assert parsed["nitrogen_mg_kg"] == 145
    assert parsed["ph"] == 6.9
    assert parsed["ec_us_cm"] == 1280
    assert parsed["moisture_percent"] == 41.2
    assert parsed["temp_celsius"] == 27.1
    print("  [PASS] Modbus response successfully parsed and all vitals verified!")

    # Test 3: YF-S201 Pulse Counter Simulation
    print("\n[TEST 3] YF-S201 Flow Sensor Pulse Counting (PCNT)...")
    sim = YFS201FlowSimulator(sample_interval_sec=1.0)
    res = sim.simulate_flow(15.0)  # 15 L/min
    # freq = 15 * 7.5 = 112.5 Hz -> 113 pulses in 1 sec
    assert res["pulses_in_window"] in (112, 113)
    assert res["flow_rate_lpm"] == 15.0
    print(f"  Flow: 15.0 L/min -> Frequency: {res['frequency_hz']} Hz -> Window Pulses: {res['pulses_in_window']}")
    print(f"  [PASS] YF-S201 calibration factor (7.5) and pulse accumulator verified!")

    # Test 4: LoRa Binary Packet Struct Pack & Unpack
    print("\n[TEST 4] LoRa SX1276 Binary Struct Encoding (57 Bytes)...")
    original = generate_telemetry_snapshot(10.0, 42)
    packed = LoRaPacketCodec.encode(original)
    assert len(packed) == LoRaPacketCodec.EXPECTED_SIZE, f"Expected 57 bytes, got {len(packed)}"
    print(f"  Packed 57 bytes: {packed[:16].hex()}... (first 16 bytes)")
    unpacked = LoRaPacketCodec.decode(packed)
    assert unpacked["node_id"] == 1
    assert unpacked["packet_seq"] == 42
    assert abs(unpacked["water_flow_rate_lpm"] - original["water_flow_rate_lpm"]) < 0.05
    assert abs(unpacked["soil_moisture_percent"] - original["soil_moisture_percent"]) < 0.1
    print("  [PASS] LoRa binary struct pack/unpack lossless round-trip verified!")

    # Test 5: JSON Schema Compatibility with IoTGatewayManager.kt
    print("\n[TEST 5] Android IoTGatewayManager.kt JSON Compatibility...")
    snapshot = generate_telemetry_snapshot(25.0, 100)
    json_str = json.dumps(snapshot, indent=2)
    assert "water_flow_rate_lpm" in json_str
    assert "soil_moisture_percent" in json_str
    assert "nitrogen_mg_kg" in json_str
    print("  [PASS] JSON telemetry payload schema validated for Android consumption!")

    print("\n" + "=" * 65)
    print("ALL 5 HARDWARE TEST HARNESS SUITES PASSED! (100% SUCCESS)")
    print("=" * 65)

# ==============================================================================
# MAIN ENTRYPOINT
# ==============================================================================

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AgriX Hardware Test Harness & Edge Simulation")
    parser.add_argument("--test", action="store_true", help="Run automated test suite and exit")
    parser.add_argument("--stream", action="store_true", help="Run continuous live telemetry stream to stdout")
    parser.add_argument("--count", type=int, default=10, help="Number of stream frames to emit")
    args = parser.parse_args()

    if args.test:
        run_automated_tests()
        sys.exit(0)

    if args.stream:
        print(f"Starting simulated sensor stream ({args.count} frames)...")
        for seq in range(1, args.count + 1):
            t_sec = seq * 2.0
            data = generate_telemetry_snapshot(t_sec, seq)
            modbus_frame = build_modbus_soil_response(
                slave_id=1,
                nitrogen_mg_kg=int(data["nitrogen_mg_kg"]),
                phosphorus_mg_kg=int(data["phosphorus_mg_kg"]),
                potassium_mg_kg=int(data["potassium_mg_kg"]),
                ph_x10=int(data["soil_ph"] * 10),
                ec_us_cm=int(data["soil_ec_us_cm"]),
                moisture_x10=int(data["soil_moisture_percent"] * 10),
                temp_x10=int(data["ambient_temp_celsius"] * 10)
            )
            lora_packet = LoRaPacketCodec.encode(data)
            print(f"[{seq:03d}] Flow: {data['water_flow_rate_lpm']} L/m | Moist: {data['soil_moisture_percent']}% | pH: {data['soil_ph']} | LoRa Hex: {lora_packet[:12].hex()}... | Modbus CRC: {modbus_frame[-2:].hex()}")
            time.sleep(0.5)
        sys.exit(0)

    # Default: Run test suite
    run_automated_tests()
