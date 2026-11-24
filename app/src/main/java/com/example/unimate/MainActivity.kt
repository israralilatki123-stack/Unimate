package com.example.unimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.unimate.data.firebase.configureAppCheck
import com.example.unimate.navigation.UniMateNavHost
import com.example.unimate.ui.theme.UniMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before any Compose UI, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val appContainer = (application as UniMateApplication).appContainer
        setContent {
            UniMateTheme {
                UniMateNavHost(appContainer = appContainer)
            }
        }
    }
}
