package com.protoprojects.agrix.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.protoprojects.agrix.iot.FarmTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Historical record of field telemetry for trend analysis and AI prompting.
 */
data class SensorRecord(
    val id: Long,
    val timestamp: Long,
    val waterFlowRateLpm: Float,
    val totalWaterLitres: Float,
    val fertilizerFlowRateLpm: Float,
    val totalFertilizerLitres: Float,
    val soilMoisturePercent: Float,
    val soilPh: Float,
    val soilEcMicroSiemens: Float,
    val nitrogenMgKg: Float,
    val phosphorusMgKg: Float,
    val potassiumMgKg: Float,
    val ambientTempCelsius: Float,
    val ambientHumidityPercent: Float
)

/**
 * SQLite-backed local repository for sensor telemetry time-series data.
 * Fully offline, zero cloud dependency.
 */
class SensorDataRepository(context: Context) {

    private val dbHelper = SensorDbHelper(context)
    private val _latestRecord = MutableStateFlow<SensorRecord?>(null)
    val latestRecord: Flow<SensorRecord?> = _latestRecord.asStateFlow()

    suspend fun saveTelemetry(telemetry: FarmTelemetry): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SensorDbHelper.COL_TIMESTAMP, telemetry.timestamp)
            put(SensorDbHelper.COL_WATER_FLOW, telemetry.waterFlowRateLpm)
            put(SensorDbHelper.COL_TOTAL_WATER, telemetry.totalWaterLitres)
            put(SensorDbHelper.COL_FERT_FLOW, telemetry.fertilizerFlowRateLpm)
            put(SensorDbHelper.COL_TOTAL_FERT, telemetry.totalFertilizerLitres)
            put(SensorDbHelper.COL_SOIL_MOISTURE, telemetry.soilMoisturePercent)
            put(SensorDbHelper.COL_SOIL_PH, telemetry.soilPh)
            put(SensorDbHelper.COL_SOIL_EC, telemetry.soilEcMicroSiemens)
            put(SensorDbHelper.COL_NITROGEN, telemetry.nitrogenMgKg)
            put(SensorDbHelper.COL_PHOSPHORUS, telemetry.phosphorusMgKg)
            put(SensorDbHelper.COL_POTASSIUM, telemetry.potassiumMgKg)
            put(SensorDbHelper.COL_TEMP, telemetry.ambientTempCelsius)
            put(SensorDbHelper.COL_HUMIDITY, telemetry.ambientHumidityPercent)
        }

        val id = db.insert(SensorDbHelper.TABLE_NAME, null, values)
        val record = SensorRecord(
            id = id,
            timestamp = telemetry.timestamp,
            waterFlowRateLpm = telemetry.waterFlowRateLpm,
            totalWaterLitres = telemetry.totalWaterLitres,
            fertilizerFlowRateLpm = telemetry.fertilizerFlowRateLpm,
            totalFertilizerLitres = telemetry.totalFertilizerLitres,
            soilMoisturePercent = telemetry.soilMoisturePercent,
            soilPh = telemetry.soilPh,
            soilEcMicroSiemens = telemetry.soilEcMicroSiemens,
            nitrogenMgKg = telemetry.nitrogenMgKg,
            phosphorusMgKg = telemetry.phosphorusMgKg,
            potassiumMgKg = telemetry.potassiumMgKg,
            ambientTempCelsius = telemetry.ambientTempCelsius,
            ambientHumidityPercent = telemetry.ambientHumidityPercent
        )
        _latestRecord.value = record
        id
    }

    suspend fun getRecentRecords(limit: Int = 50): List<SensorRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SensorRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SensorDbHelper.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${SensorDbHelper.COL_TIMESTAMP} DESC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SensorRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(SensorDbHelper.COL_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(SensorDbHelper.COL_TIMESTAMP)),
                        waterFlowRateLpm = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_WATER_FLOW)),
                        totalWaterLitres = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_TOTAL_WATER)),
                        fertilizerFlowRateLpm = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_FERT_FLOW)),
                        totalFertilizerLitres = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_TOTAL_FERT)),
                        soilMoisturePercent = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_SOIL_MOISTURE)),
                        soilPh = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_SOIL_PH)),
                        soilEcMicroSiemens = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_SOIL_EC)),
                        nitrogenMgKg = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_NITROGEN)),
                        phosphorusMgKg = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_PHOSPHORUS)),
                        potassiumMgKg = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_POTASSIUM)),
                        ambientTempCelsius = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_TEMP)),
                        ambientHumidityPercent = it.getFloat(it.getColumnIndexOrThrow(SensorDbHelper.COL_HUMIDITY))
                    )
                )
            }
        }
        list
    }

    private class SensorDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TIMESTAMP INTEGER NOT NULL,
                    $COL_WATER_FLOW REAL,
                    $COL_TOTAL_WATER REAL,
                    $COL_FERT_FLOW REAL,
                    $COL_TOTAL_FERT REAL,
                    $COL_SOIL_MOISTURE REAL,
                    $COL_SOIL_PH REAL,
                    $COL_SOIL_EC REAL,
                    $COL_NITROGEN REAL,
                    $COL_PHOSPHORUS REAL,
                    $COL_POTASSIUM REAL,
                    $COL_TEMP REAL,
                    $COL_HUMIDITY REAL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_sensor_time ON $TABLE_NAME ($COL_TIMESTAMP DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }

        companion object {
            const val DATABASE_NAME = "agrix_sensors.db"
            const val DATABASE_VERSION = 1
            const val TABLE_NAME = "sensor_readings"

            const val COL_ID = "id"
            const val COL_TIMESTAMP = "timestamp"
            const val COL_WATER_FLOW = "water_flow_lpm"
            const val COL_TOTAL_WATER = "total_water_litres"
            const val COL_FERT_FLOW = "fert_flow_lpm"
            const val COL_TOTAL_FERT = "total_fert_litres"
            const val COL_SOIL_MOISTURE = "soil_moisture"
            const val COL_SOIL_PH = "soil_ph"
            const val COL_SOIL_EC = "soil_ec"
            const val COL_NITROGEN = "nitrogen"
            const val COL_PHOSPHORUS = "phosphorus"
            const val COL_POTASSIUM = "potassium"
            const val COL_TEMP = "temperature"
            const val COL_HUMIDITY = "humidity"
        }
    }
}
