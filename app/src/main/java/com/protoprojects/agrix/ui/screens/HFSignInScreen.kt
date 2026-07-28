package com.protoprojects.agrix.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.protoprojects.agrix.ai.HFModelSource

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

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sign in to Hugging Face") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel")
                }
            }
        )

        Text(
            "Sign in below, then open the model page once and tap \"Agree and access repository\" " +
                "if you haven't before. This is a one-time step required by Google's Gemma license.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall
        )

        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url ?: ""
                            // Heuristic: once the WebView has navigated away from the
                            // login/join pages and onto a normal huggingface.co page,
                            // the farmer is signed in.
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
