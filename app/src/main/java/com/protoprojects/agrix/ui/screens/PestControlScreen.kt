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
import com.protoprojects.agrix.ai.PromptTemplates
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.AgrixDeepBlack
import com.protoprojects.agrix.ui.theme.AgrixNeonGreen
import com.protoprojects.agrix.ui.theme.AgrixWhite
import com.protoprojects.agrix.viewmodel.GemmaQueryViewModel

@Composable
fun PestControlScreen(viewModel: GemmaQueryViewModel = viewModel()) {
    var cropName by remember { mutableStateOf("") }
    var pestDescription by remember { mutableStateOf("") }
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
        Text("Pest Control Advice", style = MaterialTheme.typography.headlineMedium, color = AgrixWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Describe the pest or symptoms — Gemma answers offline.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(cropName, { cropName = it }, label = { Text("Crop") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    pestDescription, { pestDescription = it },
                    label = { Text("What are you seeing? (holes in leaves, insects, wilting...)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = fieldColors
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        NeonPrimaryButton(
            text = "Get advice",
            onClick = { viewModel.ask(PromptTemplates.pestControlAdvice(cropName, pestDescription)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = cropName.isNotBlank() && pestDescription.isNotBlank()
        )
        ResultArea(state)
    }
}
