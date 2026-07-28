package com.protoprojects.agrix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.protoprojects.agrix.ui.navigation.AgriXNavHost
import com.protoprojects.agrix.ui.theme.AgriXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AgriXApp

        setContent {
            AgriXTheme {
                // Explicitly the "background" token (deep black), not Surface's
                // own default of "surface" (dark grey) — screens that don't set
                // their own background should still land on true black, matching
                // the "Deep Space Black" OLED-optimized design spec.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AgriXNavHost(app = app)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release native model resources when the app is fully closing.
        if (isFinishing) {
            (application as AgriXApp).gemma.close()
        }
    }
}
