package com.protoprojects.agrix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.protoprojects.agrix.ui.components.ConfidenceBar
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.theme.AgrixNeonGreen
import com.protoprojects.agrix.ui.theme.AgrixWarnRed
import com.protoprojects.agrix.ui.theme.AgrixWhite
import com.protoprojects.agrix.viewmodel.QueryUiState

/**
 * Shared result panel for every feature screen — styled like the design
 * spec's "Diagnosis Complete" bottom sheet: a glass card, a headline result,
 * and (when the model included one) a glowing confidence meter.
 */
@Composable
fun ResultArea(state: QueryUiState) {
    when (state) {
        is QueryUiState.Idle -> {}
        is QueryUiState.Loading -> {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AgrixNeonGreen)
                Spacer(Modifier.height(10.dp))
                MicroLabel("Analyzing on-device…")
            }
        }
        is QueryUiState.Error -> {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column {
                    MicroLabel("Something went wrong", color = AgrixWarnRed)
                    Spacer(Modifier.height(6.dp))
                    Text(state.message, color = AgrixWhite, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is QueryUiState.Success -> {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    MicroLabel("Diagnosis complete")
                    Spacer(Modifier.height(10.dp))
                    if (state.json != null) {
                        JsonPrettyView(state.json)
                    } else {
                        Text(state.raw, color = AgrixWhite, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonPrettyView(json: org.json.JSONObject) {
    val confidence = json.optDouble("confidence", Double.NaN)
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (key == "confidence") continue
        val value = json.get(key)
        Text(
            text = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() },
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = AgrixNeonGreen
        )
        Spacer(Modifier.height(3.dp))
        when (value) {
            is org.json.JSONArray -> {
                for (i in 0 until value.length()) {
                    val item = value.get(i)
                    if (item is org.json.JSONObject) {
                        NestedObjectRow(item)
                    } else {
                        Text("• $item", style = MaterialTheme.typography.bodyMedium, color = AgrixWhite)
                    }
                }
            }
            else -> Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = AgrixWhite)
        }
        Spacer(Modifier.height(14.dp))
    }

    if (!confidence.isNaN()) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MicroLabel("Confidence")
            Text("${(confidence * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = AgrixNeonGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        ConfidenceBar(confidence = confidence.toFloat())
    }
}

@Composable
private fun NestedObjectRow(item: org.json.JSONObject) {
    val keys = item.keys()
    Column(Modifier.padding(vertical = 4.dp)) {
        while (keys.hasNext()) {
            val k = keys.next()
            Text("· $k: ${item.get(k)}", style = MaterialTheme.typography.bodyMedium, color = AgrixWhite)
        }
    }
}
