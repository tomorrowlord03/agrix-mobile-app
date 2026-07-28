package com.protoprojects.agrix.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agrix_prefs")

/**
 * Everything here lives only on the device. There is no backend, no account
 * system, and no sync — this is what "completely offline" means in practice.
 */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val MODEL_PATH = stringPreferencesKey("model_path")
        val MODEL_READY = booleanPreferencesKey("model_ready")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val FARMER_NAME = stringPreferencesKey("farmer_name")
        val FARMER_LOCATION = stringPreferencesKey("farmer_location")
        val FARM_SIZE = stringPreferencesKey("farm_size")
        val SOIL_TYPE = stringPreferencesKey("soil_type")
        val MAIN_CROPS = stringPreferencesKey("main_crops")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val modelPath: Flow<String?> = context.dataStore.data.map { it[Keys.MODEL_PATH] }
    val modelReady: Flow<Boolean> = context.dataStore.data.map { it[Keys.MODEL_READY] ?: false }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setModelInstalled(path: String) {
        context.dataStore.edit {
            it[Keys.MODEL_PATH] = path
            it[Keys.MODEL_READY] = true
        }
    }

    suspend fun clearModel() {
        context.dataStore.edit {
            it.remove(Keys.MODEL_PATH)
            it[Keys.MODEL_READY] = false
        }
    }

    suspend fun setOnboarded(name: String, location: String, farmSize: String, soilType: String, mainCrops: String, language: String) {
        context.dataStore.edit {
            it[Keys.FARMER_NAME] = name
            it[Keys.FARMER_LOCATION] = location
            it[Keys.FARM_SIZE] = farmSize
            it[Keys.SOIL_TYPE] = soilType
            it[Keys.MAIN_CROPS] = mainCrops
            it[Keys.LANGUAGE] = language
            it[Keys.ONBOARDED] = true
        }
    }

    // Nullable: every screen that reads this treats "no profile yet" as a
    // real state (disables its submit button, falls back to a blank
    // profile, etc.), so the flow needs to be able to say "there isn't one"
    // rather than silently handing back an empty-but-present FarmerProfile.
    val farmerProfile: Flow<FarmerProfile?> = context.dataStore.data.map {
        val name = it[Keys.FARMER_NAME]
        if (name.isNullOrBlank()) {
            null
        } else {
            FarmerProfile(
                name = name,
                location = it[Keys.FARMER_LOCATION] ?: "",
                farmSize = it[Keys.FARM_SIZE] ?: "",
                soilType = it[Keys.SOIL_TYPE] ?: "",
                mainCrops = it[Keys.MAIN_CROPS] ?: "",
                languagePreference = it[Keys.LANGUAGE] ?: "English"
            )
        }
    }
}

data class FarmerProfile(
    val name: String,
    val location: String,
    val farmSize: String,
    val soilType: String,
    val mainCrops: String,
    val languagePreference: String
)
