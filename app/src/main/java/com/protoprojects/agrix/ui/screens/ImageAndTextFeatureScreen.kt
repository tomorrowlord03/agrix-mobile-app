package com.protoprojects.agrix.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.protoprojects.agrix.AgriXApp
import com.protoprojects.agrix.ui.components.GlassCard
import com.protoprojects.agrix.ui.components.MicroLabel
import com.protoprojects.agrix.ui.components.NeonPrimaryButton
import com.protoprojects.agrix.ui.theme.*
import com.protoprojects.agrix.util.loadBitmapFromUri
import com.protoprojects.agrix.viewmodel.GemmaQueryViewModel

/**
 * Like SimpleTextFeatureScreen, but with an optional photo attached to the
 * prompt — used for the two screens where a picture genuinely helps
 * (Disease Detector, Produce Grading), styled like the design spec's
 * camera-forward diagnostic tool: a glowing green targeting frame around
 * the attached photo, and a glass results panel below.
 *
 * Uses the system Photo Picker (ActivityResultContracts.PickVisualMedia),
 * which needs no storage permission on any supported API level.
 *
 * The photo option only appears if the currently loaded model was actually
 * set up to accept images (GemmaInferenceEngine.visionCapable) — the model
 * this app installs by default (Gemma 3 1B IT) is text-only, so offering a
 * photo picker that would just fail (or worse, crash native code) isn't
 * honest UI. The screen still works fully on text alone either way.
 */
@Composable
fun ImageAndTextFeatureScreen(
    title: String,
    subtitle: String,
    fieldLabel: String,
    buttonLabel: String,
    buildPrompt: (String) -> String,
    viewModel: GemmaQueryViewModel = viewModel()
) {
    val context = LocalContext.current
    val visionCapable = remember { (context.applicationContext as AgriXApp).gemma.visionCapable }
    var input by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val state by viewModel.state.collectAsState()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        imageUri = uri
        imageBitmap = uri?.let { loadBitmapFromUri(context, it) }
    }

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

        if (imageBitmap != null && visionCapable) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, AgrixNeonGreen.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AgrixSurfaceGrey.copy(alpha = 0.85f))
                        .border(1.dp, AgrixGlassBorder, RoundedCornerShape(50))
                ) {
                    IconButton(onClick = { imageUri = null; imageBitmap = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = AgrixWhite)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (visionCapable) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(Modifier)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = AgrixNeonGreen, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add a photo (optional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AgrixWhite,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Choose from gallery", color = AgrixNeonGreen)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = AgrixMutedSilver, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This on-device model is text-only — describe what you see below instead of a photo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
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
        Spacer(Modifier.height(16.dp))
        NeonPrimaryButton(
            text = buttonLabel,
            onClick = { viewModel.ask(buildPrompt(input), if (visionCapable) imageBitmap else null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank()
        )
        ResultArea(state)
    }
}
