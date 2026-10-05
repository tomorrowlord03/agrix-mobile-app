package com.protoprojects.agrix

import android.app.Application
import com.protoprojects.agrix.ai.GemmaInferenceEngine
import com.protoprojects.agrix.ai.ModelDistributionManager
import com.protoprojects.agrix.data.PreferencesManager
import com.protoprojects.agrix.data.SensorDataRepository
import com.protoprojects.agrix.iot.IoTGatewayManager

class AgriXApp : Application() {

    lateinit var prefs: PreferencesManager
        private set

    lateinit var gemma: GemmaInferenceEngine
        private set

    lateinit var iotGateway: IoTGatewayManager
        private set

    lateinit var sensorRepo: SensorDataRepository
        private set

    lateinit var modelDistributor: ModelDistributionManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        gemma = GemmaInferenceEngine(this)
        iotGateway = IoTGatewayManager(this)
        sensorRepo = SensorDataRepository(this)
        modelDistributor = ModelDistributionManager(this)
    }
}
