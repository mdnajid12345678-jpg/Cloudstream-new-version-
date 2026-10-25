package com.lagradost.cloudstream3.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun ImportSystemPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloudstream_import_system", 0) }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var projectUrl by remember { mutableStateOf(prefs.getString("project_url", "") ?: "") }
    var anonKey by remember { mutableStateOf(prefs.getString("anon_key", "") ?: "") }
    var status by remember { mutableStateOf("Not connected") }
    var busy by remember { mutableStateOf(false) }
    val providers = remember { APIHolder.apis.map { it.name }.distinct().sorted() }
    val selected = remember { mutableStateOf(prefs.getStringSet("providers", emptySet())?.toSet() ?: emptySet()) }

    Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Import System · Supabase")
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("Import System") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Supabase connection and installed provider selection")
                    OutlinedTextField(
                        value = projectUrl,
                        onValueChange = { projectUrl = it },
                        label = { Text("Supabase Project URL") },
                        placeholder = { Text("https://your-project.supabase.co") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = anonKey,
                        onValueChange = { anonKey = it },
                        label = { Text("Publishable / anon key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Text("Installed providers (${providers.size})")
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(providers) { provider ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = provider in selected.value,
                                    onCheckedChange = { checked ->
                                        selected.value = if (checked) selected.value + provider else selected.value - provider
                                    }
                                )
                                Text(provider, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            if (!projectUrl.startsWith("https://") || anonKey.isBlank()) {
                                status = "Enter a valid HTTPS project URL and publishable key."
                                return@TextButton
                            }
                            busy = true
                            status = "Testing Supabase connection…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val base = projectUrl.trim().trimEnd('/')
                                        val connection = URL("$base/rest/v1/media_items?select=id&limit=1").openConnection() as HttpURLConnection
                                        try {
                                            connection.requestMethod = "GET"
                                            connection.connectTimeout = 15000
                                            connection.readTimeout = 15000
                                            connection.setRequestProperty("apikey", anonKey.trim())
                                            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")
                                            connection.setRequestProperty("Accept", "application/json")
                                            val code = connection.responseCode
                                            if (code !in 200..299) {
                                                val detail = (connection.errorStream ?: connection.inputStream).bufferedReader().use { it.readText() }
                                                error("HTTP $code: ${detail.take(180)}")
                                            }
                                            "Connected successfully"
                                        } finally {
                                            connection.disconnect()
                                        }
                                    }.getOrElse { "Connection failed: ${it.message ?: "unknown error"}" }
                                }
                                status = result
                                busy = false
                                if (result == "Connected successfully") {
                                    prefs.edit()
                                        .putString("project_url", projectUrl.trim().trimEnd('/'))
                                        .putString("anon_key", anonKey.trim())
                                        .putStringSet("providers", selected.value.toMutableSet())
                                        .apply()
                                }
                            }
                        }
                    ) { Text("Test & Save") }
                    TextButton(onClick = { if (!busy) open = false }) { Text("Close") }
                }
            },
            dismissButton = {}
        )
    }
}
