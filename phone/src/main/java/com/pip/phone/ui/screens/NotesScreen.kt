package com.pip.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pip.phone.R
import com.pip.phone.data.NoteEntity
import com.pip.phone.data.NoteStatus
import com.pip.phone.data.PipDatabase
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note)
                }
            }
        }
    }
}

@Composable
private fun rememberDao() = androidx.compose.runtime.remember {
    PipDatabase.get(LocalContext.current).noteDao()
}

@Composable
private fun NoteCard(note: NoteEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = note.text.ifBlank { "…" },
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(note.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        NoteStatus.TRANSCRIBED -> context.getString(R.string.status_transcribed)
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