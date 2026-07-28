package com.protoprojects.agrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.theme.*

/** Read-only view of the on-device farmer profile captured during onboarding. */
@Composable
fun ProfileScreen(app: AgriXApp) {
    val profile by app.prefs.farmerProfile.collectAsState(initial = null)

    Column(
        Modifier
            .fillMaxSize()
            .background(AgrixDeepBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AgrixSurfaceGreyElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))

        val p = profile
        if (p == null) {
            Text("No profile yet", style = MaterialTheme.typography.headlineSmall, color = AgrixWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Finish onboarding to see your farm profile here.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(p.name, style = MaterialTheme.typography.headlineSmall, color = AgrixWhite, fontWeight = FontWeight.Bold)
            Text(p.location, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    ProfileRow("Farm size", "${p.farmSize} acres")
                    ProfileDivider()
                    ProfileRow("Soil type", p.soilType.ifBlank { "Not set" })
                    ProfileDivider()
                    ProfileRow("Main crops", p.mainCrops.ifBlank { "Not set" })
                    ProfileDivider()
                    ProfileRow("Language", p.languagePreference)
                }
            }

            Spacer(Modifier.height(20.dp))
            MicroLabel("On-device only")
            Spacer(Modifier.height(6.dp))
            Text(
                "This profile lives only in this app's local storage. It's never uploaded anywhere — everything in Agrix runs fully offline.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AgrixWhite, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AgrixGlassBorder))
}
