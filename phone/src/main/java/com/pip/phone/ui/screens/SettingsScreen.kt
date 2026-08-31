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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import android.content.Context

/** Outcome of a "Test connection" attempt, with a reason so the UI can explain itself. */
private sealed interface TestResult {
    data object Success : TestResult
    data object InvalidUrl : TestResult
    data object Unauthorized : TestResult
    data object TimedOut : TestResult
    data class HttpError(val code: Int) : TestResult
    data class NetworkError(val detail: String) : TestResult
}

private suspend fun testConnection(serverUrl: String, token: String): TestResult = withContext(Dispatchers.IO) {
    // Ping the dedicated health endpoint: GET {base}/health/audio.
    // Side-effect free, checks both reachability and token validity.
    val url = (serverUrl.trim().trimEnd('/') + "/health/audio").toHttpUrlOrNull()
        ?: return@withContext TestResult.InvalidUrl
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $token")
        .get()
        .build()
    try {
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> TestResult.Success
                response.code == 401 || response.code == 403 -> TestResult.Unauthorized
                else -> TestResult.HttpError(response.code)
            }
        }
    } catch (_: SocketTimeoutException) {
        TestResult.TimedOut
    } catch (e: IOException) {
        TestResult.NetworkError(e.message ?: e.javaClass.simpleName)
    }
}

private fun TestResult.message(context: Context): String = when (this) {
    TestResult.Success -> context.getString(R.string.connection_ok)
    TestResult.InvalidUrl -> context.getString(R.string.connection_invalid_url)
    TestResult.Unauthorized -> context.getString(R.string.connection_unauthorized)
    TestResult.TimedOut -> context.getString(R.string.connection_timeout)
    is TestResult.HttpError -> context.getString(R.string.connection_http_error, code)
    is TestResult.NetworkError -> context.getString(R.string.connection_unreachable, detail)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val config = ServerConfig(context)
    val existing = config.load()

    var serverUrl by remember { mutableStateOf(existing?.serverUrl ?: "") }
    var token by remember { mutableStateOf(existing?.bearerToken ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = context.getString(R.string.close_settings)
                        )
                    }
                }
            )
        }
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
                    if (ok) {
                        scope.launch { pushConfigToWatch(context, serverUrl, token) }
                        status = context.getString(R.string.connection_testing)
                        scope.launch {
                            status = testConnection(serverUrl, token).message(context)
                        }
                    } else {
                        status = context.getString(R.string.config_error)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.save_config))
            }
            TextButton(
                onClick = {
                    scope.launch {
                        status = testConnection(serverUrl, token).message(context)
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