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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pip.phone.R
import com.pip.phone.data.NoteDao
import com.pip.phone.data.NoteEntity
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
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

    // The audio file currently being previewed, or null when stopped.
    var previewPath by remember { mutableStateOf<String?>(null) }

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
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(context.getString(R.string.empty_notes))
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        isPlaying = previewPath != null && previewPath == note.audioPath,
                        onTogglePlay = {
                            previewPath = if (previewPath == note.audioPath) null else note.audioPath
                        }
                    )
                }
            }
        }
    }

    // Owns the MediaPlayer lifecycle for the selected file.
    PlaybackPlayer(filePath = previewPath)
}

/** Creates/releases a [MediaPlayer] for [filePath]; null stops playback. */
@Composable
private fun PlaybackPlayer(filePath: String?) {
    DisposableEffect(filePath) {
        val player = if (filePath != null) {
            MediaPlayer().apply {
                setDataSource(File(filePath).absolutePath)
                setOnCompletionListener { runCatching { stop() } }
                prepare()
                start()
            }
        } else {
            null
        }
        onDispose {
            if (player != null) {
                runCatching { player.stop() }
                player.release()
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
private fun NoteCard(note: NoteEntity, isPlaying: Boolean, onTogglePlay: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                Text(
                    text = formatTime(note.createdAt),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.size(4.dp))
                StatusBadge(note.status)
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