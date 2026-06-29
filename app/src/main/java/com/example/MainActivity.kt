package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TimetableDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TimetableViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Check system standard dark theme default
            val systemInDark = isSystemInDarkTheme()
            
            // Allow manual toggle fallback, starting with accessibility-friendly dark mode active by default!
            var darkThemeOverride by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = darkThemeOverride) {
                val viewModel: TimetableViewModel = viewModel()
                TimetableDashboard(
                    viewModel = viewModel,
                    darkThemeOverride = darkThemeOverride,
                    onToggleDarkTheme = { darkThemeOverride = !darkThemeOverride },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
