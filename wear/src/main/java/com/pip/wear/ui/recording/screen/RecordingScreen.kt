package com.pip.wear.ui.recording.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pip.wear.R
import com.pip.wear.audio.AudioQueueManager
import com.pip.wear.recording.DeliveryStatus
import com.pip.wear.recording.RecordingService
import kotlinx.coroutines.delay

private enum class UiState {
    IDLE,
    RECORDING,
    SENDING,
    RESULT
}

private const val SEND_TIMEOUT_MS = 2000L
private const val RESULT_DURATION_MS = 2000L

@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf(UiState.IDLE) }
    var tick by remember { mutableLongStateOf(0L) }
    var resultText by remember { mutableStateOf<String?>(null) }

    fun startCapture() {
        if (uiState == UiState.RECORDING) return
        val output = AudioQueueManager(context).newRecordingFile()
        RecordingService.postStart(context, output)
        tick = 0
        uiState = UiState.RECORDING
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCapture() }

    fun requestOrStart() {
        if (uiState != UiState.IDLE && uiState != UiState.RESULT) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startCapture() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Observe delivery status; only meaningful while SENDING.
    LaunchedEffect(Unit) {
        RecordingService.statusFlow.collect { status ->
            if (status != null && uiState == UiState.SENDING) {
                resultText = when (status) {
                    DeliveryStatus.Sent -> context.getString(R.string.status_sent)
                    DeliveryStatus.Queued -> context.getString(R.string.status_queued)
                    DeliveryStatus.PhoneUnreachable -> context.getString(R.string.status_no_phone)
                }
                uiState = UiState.RESULT
            }
        }
    }

    // Guard: if no status arrives within SEND_TIMEOUT_MS, treat as queued so the
    // circle never hangs on "Sending…" (clip is persisted locally either way).
    LaunchedEffect(uiState) {
        if (uiState == UiState.SENDING) {
            delay(SEND_TIMEOUT_MS)
            if (uiState == UiState.SENDING) {
                resultText = context.getString(R.string.status_queued)
                uiState = UiState.RESULT
            }
        }
    }

    // Show the result briefly, then return to idle.
    LaunchedEffect(uiState) {
        if (uiState == UiState.RESULT) {
            delay(RESULT_DURATION_MS)
            if (uiState == UiState.RESULT) {
                resultText = null
                uiState = UiState.IDLE
            }
        }
    }

    // Elapsed timer while recording
    LaunchedEffect(uiState) {
        while (uiState == UiState.RECORDING) {
            delay(500)
            tick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        RecordCircle(
            uiState = uiState,
            tick = tick,
            resultText = resultText,
            onPress = { requestOrStart() },
            onRelease = {
                if (uiState == UiState.RECORDING) {
                    uiState = UiState.SENDING
                    RecordingService.postStop(context)
                }
            }
        )
    }
}

@Composable
private fun RecordCircle(
    uiState: UiState,
    tick: Long,
    resultText: String?,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    // Pulse animation: only pulse when listening (RECORDING) or sending (SENDING)
    val shouldPulse = uiState == UiState.RECORDING || uiState == UiState.SENDING
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState == UiState.SENDING) 1.18f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (uiState) {
                    UiState.SENDING -> 400 // faster pulse when sending
                    UiState.RECORDING -> 700
                    else -> 1000
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val scaleModifier = if (shouldPulse) Modifier.scale(pulse) else Modifier

    Surface(
        shape = CircleShape,
        color = when (uiState) {
            UiState.RECORDING -> MaterialTheme.colorScheme.primary
            UiState.SENDING -> MaterialTheme.colorScheme.secondary
            UiState.RESULT -> MaterialTheme.colorScheme.primary
            UiState.IDLE -> MaterialTheme.colorScheme.tertiaryContainer
        },
        modifier = Modifier
            .size(140.dp)
            .then(scaleModifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        // Suspends until finger up OR the gesture is cancelled;
                        // either way the hold is over, so stop. Keyed on Unit so the
                        // state change (IDLE -> RECORDING) doesn't cancel this mid-hold.
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (uiState) {
                UiState.IDLE -> {
                    Text(
                        text = stringResource(R.string.hold_to_record),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                UiState.RECORDING -> {
                    val elapsed = tick * 500
                    val seconds = elapsed / 1000
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    Text(
                        text = "%d:%02d".format(minutes, secs),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
                UiState.SENDING -> {
                    Text(
                        text = stringResource(R.string.sending),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                UiState.RESULT -> {
                    Text(
                        text = resultText ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}