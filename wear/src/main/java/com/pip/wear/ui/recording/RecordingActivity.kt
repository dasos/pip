package com.pip.wear.ui.recording

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pip.wear.ui.recording.screen.RecordingScreen
import com.pip.wear.ui.theme.PipWearTheme

class RecordingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PipWearTheme {
                RecordingScreen()
            }
        }
    }
}