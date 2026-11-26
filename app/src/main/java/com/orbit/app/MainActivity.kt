package com.orbit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.data.model.ThemeMode
import com.orbit.app.data.prefs.AppSettings
import com.orbit.app.ui.LocalAppContainer
import com.orbit.app.ui.nav.OrbitApp
import com.orbit.app.ui.theme.OrbitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OrbitApplication).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            val darkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                OrbitTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                    OrbitApp()
                }
            }
        }
    }
}
