package com.example.spatialchess.platform

import android.app.Application
import com.example.spatialchess.app.ChessViewModel
import com.example.spatialchess.debug.DebugReceiver
import com.example.spatialchess.mainApp
import com.pico.spatial.ui.foundation.dsl.launch

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // App-scoped state + the adb test harness are created before the first window opens.
        DebugReceiver.register(this, ChessViewModel.get(this))
        launch(::mainApp)
    }
}
