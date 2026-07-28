package com.protoprojects.agrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ai.PromptTemplates
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.AgrixDeepBlack
import com.protoprojects.agrix.ui.theme.AgrixNeonGreen
import com.protoprojects.agrix.ui.theme.AgrixWhite
import com.protoprojects.agrix.viewmodel.GemmaQueryViewModel

@Composable
fun PricePredictorScreen(app: AgriXApp, viewModel: GemmaQueryViewModel = viewModel()) {
    val profile by app.prefs.farmerProfile.collectAsState(initial = null)
    var cropName by remember { mutableStateOf("") }
    var timeHorizon by remember { mutableStateOf("Next 30 days") }
    val state by viewModel.state.collectAsState()

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = AgrixWhite,
        unfocusedTextColor = AgrixWhite,
        focusedIndicatorColor = AgrixNeonGreen,
        cursorColor = AgrixNeonGreen
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(AgrixDeepBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        MicroLabel("Diagnostic Tool")
        Spacer(Modifier.height(6.dp))
        Text("Price Predictor", style = MaterialTheme.typography.headlineMedium, color = AgrixWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("On-device market price forecast — works with no signal.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(cropName, { cropName = it }, label = { Text("Crop name") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(timeHorizon, { timeHorizon = it }, label = { Text("Time horizon") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
            }
        }
        Spacer(Modifier.height(16.dp))
        NeonPrimaryButton(
            text = "Predict price",
            onClick = { profile?.let { viewModel.ask(PromptTemplates.predictMarketPrice(cropName, it.location, timeHorizon)) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = cropName.isNotBlank() && profile != null
        )
        ResultArea(state)
    }
}
