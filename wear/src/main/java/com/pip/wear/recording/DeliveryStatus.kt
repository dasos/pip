package com.pip.wear.recording

/** Transient outcome of a completed capture, shown briefly in the UI. */
sealed interface DeliveryStatus {
    data object Sent : DeliveryStatus
    data object Queued : DeliveryStatus

    /** No phone node is connected, so the clip was kept locally for later retry. */
    data object PhoneUnreachable : DeliveryStatus
}