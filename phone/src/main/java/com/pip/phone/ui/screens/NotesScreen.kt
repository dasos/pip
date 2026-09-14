package com.pip.phone.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pip.phone.R
import com.pip.phone.data.NoteDao
import com.pip.phone.data.NoteEntity
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
import com.pip.phone.wear.PhoneWatchLink
import com.pip.phone.wear.requestWatchSync
import com.pip.phone.worker.AudioUploadWorker
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val dao = rememberDao()

    val notes by produceState<List<NoteEntity>>(initialValue = emptyList()) {
        dao.observeAll().collect { value = it }
    }

    val scope = rememberCoroutineScope()
    var previewPath by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val watchConnected by PhoneWatchLink.watchConnected.collectAsState()

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
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    requestWatchSync(context)
                    AudioUploadWorker.enqueue(context)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding).fillMaxSize()
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
            MediaPlayer().apply {
                setDataSource(File(path).absolutePath)
                setOnCompletionListener { onCompleted(path) }
                prepare()
                start()
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

@Composable
private fun NoteCard(note: NoteEntity, dao: NoteDao, isPlaying: Boolean, onTogglePlay: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    scope.launch {
                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            note.audioPath?.let { File(it).delete() }
                            dao.delete(note.id)
                        } else {
                            dao.update(note.copy(status = NoteStatus.PENDING))
                            AudioUploadWorker.enqueue(context)
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Filled.Delete else Icons.Filled.PlayArrow,
                        contentDescription = context.getString(if (direction == SwipeToDismissBoxValue.StartToEnd) R.string.delete_note else R.string.upload_note)
                    )
                    Text(context.getString(if (direction == SwipeToDismissBoxValue.StartToEnd) R.string.delete_note else R.string.upload_note))
                }
            }
        }
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
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