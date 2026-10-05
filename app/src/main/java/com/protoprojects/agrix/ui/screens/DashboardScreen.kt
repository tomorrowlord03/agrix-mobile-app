package com.protoprojects.agrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.GlassIconButton
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.theme.*

data class FeatureItem(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

val allFeatures = listOf(
    FeatureItem("Crop Suggester", "Best crops for this season", Icons.Filled.Grass, "crop_suggester"),
    FeatureItem("Price Predictor", "Market price forecast", Icons.Filled.TrendingUp, "price_predictor"),
    FeatureItem("Soil Recommender", "From your soil test report", Icons.Filled.Terrain, "soil_recommender"),
    FeatureItem("Pest Control", "Identify pests, get treatment", Icons.Filled.BugReport, "pest_control"),
    FeatureItem("Disease Detector", "Diagnose from photo + notes", Icons.Filled.HealthAndSafety, "disease_detector"),
    FeatureItem("Produce Grading", "Grade your harvest quality", Icons.Filled.Grade, "produce_grading"),
    FeatureItem("Profit Advisor", "Increase this season's margin", Icons.Filled.Savings, "profit_advisor"),
    FeatureItem("Irrigation", "Precision watering schedule", Icons.Filled.WaterDrop, "irrigation"),
    FeatureItem("Livestock", "Veterinary-style guidance", Icons.Filled.Pets, "livestock"),
)

private val bentoFeatures = listOf(
    allFeatures[0], allFeatures[1], allFeatures[3], allFeatures[2]
)

@Composable
fun DashboardScreen(app: AgriXApp? = null, onOpenFeature: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val profile by (app?.prefs?.farmerProfile?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
    val farmerName = profile?.name ?: "Anshuman Yadav"
    val filtered = remember(query) {
        if (query.isBlank()) allFeatures
        else allFeatures.filter { it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(AgrixDeepBlack)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 120.dp)
        ) {
            DashboardHeader(
                farmerName = farmerName,
                onProfileClick = { onOpenFeature("profile") }
            )
            Spacer(Modifier.height(20.dp))
            SearchBar(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(20.dp))

            if (query.isBlank()) {
                HeroDiagnosisCard(onClick = { onOpenFeature("disease_detector") })
                Spacer(Modifier.height(24.dp))
                MicroLabel("Quick Actions")
                Spacer(Modifier.height(10.dp))
                BentoGrid(items = bentoFeatures, onOpenFeature = onOpenFeature)
                Spacer(Modifier.height(28.dp))
                MicroLabel("All Tools")
                Spacer(Modifier.height(10.dp))
            } else {
                MicroLabel("Results")
                Spacer(Modifier.height(10.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (if (query.isBlank()) allFeatures else filtered).forEach { feature ->
                    ToolListRow(feature, onClick = { onOpenFeature(feature.route) })
                }
                if (filtered.isEmpty() && query.isNotBlank()) {
                    Text("No tools match \"$query\".", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        FloatingBottomNav(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            onDashboard = { /* already here */ },
            onScan = { onOpenFeature("disease_detector") },
            onProfile = { onOpenFeature("profile") }
        )
    }
}

@Composable
private fun DashboardHeader(farmerName: String, onProfileClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AgrixSurfaceGreyElevated)
                .border(1.dp, AgrixNeonGreen.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onProfileClick)) {
            MicroLabel("Farmer: $farmerName")
            Text("Farm Status: Optimal", style = MaterialTheme.typography.titleMedium, color = AgrixWhite)
        }
        GlassIconButton(icon = Icons.Filled.AccountCircle, contentDescription = "Profile", onClick = onProfileClick)
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(AgrixSurfaceGrey.copy(alpha = 0.7f), shape)
            .border(1.dp, AgrixGlassBorder, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = AgrixMutedSilver, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search crops, pathogens, tools",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgrixDimSilver
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = AgrixWhite, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AgrixNeonGreen),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HeroDiagnosisCard(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        padding = 22.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AgrixNeonGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("AI Crop Diagnosis", style = MaterialTheme.typography.titleLarge, color = AgrixWhite, fontWeight = FontWeight.Bold)
                    Text("Detect diseases instantly, on-device", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            com.protoprojects.agrix.ui.components.NeonPrimaryButton(text = "Launch Scanner", onClick = onClick)
        }
    }
}

@Composable
private fun BentoGrid(items: List<FeatureItem>, onOpenFeature: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.heightIn(max = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(items) { feature ->
            GlassCard(
                modifier = Modifier.fillMaxWidth().clickable { onOpenFeature(feature.route) },
                padding = 16.dp
            ) {
                Column {
                    Icon(feature.icon, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(feature.title, style = MaterialTheme.typography.titleSmall, color = AgrixWhite, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ToolListRow(feature: FeatureItem, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        padding = 16.dp
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AgrixSurfaceGreyElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(feature.icon, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.title, style = MaterialTheme.typography.titleSmall, color = AgrixWhite, fontWeight = FontWeight.SemiBold)
                Text(feature.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AgrixDimSilver, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FloatingBottomNav(
    modifier: Modifier = Modifier,
    onDashboard: () -> Unit,
    onScan: () -> Unit,
    onProfile: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(AgrixSurfaceGrey.copy(alpha = 0.92f), shape)
            .border(1.dp, AgrixGlassBorder, shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavPillItem("Dashboard", Icons.Filled.Home, active = true, onClick = onDashboard)
        NavPillItem("Scan", Icons.Filled.CenterFocusStrong, active = false, onClick = onScan)
        NavPillItem("Profile", Icons.Filled.Person, active = false, onClick = onProfile)
    }
}

@Composable
private fun NavPillItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .then(if (active) Modifier.background(AgrixNeonGreen.copy(alpha = 0.15f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (active) AgrixNeonGreen else AgrixMutedSilver, modifier = Modifier.size(18.dp))
        if (active) {
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = AgrixNeonGreen)
        }
    }
}
