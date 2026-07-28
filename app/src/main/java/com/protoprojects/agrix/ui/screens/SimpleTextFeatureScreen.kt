package com.protoprojects.agrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.AgrixDeepBlack
import com.protoprojects.agrix.ui.theme.AgrixNeonGreen
import com.protoprojects.agrix.ui.theme.AgrixWhite
import com.protoprojects.agrix.viewmodel.GemmaQueryViewModel

/** Generic text-in, JSON-out feature screen — reused by soil/irrigation/livestock/profit. */
@Composable
fun SimpleTextFeatureScreen(
    title: String,
    subtitle: String,
    fieldLabel: String,
    buttonLabel: String,
    buildPrompt: (String) -> String,
    viewModel: GemmaQueryViewModel = viewModel()
) {
    var input by remember { mutableStateOf("") }
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
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AgrixWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    input, { input = it },
                    label = { Text(fieldLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = AgrixWhite,
                        unfocusedTextColor = AgrixWhite,
                        focusedIndicatorColor = AgrixNeonGreen,
                        cursorColor = AgrixNeonGreen
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        NeonPrimaryButton(
            text = buttonLabel,
            onClick = { viewModel.ask(buildPrompt(input)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank()
        )
        ResultArea(state)
    }
}
