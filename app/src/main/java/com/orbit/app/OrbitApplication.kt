package com.orbit.app

import android.app.Application
import com.orbit.app.core.AppContainer
import kotlinx.coroutines.launch

class OrbitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            runCatching { container.conversations.markStreamingInterrupted() }
        }
    }
}
