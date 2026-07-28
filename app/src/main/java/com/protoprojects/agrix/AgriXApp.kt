package com.protoprojects.agrix

import android.app.Application
import com.protoprojects.agrix.ai.GemmaInferenceEngine
import com.protoprojects.agrix.data.PreferencesManager

class AgriXApp : Application() {

    lateinit var prefs: PreferencesManager
        private set

    lateinit var gemma: GemmaInferenceEngine
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        gemma = GemmaInferenceEngine(this)
    }
}
