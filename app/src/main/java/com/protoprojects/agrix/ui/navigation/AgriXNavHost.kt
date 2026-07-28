package com.protoprojects.agrix.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ai.PromptTemplates
import com.protoprojects.agrix.ui.screens.*
import com.protoprojects.agrix.ui.theme.AgrixDeepBlack
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

object Routes {
    const val MODEL_SETUP = "model_setup"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
}

private data class LoadedPersistedState(val modelReady: Boolean, val onboarded: Boolean)

@Composable
fun AgriXNavHost(app: AgriXApp) {
    // startDestination needs to be picked from the REAL persisted value, not
    // a guessed default — picking it before DataStore has actually reported
    // in is what used to send the app back to setup on every launch.
    //
    // The first fix for that swapped modelReady/onboarded to Flow<Boolean?>
    // and used collectAsState(initial = null) as a "haven't loaded yet"
    // placeholder. That introduced a worse bug: on a fresh install neither
    // key exists in DataStore, so the flow's first REAL emission is also
    // null (key absent, not "still loading") — indistinguishable from the
    // placeholder, so the loading screen never went away.
    //
    // Fixed properly here: the flows stay plain Flow<Boolean> (defaulting
    // false when a key is absent, same as any other DataStore read), and we
    // explicitly await one real emission with `.first()` before rendering
    // anything nav-related at all. `.first()` only completes once DataStore
    // has actually delivered a value — there's no ambiguous placeholder to
    // collide with.
    var loaded by remember { mutableStateOf<LoadedPersistedState?>(null) }

    LaunchedEffect(Unit) {
        val (modelReady, onboarded) = combine(app.prefs.modelReady, app.prefs.onboarded) { m, o -> m to o }.first()
        loaded = LoadedPersistedState(modelReady, onboarded)
    }

    val state = loaded
    if (state == null) {
        Box(
            Modifier.fillMaxSize().background(AgrixDeepBlack),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = com.protoprojects.agrix.ui.theme.AgrixNeonGreen
            )
        }
        return
    }

    AgriXNavHostContent(app = app, modelReady = state.modelReady, onboarded = state.onboarded)
}

@Composable
private fun AgriXNavHostContent(app: AgriXApp, modelReady: Boolean, onboarded: Boolean) {
    val navController: NavHostController = rememberNavController()

    // If the model was already installed in a previous session, load it into
    // the inference engine as soon as we know its path. Guarded: if the
    // previously-installed file has gone missing or corrupted since last
    // launch, this would otherwise throw inside a LaunchedEffect with
    // nothing to catch it — crashing the app on every subsequent open. On
    // failure it self-heals instead: clear the "installed" flag and send the
    // farmer back through setup to reinstall a working copy.
    val modelPath by app.prefs.modelPath.collectAsState(initial = null)
    LaunchedEffect(modelPath) {
        val path = modelPath ?: return@LaunchedEffect
        if (app.gemma.isReady) return@LaunchedEffect
        try {
            app.gemma.load(path)
        } catch (t: Throwable) {
            app.gemma.close()
            app.prefs.clearModel()
            navController.navigate(Routes.MODEL_SETUP) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val startDestination = when {
        !modelReady -> Routes.MODEL_SETUP
        !onboarded -> Routes.ONBOARDING
        else -> Routes.DASHBOARD
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.MODEL_SETUP) {
            ModelSetupScreen(onModelReady = {
                navController.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.MODEL_SETUP) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(app = app, onDone = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onOpenFeature = { route -> navController.navigate(route) })
        }
        composable("profile") { ProfileScreen(app = app) }
        composable("crop_suggester") { CropSuggesterScreen(app = app) }
        composable("price_predictor") { PricePredictorScreen(app = app) }
        composable("pest_control") { PestControlScreen() }
        composable("disease_detector") {
            ImageAndTextFeatureScreen(
                title = "Disease Detector",
                subtitle = "Add a photo if you have one, and describe what you see on the crop (spots, wilting, discoloration, insects)",
                fieldLabel = "Describe the symptoms",
                buttonLabel = "Diagnose",
                buildPrompt = { PromptTemplates.detectCropDiseaseFromDescription(it) }
            )
        }
        composable("soil_recommender") {
            SimpleTextFeatureScreen(
                title = "Soil Recommender",
                subtitle = "Type the values from your soil test report (pH, N, P, K, etc.)",
                fieldLabel = "Soil test values",
                buttonLabel = "Get recommendations",
                buildPrompt = { PromptTemplates.soilBasedRecommendations(it) }
            )
        }
        composable("produce_grading") {
            ImageAndTextFeatureScreen(
                title = "Produce Grading",
                subtitle = "Add a photo if you have one, and describe your harvest (type, size, color, visible defects)",
                fieldLabel = "Describe the produce",
                buttonLabel = "Grade it",
                buildPrompt = { PromptTemplates.gradeProduceFromDescription(it) }
            )
        }
        composable("profit_advisor") {
            val profile by app.prefs.farmerProfile.collectAsState(initial = null)
            SimpleTextFeatureScreen(
                title = "Profit Advisor",
                subtitle = "Describe your current situation (costs, yields, sales channel)",
                fieldLabel = "Current situation",
                buttonLabel = "Get suggestions",
                buildPrompt = { situation ->
                    PromptTemplates.profitImprovementAdvice(
                        profile ?: com.protoprojects.agrix.data.FarmerProfile("", "", "", "", "", "English"),
                        situation
                    )
                }
            )
        }
        composable("irrigation") {
            SimpleTextFeatureScreen(
                title = "Precision Irrigation",
                subtitle = "Crop, soil type, and recent weather",
                fieldLabel = "Crop / soil / weather details",
                buttonLabel = "Get schedule",
                buildPrompt = { PromptTemplates.irrigationAdvice(it, "", "") }
            )
        }
        composable("livestock") {
            SimpleTextFeatureScreen(
                title = "Livestock Advice",
                subtitle = "Animal type and the issue you're seeing",
                fieldLabel = "Describe the issue",
                buttonLabel = "Get advice",
                buildPrompt = { PromptTemplates.livestockAdvice("", it) }
            )
        }
    }
}
