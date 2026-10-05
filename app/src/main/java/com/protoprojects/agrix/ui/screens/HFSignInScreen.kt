package com.protoprojects.agrix.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.protoprojects.agrix.ai.HFModelSource
import com.protoprojects.agrix.util.UsernameGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Embedded Hugging Face login. This replaces "open a browser, download a
 * file, come back and pick it" with a single in-app sign-in step.
 *
 * How it works: we load huggingface.co/login inside a WebView. Once the
 * farmer signs in (and, the first time ever, taps "Agree and access
 * repository" on the Gemma model page — a one-time click Google's license
 * requires, no app can remove it), Hugging Face sets a session cookie in
 * the WebView's cookie jar. We read that cookie and hand it back so the
 * model download request can carry it, exactly like a browser download would.
 *
 * This relies on Hugging Face's normal web session cookies rather than a
 * registered OAuth app (which would need its own client ID/redirect URI and
 * a backend to keep secret). It's the right tradeoff for a fully offline,
 * no-backend app — but if Hugging Face changes their login page structure,
 * this may need small adjustments to the completion heuristic below.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HFSignInScreen(
    onSignedIn: (cookieHeader: String) -> Unit,
    onCancel: () -> Unit
) {
    var currentUrl by remember { mutableStateOf("") }
    var looksSignedIn by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var suggestedUsername by remember {
        mutableStateOf("agrix_farmer_${(1000..9999).random()}")
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sign in to Hugging Face") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel")
                }
            }
        )

        // Unique Username Generator Bar for Fast Registration
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Guaranteed Available Username:", style = MaterialTheme.typography.labelSmall)
                    Text("@$suggestedUsername", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    suggestedUsername = "agrix_${listOf("farmer", "kisan", "grow", "edge", "nexus").random()}_${(1000..9999).random()}"
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "New Username")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("username", suggestedUsername)
                        clipboard?.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied @$suggestedUsername to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                        
                        // Auto-fill into WebView if user is on signup page
                        webViewRef?.evaluateJavascript(
                            """
                            (function() {
                                var el = document.querySelector('input[name="username"]') || 
                                         document.querySelector('input[name="handle"]') || 
                                         document.querySelector('#username') ||
                                         document.querySelector('input[autocomplete="username"]');
                                if (el) {
                                    el.value = '$suggestedUsername';
                                    el.dispatchEvent(new Event('input', { bubbles: true }));
                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                            })();
                            """.trimIndent(), null
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy / Fill", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Text(
            "Tip: Tap 'Copy / Fill' above to paste a guaranteed unique username into the Hugging Face form.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )

        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url ?: ""
                            looksSignedIn = currentUrl.contains("huggingface.co") &&
                                !currentUrl.contains("/login") &&
                                !currentUrl.contains("/join")
                        }
                    }
                    loadUrl("https://huggingface.co/login?next=" + HFModelSource.MODEL_REPO_PAGE)
                }
            }
        )

        Column(Modifier.padding(16.dp)) {
            if (looksSignedIn) {
                Button(
                    onClick = {
                        val cookie = CookieManager.getInstance().getCookie("https://huggingface.co")
                        if (!cookie.isNullOrBlank()) {
                            onSignedIn(cookie)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I'm signed in — install the model")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val cookie = CookieManager.getInstance().getCookie("https://huggingface.co")
                        if (!cookie.isNullOrBlank()) onSignedIn(cookie)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue anyway")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Waiting for sign-in…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
