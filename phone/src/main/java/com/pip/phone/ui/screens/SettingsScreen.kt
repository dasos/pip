package com.pip.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.pip.phone.R
import com.pip.phone.config.ServerConfig
import com.pip.phone.wear.pushConfigToWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private suspend fun testConnection(serverUrl: String, token: String): Boolean = withContext(Dispatchers.IO) {
    if (serverUrl.isBlank()) return@withContext false
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url(serverUrl.trim().trimEnd('/'))
        .addHeader("Authorization", "Bearer $token")
        .head()
        .build()
    try {
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Throwable) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val config = ServerConfig(context)
    val existing = config.load()

    var serverUrl by remember { mutableStateOf(existing?.serverUrl ?: "") }
    var token by remember { mutableStateOf(existing?.bearerToken ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(context.getString(R.string.settings_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(context.getString(R.string.server_url_label)) },
                placeholder = { Text(context.getString(R.string.server_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(context.getString(R.string.bearer_token_label)) },
                placeholder = { Text(context.getString(R.string.bearer_token_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val ok = config.save(ServerConfig.Config(serverUrl, token))
                    status = if (ok) context.getString(R.string.config_saved)
                    else context.getString(R.string.config_error)
                    if (ok) {
                        scope.launch { pushConfigToWatch(context, serverUrl, token) }
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.save_config))
            }
            Button(
                onClick = {
                    scope.launch {
                        status = if (testConnection(serverUrl, token)) {
                            context.getString(R.string.connection_ok)
                        } else {
                            context.getString(R.string.connection_failed)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.test_connection))
            }
            status?.let {
                Spacer(Modifier.height(4.dp))
                Text(it)
            }
        }
    }
}