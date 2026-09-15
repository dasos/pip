package com.pip.phone.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pip.phone.R
import com.pip.phone.data.NoteDao
import com.pip.phone.data.NoteEntity
import com.pip.phone.data.NoteQueueManager
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
import com.pip.phone.recording.RecorderController
import com.pip.phone.wear.PhoneWatchLink
import com.pip.phone.wear.requestWatchSync
import com.pip.phone.worker.AudioUploadWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val dao = rememberDao()

    val notes by produceState<List<NoteEntity>>(initialValue = emptyList()) {
        dao.observeAll().collect { value = it }
    }

    var previewPath by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        isRefreshing = true
        try {
            requestWatchSync(context)
            AudioUploadWorker.enqueue(context)
        } finally {
            isRefreshing = false
        }
    }

    val watchConnected by PhoneWatchLink.watchConnected.collectAsState()

    // Press-and-hold capture. In-process recorder (no foreground service — the
    // user's finger is on the button, so the activity stays foreground for the
    // whole clip).
    // ponytail: a mid-hold activity destroy (rotation, swipe-away, kill) drops
    // the partial clip. If background capture is ever wanted, mirror the
    // watch's microphone-type foreground service instead.
    val recorder = remember { RecorderController(context) }
    var isRecording by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }

    fun startRecording() {
        if (isRecording) return
        if (recorder.start()) {
            tick = 0
            isRecording = true
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        val result = recorder.stop() ?: return
        isRecording = false
        if (result.tooShort) {
            Toast.makeText(context, R.string.hold_to_record, Toast.LENGTH_SHORT).show()
            return
        }
        val captured = result.captured ?: return
        scope.launch {
            val dao = PipDatabase.get(context).noteDao()
            dao.insert(
                NoteEntity(
                    createdAt = captured.createdAt,
                    status = NoteStatus.PENDING,
                    audioPath = captured.file.absolutePath,
                )
            )
            NoteQueueManager(context).enforcePolicies(dao)
            AudioUploadWorker.enqueue(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }

    fun requestOrStart() {
        if (isRecording) return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Elapsed timer while recording.
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(500)
            tick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.notes_title)) },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(context.getString(R.string.open_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            RecordFab(
                isRecording = isRecording,
                tick = tick,
                onPress = { requestOrStart() },
                onRelease = { stopRecording() },
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!watchConnected) {
                    Text(
                        text = stringResource(R.string.watch_not_connected),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.empty_notes))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                dao = dao,
                                isPlaying = previewPath != null && previewPath == note.audioPath,
                                onTogglePlay = {
                                    previewPath = if (previewPath == note.audioPath) null else note.audioPath
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Owns the MediaPlayer lifecycle for the selected file.
    PlaybackPlayer(filePath = previewPath) { completedPath ->
        if (previewPath == completedPath) previewPath = null
    }
}

/** Creates/releases a [MediaPlayer] for [filePath]; null stops playback. */
@Composable
private fun PlaybackPlayer(filePath: String?, onCompleted: (String) -> Unit) {
    DisposableEffect(filePath) {
        val player = filePath?.let { path ->
            runCatching {
                MediaPlayer().apply {
                    setDataSource(File(path).absolutePath)
                    setOnCompletionListener { onCompleted(path) }
                    prepare()
                    start()
                }
            }.getOrElse {
                onCompleted(path)
                null
            }
        }
        onDispose {
            player?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }
}

@Composable
private fun rememberDao(): NoteDao {
    val context = LocalContext.current
    return remember { PipDatabase.get(context).noteDao() }
}

private enum class RevealValue { Closed, EndOpen, StartOpen }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: NoteEntity, dao: NoteDao, isPlaying: Boolean, onTogglePlay: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actionWidth = 88.dp
    val density = LocalDensity.current
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val reveal = remember {
        AnchoredDraggableState(
            initialValue = RevealValue.Closed,
            anchors = DraggableAnchors {
                RevealValue.Closed at 0f
                RevealValue.EndOpen at -actionWidthPx
                RevealValue.StartOpen at actionWidthPx
            },
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 140.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMedium),
            decayAnimationSpec = splineBasedDecay(density)
        )
    }
    val offset = reveal.offset

    Box(modifier = Modifier.fillMaxWidth()) {
        // Actions behind the card: upload on the left, delete on the right.
        Row(modifier = Modifier.fillMaxSize()) {
            RevealAction(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = Icons.Filled.PlayArrow,
                label = context.getString(R.string.upload_note),
                onClick = {
                    scope.launch {
                        dao.update(note.copy(status = NoteStatus.PENDING))
                        AudioUploadWorker.enqueue(context)
                        reveal.animateTo(RevealValue.Closed)
                    }
                },
                modifier = Modifier.width(actionWidth).fillMaxHeight()
            )
            Spacer(modifier = Modifier.weight(1f))
            RevealAction(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                icon = Icons.Filled.Delete,
                label = context.getString(R.string.delete_note),
                onClick = {
                    scope.launch {
                        note.audioPath?.let { File(it).delete() }
                        dao.delete(note.id)
                    }
                },
                modifier = Modifier.width(actionWidth).fillMaxHeight()
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .anchoredDraggable(reveal, Orientation.Horizontal)
                .clickable { scope.launch { reveal.animateTo(RevealValue.Closed) } }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = context.getString(R.string.play_recording)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = formatTime(note.createdAt), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.size(4.dp))
                    StatusBadge(note.status)
                }
            }
        }
    }
}

/** Full-height, fixed-width action revealed behind the card when dragged. */
@Composable
private fun RevealAction(
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor)
            Text(text = label, color = contentColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusBadge(status: NoteStatus) {
    val context = LocalContext.current
    val label = when (status) {
        NoteStatus.PENDING -> context.getString(R.string.status_pending)
        NoteStatus.UPLOADED -> context.getString(R.string.status_uploaded)
        NoteStatus.FAILED -> context.getString(R.string.status_failed)
    }
    val color = when (status) {
        NoteStatus.UPLOADED -> MaterialTheme.colorScheme.primary
        NoteStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

private fun formatTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

/** Bottom-right hold-to-record button: mic icon when idle, pulsing timer while recording. */
@Composable
private fun RecordFab(
    isRecording: Boolean,
    tick: Long,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    // Pulse only while recording; when idle the target equals the start value,
    // so the scale stays visually at 1f.
    val transition = rememberInfiniteTransition(label = "recordPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRecording) 700 else 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recordPulseScale"
    )
    Surface(
        shape = CircleShape,
        color = if (isRecording) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(72.dp)
            .scale(pulse)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        // Suspends until the finger lifts (or the gesture is
                        // cancelled); either way the hold is over, so stop.
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isRecording) {
                Text(
                    text = formatRecElapsed(tick),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.hold_to_record),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun formatRecElapsed(tick: Long): String {
    val seconds = tick * 500 / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}