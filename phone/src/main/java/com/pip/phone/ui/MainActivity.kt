package com.pip.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pip.phone.config.ServerConfig
import com.pip.phone.ui.screens.NotesScreen
import com.pip.phone.ui.screens.SettingsScreen
import com.pip.phone.ui.theme.PipPhoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = ServerConfig(this)

        setContent {
            PipPhoneTheme {
                var showSettings by remember { mutableStateOf(!config.isConfigured()) }

                if (showSettings) {
                    SettingsScreen(
                        onClose = { showSettings = false }
                    )
                } else {
                    NotesScreen(
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }
    }
}