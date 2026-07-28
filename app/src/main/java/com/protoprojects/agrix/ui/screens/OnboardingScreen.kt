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
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.AgrixDeepBlack
import com.protoprojects.agrix.ui.theme.AgrixNeonGreen
import com.protoprojects.agrix.ui.theme.AgrixWhite
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(app: AgriXApp, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var soilType by remember { mutableStateOf("") }
    var mainCrops by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        MicroLabel("Step 1 of 1")
        Spacer(Modifier.height(8.dp))
        Text("Tell us about your farm", style = MaterialTheme.typography.headlineLarge, color = AgrixWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "This stays on your device only and helps the AI give you better answers.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(name, { name = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(location, { location = it }, label = { Text("Village / town, district") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(farmSize, { farmSize = it }, label = { Text("Farm size (acres)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(soilType, { soilType = it }, label = { Text("Soil type (e.g. black, red, alluvial)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(mainCrops, { mainCrops = it }, label = { Text("Main crops (comma separated)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
            }
        }

        Spacer(Modifier.height(24.dp))
        NeonPrimaryButton(
            text = "Get started",
            onClick = {
                scope.launch {
                    app.prefs.setOnboarded(name, location, farmSize, soilType, mainCrops, "English")
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && location.isNotBlank()
        )
    }
}
