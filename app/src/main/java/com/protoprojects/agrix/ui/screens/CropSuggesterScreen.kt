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
fun CropSuggesterScreen(app: AgriXApp, viewModel: GemmaQueryViewModel = viewModel()) {
    val profile by app.prefs.farmerProfile.collectAsState(initial = null)
    var seasonalInfo by remember { mutableStateOf("Kharif season, monsoon just started") }
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(AgrixDeepBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        MicroLabel("Diagnostic Tool")
        Spacer(Modifier.height(6.dp))
        Text("Crop Suggester", style = MaterialTheme.typography.headlineMedium, color = AgrixWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Get crop suggestions ranked by expected profit, based on your farm profile — computed entirely on-device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = seasonalInfo,
                onValueChange = { seasonalInfo = it },
                label = { Text("Season / conditions") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = AgrixWhite,
                    unfocusedTextColor = AgrixWhite,
                    focusedIndicatorColor = AgrixNeonGreen,
                    cursorColor = AgrixNeonGreen
                )
            )
        }
        Spacer(Modifier.height(16.dp))
        NeonPrimaryButton(
            text = "Suggest crops",
            onClick = { profile?.let { viewModel.ask(PromptTemplates.suggestCropsProfit(it, seasonalInfo)) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = profile != null
        )
        ResultArea(state)
    }
}
