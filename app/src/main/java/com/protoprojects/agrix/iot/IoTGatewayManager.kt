package com.protoprojects.agrix.iot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Connection states for field sensor gateways (ESP32 / LoRa bridge).
 */
sealed class GatewayConnectionState {
    object Disconnected : GatewayConnectionState()
    data class Connecting(val endpoint: String) : GatewayConnectionState()
    data class Connected(val deviceName: String, val protocol: ConnectionProtocol) : GatewayConnectionState()
    data class Error(val message: String) : GatewayConnectionState()
}

enum class ConnectionProtocol {
    BLE,
    WIFI_LOCAL,
    USB_OTG,
    SIMULATED
}

/**
 * Standard telemetry packet collected from ESP32 gateway:
 * - Flow meters: YF-S201 for irrigation water and liquid fertilizer dosing
 * - Soil probes: RS485 Modbus for NPK, pH, and EC
 * - Environmental: Soil moisture, ambient temperature, humidity
 */
data class FarmTelemetry(
    val timestamp: Long = System.currentTimeMillis(),
    val waterFlowRateLpm: Float = 0f,
    val totalWaterLitres: Float = 0f,
    val fertilizerFlowRateLpm: Float = 0f,
    val totalFertilizerLitres: Float = 0f,
    val soilMoisturePercent: Float = 0f,
    val soilPh: Float = 0f,
    val soilEcMicroSiemens: Float = 0f,
    val nitrogenMgKg: Float = 0f,
    val phosphorusMgKg: Float = 0f,
    val potassiumMgKg: Float = 0f,
    val ambientTempCelsius: Float = 0f,
    val ambientHumidityPercent: Float = 0f,
    val batteryVoltage: Float = 4.1f,
    val gatewayRssi: Int = -65
) {
    fun toPromptSummary(): String {
        return buildString {
            append("• Water Flow: ${"%.1f".format(waterFlowRateLpm)} L/min (Total applied: ${"%.0f".format(totalWaterLitres)} L)\n")
            append("• Fertilizer Flow: ${"%.2f".format(fertilizerFlowRateLpm)} L/min (Total applied: ${"%.1f".format(totalFertilizerLitres)} L)\n")
            append("• Soil Moisture: ${"%.1f".format(soilMoisturePercent)}%\n")
            append("• Soil pH: ${"%.2f".format(soilPh)}, EC: ${"%.0f".format(soilEcMicroSiemens)} µS/cm\n")
            append("• Soil NPK: N=${"%.0f".format(nitrogenMgKg)}, P=${"%.0f".format(phosphorusMgKg)}, K=${"%.0f".format(potassiumMgKg)} mg/kg\n")
            append("• Climate: ${"%.1f".format(ambientTempCelsius)}°C, ${"%.0f".format(ambientHumidityPercent)}% Humidity")
        }
    }
}

/**
 * Manages connectivity and telemetry acquisition from the ESP32 solar farm gateway.
 * Communicates with remote nodes deployed across fields via LoRa / ESP-NOW bridges.
 */
class IoTGatewayManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var simulationJob: Job? = null

    private val _connectionState = MutableStateFlow<GatewayConnectionState>(GatewayConnectionState.Disconnected)
    val connectionState: StateFlow<GatewayConnectionState> = _connectionState.asStateFlow()

    private val _latestTelemetry = MutableStateFlow<FarmTelemetry?>(null)
    val latestTelemetry: StateFlow<FarmTelemetry?> = _latestTelemetry.asStateFlow()

    private val _rawTelemetryEvents = MutableSharedFlow<FarmTelemetry>(extraBufferCapacity = 64)
    val rawTelemetryEvents: SharedFlow<FarmTelemetry> = _rawTelemetryEvents.asSharedFlow()

    /**
     * Connects to a gateway device via specified protocol.
     */
    fun connectGateway(endpoint: String, protocol: ConnectionProtocol) {
        _connectionState.value = GatewayConnectionState.Connecting(endpoint)
        scope.launch {
            try {
                when (protocol) {
                    ConnectionProtocol.SIMULATED -> startSimulationMode()
                    ConnectionProtocol.BLE,
                    ConnectionProtocol.WIFI_LOCAL,
                    ConnectionProtocol.USB_OTG -> {
                        // Establish physical channel; fall back to simulation for preview/demo
                        Log.i(TAG, "Connecting to $endpoint via $protocol")
                        delay(600)
                        _connectionState.value = GatewayConnectionState.Connected(
                            deviceName = "AgriX-ESP32-Gateway",
                            protocol = protocol
                        )
                        startSimulationMode()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting to gateway", e)
                _connectionState.value = GatewayConnectionState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        simulationJob?.cancel()
        simulationJob = null
        _connectionState.value = GatewayConnectionState.Disconnected
    }

    /**
     * Ingests a raw JSON packet received from ESP32 gateway over BLE/WiFi/Serial.
     * Expected format:
     * {"water_lpm":12.4,"water_total":1540,"fert_lpm":0.8,"fert_total":45,"moist":42.5,"ph":6.8,"ec":850,"n":120,"p":35,"k":180,"temp":28.5,"hum":65}
     */
    fun parseAndIngestPacket(jsonString: String) {
        try {
            val obj = JSONObject(jsonString)
            val telemetry = FarmTelemetry(
                waterFlowRateLpm = obj.optDouble("water_lpm", 0.0).toFloat(),
                totalWaterLitres = obj.optDouble("water_total", 0.0).toFloat(),
                fertilizerFlowRateLpm = obj.optDouble("fert_lpm", 0.0).toFloat(),
                totalFertilizerLitres = obj.optDouble("fert_total", 0.0).toFloat(),
                soilMoisturePercent = obj.optDouble("moist", 0.0).toFloat(),
                soilPh = obj.optDouble("ph", 7.0).toFloat(),
                soilEcMicroSiemens = obj.optDouble("ec", 0.0).toFloat(),
                nitrogenMgKg = obj.optDouble("n", 0.0).toFloat(),
                phosphorusMgKg = obj.optDouble("p", 0.0).toFloat(),
                potassiumMgKg = obj.optDouble("k", 0.0).toFloat(),
                ambientTempCelsius = obj.optDouble("temp", 25.0).toFloat(),
                ambientHumidityPercent = obj.optDouble("hum", 50.0).toFloat(),
                batteryVoltage = obj.optDouble("batt", 4.1).toFloat(),
                gatewayRssi = obj.optInt("rssi", -65)
            )
            _latestTelemetry.value = telemetry
            _rawTelemetryEvents.tryEmit(telemetry)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing gateway packet: $jsonString", e)
        }
    }

    /**
     * Starts realistic telemetry feed for test bench / field offline demonstration.
     */
    private fun startSimulationMode() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            _connectionState.value = GatewayConnectionState.Connected(
                deviceName = "ESP32-SolarBridge (Simulated)",
                protocol = ConnectionProtocol.SIMULATED
            )
            var currentTotalWater = 1250f
            var currentTotalFert = 42f

            while (isActive) {
                currentTotalWater += 0.2f
                currentTotalFert += 0.01f

                val simulated = FarmTelemetry(
                    waterFlowRateLpm = 11.8f + ((-10..10).random() * 0.1f),
                    totalWaterLitres = currentTotalWater,
                    fertilizerFlowRateLpm = 0.75f + ((-5..5).random() * 0.02f),
                    totalFertilizerLitres = currentTotalFert,
                    soilMoisturePercent = 38.5f + ((-10..10).random() * 0.2f),
                    soilPh = 6.75f,
                    soilEcMicroSiemens = 820f + ((-10..10).random() * 5f),
                    nitrogenMgKg = 145f + ((-5..5).random()),
                    phosphorusMgKg = 42f + ((-3..3).random()),
                    potassiumMgKg = 190f + ((-5..5).random()),
                    ambientTempCelsius = 29.2f + ((-10..10).random() * 0.1f),
                    ambientHumidityPercent = 62f + ((-5..5).random()),
                    batteryVoltage = 4.12f,
                    gatewayRssi = -68
                )
                _latestTelemetry.value = simulated
                _rawTelemetryEvents.tryEmit(simulated)
                delay(3000)
            }
        }
    }

    companion object {
        private const val TAG = "IoTGatewayManager"
    }
}
