package com.pip.wear.recording

/** Transient outcome of a completed capture, shown briefly in the UI. */
sealed interface DeliveryStatus {
    data object Sent : DeliveryStatus
    data object Queued : DeliveryStatus
}