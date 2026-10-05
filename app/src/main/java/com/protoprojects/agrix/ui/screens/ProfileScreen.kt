package com.protoprojects.agrix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.data.FarmerProfile
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.*
import kotlinx.coroutines.launch

/**
 * On-device profile management, in-app login, and farmer identity switcher.
 * All credentials and profiles live 100% on the device with zero cloud dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    app: AgriXApp,
    onBack: () -> Unit = {},
    onOpenModelSetup: () -> Unit = {}
) {
    val profile by app.prefs.farmerProfile.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    var editFarmSize by remember { mutableStateOf("") }
    var editSoilType by remember { mutableStateOf("") }
    var editMainCrops by remember { mutableStateOf("") }

    // Sync form values when opening editor
    LaunchedEffect(profile, isEditing) {
        if (isEditing) {
            editName = profile?.name ?: "Anshuman Yadav"
            editLocation = profile?.location ?: "Ludhiana, Punjab"
            editFarmSize = profile?.farmSize ?: "8.5"
            editSoilType = profile?.soilType ?: "Alluvial Loam (pH 7.2)"
            editMainCrops = profile?.mainCrops ?: "Wheat, Paddy, Mustard"
        }
    }

    val presetProfiles = remember {
        listOf(
            FarmerProfile("Anshuman Yadav", "Ludhiana, Punjab", "8.5", "Alluvial Loam (pH 7.2)", "Wheat, Paddy, Mustard", "English"),
            FarmerProfile("Rao Sahab", "Rewari, Haryana", "12.0", "Sandy Loam (pH 7.8)", "Mustard, Bajra, Wheat", "Hindi"),
            FarmerProfile("Kisan Devendra", "Varanasi, UP", "4.5", "Clayey Alluvial", "Paddy, Vegetables, Pulses", "Hindi"),
            FarmerProfile("Patel Agro Farm", "Anand, Gujarat", "15.0", "Medium Black Soil", "Cotton, Groundnut, Tobacco", "Gujarati")
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AgrixDeepBlack)
    ) {
        TopAppBar(
            title = { Text("Farmer Profile & Identity", color = AgrixWhite, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = AgrixWhite)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AgrixDeepBlack)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Active Profile Header
            val p = profile ?: com.protoprojects.agrix.data.PreferencesManager.DEFAULT_PROFILE
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AgrixSurfaceGreyElevated)
                        .border(2.dp, AgrixNeonGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, color = AgrixWhite, fontWeight = FontWeight.Bold)
                    Text(p.location, style = MaterialTheme.typography.bodyMedium, color = AgrixMutedSilver)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AgrixNeonGreen))
                        Spacer(Modifier.width(6.dp))
                        Text("Active In-App Profile", style = MaterialTheme.typography.labelSmall, color = AgrixNeonGreen)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Profile Details or Edit Form
            if (!isEditing) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Farm Details", style = MaterialTheme.typography.titleMedium, color = AgrixWhite, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { isEditing = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit", color = AgrixNeonGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ProfileRow("Farmer Name", p.name)
                        ProfileDivider()
                        ProfileRow("Location", p.location)
                        ProfileDivider()
                        ProfileRow("Farm Size", "${p.farmSize} acres")
                        ProfileDivider()
                        ProfileRow("Soil Type", p.soilType.ifBlank { "Alluvial Loam" })
                        ProfileDivider()
                        ProfileRow("Main Crops", p.mainCrops.ifBlank { "Wheat, Mustard" })
                        ProfileDivider()
                        ProfileRow("Language", p.languagePreference)
                    }
                }
            } else {
                // Edit Profile Form
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Edit Farm Profile", style = MaterialTheme.typography.titleMedium, color = AgrixWhite, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(14.dp))

                        val fieldColors = TextFieldDefaults.colors(
                            focusedTextColor = AgrixWhite,
                            unfocusedTextColor = AgrixWhite,
                            focusedIndicatorColor = AgrixNeonGreen,
                            cursorColor = AgrixNeonGreen
                        )

                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Your Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editLocation,
                            onValueChange = { editLocation = it },
                            label = { Text("Village / District") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editFarmSize,
                            onValueChange = { editFarmSize = it },
                            label = { Text("Farm Size (Acres)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editSoilType,
                            onValueChange = { editSoilType = it },
                            label = { Text("Soil Type") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editMainCrops,
                            onValueChange = { editMainCrops = it },
                            label = { Text("Main Crops") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { isEditing = false }) {
                                Text("Cancel", color = AgrixMutedSilver)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.prefs.setOnboarded(
                                            name = editName.ifBlank { "Anshuman Yadav" },
                                            location = editLocation.ifBlank { "Ludhiana, Punjab" },
                                            farmSize = editFarmSize.ifBlank { "8.5" },
                                            soilType = editSoilType.ifBlank { "Alluvial Loam" },
                                            mainCrops = editMainCrops.ifBlank { "Wheat, Mustard" },
                                            language = p.languagePreference
                                        )
                                        isEditing = false
                                        Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AgrixNeonGreen, contentColor = androidx.compose.ui.graphics.Color.Black)
                            ) {
                                Text("Save Profile", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Quick Switch Profile / Native In-App Login
            MicroLabel("In-App Profile Switcher (1-Tap Login)")
            Spacer(Modifier.height(8.dp))
            Text(
                "Switch farmer identities instantly without any internet or external account:",
                style = MaterialTheme.typography.bodySmall,
                color = AgrixMutedSilver
            )
            Spacer(Modifier.height(12.dp))

            presetProfiles.forEach { preset ->
                val isCurrent = preset.name == p.name
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            if (!isCurrent) {
                                scope.launch {
                                    app.prefs.setOnboarded(
                                        name = preset.name,
                                        location = preset.location,
                                        farmSize = preset.farmSize,
                                        soilType = preset.soilType,
                                        mainCrops = preset.mainCrops,
                                        language = preset.languagePreference
                                    )
                                    Toast.makeText(context, "Switched to ${preset.name}!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.Bold, color = if (isCurrent) AgrixNeonGreen else AgrixWhite)
                            Text("${preset.location} • ${preset.farmSize} acres • ${preset.mainCrops}", style = MaterialTheme.typography.bodySmall, color = AgrixMutedSilver)
                        }
                        if (isCurrent) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = AgrixNeonGreen, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Switch", color = AgrixNeonGreen, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // On-Device AI Engine Status
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("On-Device AI Engine", fontWeight = FontWeight.Bold, color = AgrixWhite)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Status: Fully Active (Offline Intelligence Ready)\n" +
                        "All agronomic models, crop advisory, pest identification, and price forecasting run locally on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AgrixMutedSilver
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenModelSetup,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgrixWhite)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manage Model Weights / Hugging Face", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = AgrixMutedSilver)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AgrixWhite, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AgrixGlassBorder))
}
