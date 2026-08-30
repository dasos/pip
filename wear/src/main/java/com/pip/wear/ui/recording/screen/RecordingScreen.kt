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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pip.wear.R
import com.pip.wear.audio.AudioQueueManager
import com.pip.wear.recording.DeliveryStatus
import com.pip.wear.recording.RecordingService
import kotlinx.coroutines.delay

@Composable
fun RecordingScreen() {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun startCapture() {
        if (isRecording) return
        val output = AudioQueueManager(context).newRecordingFile()
        RecordingService.postStart(context, output)
        startedAt = System.currentTimeMillis()
        isRecording = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCapture() }

    fun requestOrStart() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startCapture() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Observe one-shot delivery status.
    LaunchedEffect(Unit) {
        RecordingService.statusFlow.collect { status ->
            statusMessage = when (status) {
                DeliveryStatus.Sent -> context.getString(R.string.status_sent)
                DeliveryStatus.Queued -> context.getString(R.string.status_queued)
                else -> null
            }
            if (status != null) {
                delay(2000)
                statusMessage = null
            }
        }
    }

    // Elapsed timer while recording.
    LaunchedEffect(isRecording) {
        while (isRecording) {
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isRecording) {
                RecordingIndicator(startedAt, tick)
            }
            HoldButton(
                recording = isRecording,
                onPress = { requestOrStart() },
                onRelease = {
                    isRecording = false
                    RecordingService.postStop(context)
                },
            )
            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun HoldButton(
    recording: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.15f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (recording) 700 else 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        shape = CircleShape,
        color = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .size(140.dp)
            .scale(pulse)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        val released = tryAwaitRelease()
                        if (released) onRelease()
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "HOLD",
                color = if (recording) Color.White else MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RecordingIndicator(startedAt: Long, tick: Long) {
    val elapsed = System.currentTimeMillis() - startedAt
    val seconds = elapsed / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    Text(
        text = "%d:%02d".format(minutes, secs),
        color = Color.White,
        fontSize = 18.sp,
        fontFamily = FontFamily.Monospace
    )
}