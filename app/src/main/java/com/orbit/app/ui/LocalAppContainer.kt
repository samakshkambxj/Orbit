package com.orbit.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.orbit.app.core.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}
