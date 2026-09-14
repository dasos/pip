package com.pip.phone.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether a Wear OS watch is currently connected to the phone,
 * fed by [WearListenerService] peer callbacks and shown in the notes UI.
 */
object PhoneWatchLink {
    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    fun onWatchConnected() = setWatchConnected(true)

    fun onWatchDisconnected() = setWatchConnected(false)

    fun setWatchConnected(connected: Boolean) {
        _watchConnected.value = connected
    }
}