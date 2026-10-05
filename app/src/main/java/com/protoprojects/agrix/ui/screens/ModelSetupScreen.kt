package com.protoprojects.agrix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.protoprojects.agrix.ai.DownloadState
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.*
import com.protoprojects.agrix.viewmodel.ModelLoadState
import com.protoprojects.agrix.viewmodel.ModelSetupViewModel

private enum class SetupStage { INTRO, SIGNING_IN }

/**
 * Shown on first launch before anything else. The app is unusable until a
 * Gemma model is present on-device.
 *
 * Primary path: tap "Sign in & install AI model" -> embedded Hugging Face
 * login (HFSignInScreen) -> the moment sign-in succeeds, the model downloads
 * and installs automatically, no file picker involved.
 *
 * Fallback path: "I already have the model file", for anyone who prefers to
 * grab the .task file themselves and hand it to the app directly.
 *
 * Advancing to onboarding waits for the model to actually finish loading
 * into memory (ModelLoadState.Ready), not just for the file to finish
 * downloading — a farmer landing on a feature screen before the model has
 * loaded is exactly what used to produce empty answers.
 */
@Composable
fun ModelSetupScreen(
    viewModel: ModelSetupViewModel = viewModel(),
    onModelReady: () -> Unit
) {
    var stage by remember { mutableStateOf(SetupStage.INTRO) }
    val downloadState by viewModel.downloadState.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLocalFile(it) }
    }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Done) {
            viewModel.finishSetup()
        }
    }
    LaunchedEffect(loadState) {
        if (loadState is ModelLoadState.Ready) {
            onModelReady()
        }
    }

    if (stage == SetupStage.SIGNING_IN && downloadState == null) {
        HFSignInScreen(
            onSignedIn = { cookie -> viewModel.downloadAfterSignIn(cookie) },
            onCancel = { stage = SetupStage.INTRO }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgrixDeepBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AgrixSurfaceGreyElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Eco, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(20.dp))
        MicroLabel("System status: setup required")
        Spacer(Modifier.height(8.dp))
        Text(
            "Set up your on-device AI",
            style = MaterialTheme.typography.headlineLarge,
            color = AgrixWhite,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Agrix runs a Gemma AI model directly on your phone. Nothing you type or photograph " +
                "is ever sent to a server. Sign in once below to install the model, then the app works " +
                "fully offline, even with no signal.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(32.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                SetupBody(
                    downloadState = downloadState,
                    loadState = loadState,
                    onSignInClick = { stage = SetupStage.SIGNING_IN },
                    onImportClick = { filePicker.launch(arrayOf("*/*")) },
                    onDemoClick = { viewModel.launchDemoMode() },
                    onRetryClick = { viewModel.resetState(); stage = SetupStage.SIGNING_IN }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Why does this need a sign-in?", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, color = AgrixWhite)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Google requires anyone downloading the 550MB Gemma model to accept its license on Hugging Face once. " +
                        "Note: If registering a new account and Hugging Face says \"Username is not available\", " +
                        "it means that username is already taken worldwide on huggingface.co — try adding numbers or tap \"Offline Demo Mode\" above.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SetupBody(
    downloadState: DownloadState?,
    loadState: ModelLoadState,
    onSignInClick: () -> Unit,
    onImportClick: () -> Unit,
    onDemoClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    when {
        loadState is ModelLoadState.Failed -> {
            Text("Setup failed: ${loadState.message}", color = AgrixWarnRed, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            SetupButtons(onSignInClick = onRetryClick, onImportClick = onImportClick, onDemoClick = onDemoClick)
        }
        loadState is ModelLoadState.Loading -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(color = AgrixNeonGreen)
                Spacer(Modifier.height(12.dp))
                Text("Loading the model into memory…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        downloadState is DownloadState.Progress -> {
            val pct = if (downloadState.totalBytes > 0)
                (downloadState.bytesRead * 100 / downloadState.totalBytes).toInt() else null
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (pct != null) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AgrixNeonGreen,
                        trackColor = AgrixGlassBorderStrong
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Installing model… $pct%", color = AgrixWhite)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AgrixNeonGreen, trackColor = AgrixGlassBorderStrong)
                    Spacer(Modifier.height(8.dp))
                    Text("Installing model… ${downloadState.bytesRead / (1024 * 1024)} MB", color = AgrixWhite)
                }
            }
        }
        downloadState is DownloadState.Error -> {
            Text("Setup failed: ${downloadState.message}", color = AgrixWarnRed, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            SetupButtons(onSignInClick = onRetryClick, onImportClick = onImportClick, onDemoClick = onDemoClick)
        }
        else -> {
            SetupButtons(onSignInClick = onSignInClick, onImportClick = onImportClick, onDemoClick = onDemoClick)
        }
    }
}

@Composable
private fun SetupButtons(
    onSignInClick: () -> Unit,
    onImportClick: () -> Unit,
    onDemoClick: () -> Unit
) {
    NeonPrimaryButton(
        text = "Sign in & install AI model",
        onClick = onSignInClick,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(Icons.Filled.Login, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(18.dp).padding(end = 4.dp)) }
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onImportClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgrixWhite)
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("I already have the model file")
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onDemoClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgrixNeonGreen),
        border = androidx.compose.foundation.BorderStroke(1.dp, AgrixNeonGreen.copy(alpha = 0.6f))
    ) {
        Icon(Icons.Filled.Eco, contentDescription = null, tint = AgrixNeonGreen)
        Spacer(Modifier.width(8.dp))
        Text("Continue in Offline Demo Mode (No Login)", color = AgrixNeonGreen, fontWeight = FontWeight.SemiBold)
    }
}
