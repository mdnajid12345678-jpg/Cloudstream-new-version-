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
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

@Composable
internal fun ImportSystemPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloudstream_import_system", 0) }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var projectUrl by remember { mutableStateOf(prefs.getString("project_url", "") ?: "") }
    var anonKey by remember { mutableStateOf(prefs.getString("anon_key", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var fromYear by remember { mutableStateOf("2025") }
    var toYear by remember { mutableStateOf("2026") }
    var status by remember { mutableStateOf("Sign in to your Supabase account to import.") }
    var busy by remember { mutableStateOf(false) }
    var createAccount by remember { mutableStateOf(false) }
    var accessToken by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var importedCount by remember { mutableStateOf(0) }
    var sourceCount by remember { mutableStateOf(0) }
    val providers = remember { APIHolder.apis.map { it }.distinctBy { it.name }.sortedBy { it.name } }
    val selected = remember { mutableStateOf(prefs.getStringSet("providers", emptySet())?.toSet() ?: emptySet()) }
    val categories = listOf("anime", "movie", "series", "ova", "ona", "special", "other")
    val selectedCategories = remember { mutableStateOf(setOf("anime", "movie", "series")) }

    Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Import System · Supabase")
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("CloudStream Import System") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connect Supabase, choose installed providers, then import metadata and stream links.")
                    OutlinedTextField(value = projectUrl, onValueChange = { projectUrl = it }, label = { Text("Supabase Project URL") }, placeholder = { Text("https://your-project.supabase.co") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = anonKey, onValueChange = { anonKey = it }, label = { Text("Publishable / anon key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Supabase account email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(if (createAccount) "New account password" else "Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(enabled = !busy, onClick = { createAccount = !createAccount }) {
                            Text(if (createAccount) "I already have an account" else "Create Supabase account")
                        }
                        TextButton(enabled = !busy, onClick = {
                            if (!projectUrl.startsWith("https://") || anonKey.isBlank() || email.isBlank() || password.length < 6) {
                                status = "Enter HTTPS project URL, publishable key, email, and a password of at least 6 characters."
                                return@TextButton
                            }
                            busy = true
                            status = if (createAccount) "Creating Supabase account…" else "Signing in and checking database access…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val base = projectUrl.trim().trimEnd('/')
                                        val authPath = if (createAccount) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
                                        val auth = JSONObject().put("email", email.trim()).put("password", password)
                                        val response = importHttp("POST", "$base$authPath", anonKey.trim(), null, auth)
                                        val token = response.optString("access_token")
                                        val uid = response.optJSONObject("user")?.optString("id").orEmpty()
                                        if (token.isBlank() || uid.isBlank()) {
                                            if (createAccount) error("Account request accepted. Check your email for confirmation, then sign in.")
                                            error("Supabase did not return a session. Check credentials or email confirmation settings.")
                                        }
                                        importHttp("GET", "$base/rest/v1/media_items?select=id&limit=1", anonKey.trim(), token, null)
                                        Triple(token, uid, response.optString("refresh_token"))
                                    }
                                }
                                busy = false
                                result.onSuccess { (token, uid, newRefreshToken) ->
                                    accessToken = token
                                    userId = uid
                                    status = "Signed in. Database access verified."
                                    prefs.edit().putString("project_url", projectUrl.trim().trimEnd('/')).putString("anon_key", anonKey.trim()).putString("email", email.trim()).apply()
                                    ImportTokenVault.save(context, newRefreshToken)
                                    password = ""
                                }.onFailure { status = "Connection failed: ${it.message ?: "unknown error"}" }
                            }
                        }) { Text(if (createAccount) "Create account" else "Connect & sign in") }
                    }
                    Row {
                        OutlinedTextField(value = fromYear, onValueChange = { fromYear = it.filter(Char::isDigit).take(4) }, label = { Text("From year") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = toYear, onValueChange = { toYear = it.filter(Char::isDigit).take(4) }, label = { Text("To year") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Content categories")
                    categories.forEach { category ->
                        Row {
                            Checkbox(checked = category in selectedCategories.value, onCheckedChange = { checked ->
                                selectedCategories.value = if (checked) selectedCategories.value + category else selectedCategories.value - category
                            })
                            Text(category, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    Text("Installed providers (${providers.size})")
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(providers) { provider ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = provider.name in selected.value, onCheckedChange = { checked ->
                                    selected.value = if (checked) selected.value + provider.name else selected.value - provider.name
                                })
                                Text(provider.name, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    if (importedCount > 0 || sourceCount > 0) Text("Last run: $importedCount media items, $sourceCount stream links")
                }
            },
            confirmButton = {
                Row {
                    TextButton(enabled = !busy && accessToken.isNotBlank() && selected.value.isNotEmpty(), onClick = {
                        val start = fromYear.toIntOrNull()
                        val end = toYear.toIntOrNull()
                        if (start == null || end == null || start > end || selectedCategories.value.isEmpty()) {
                            status = "Check year range and choose at least one content category."
                            return@TextButton
                        }
                        busy = true
                        importedCount = 0
                        sourceCount = 0
                        status = "Import started…"
                        prefs.edit().putStringSet("providers", selected.value.toMutableSet()).apply()
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    importCloudStreamProviders(
                                        providers.filter { it.name in selected.value },
                                        projectUrl.trim().trimEnd('/'), anonKey.trim(), accessToken, userId,
                                        start, end, selectedCategories.value
                                    )
                                }
                            }
                            busy = false
                            result.onSuccess { (media, sources) ->
                                importedCount = media
                                sourceCount = sources
                                status = "Import finished. Added/updated $media media items and saved $sources unique stream links. Daily sync is scheduled."
                                CloudStreamImportWorker.enqueue(context)
                            }.onFailure { status = "Import stopped: ${it.message ?: "unknown error"}" }
                        }
                    }) { Text(if (busy) "Working…" else "Start Import") }
                    TextButton(enabled = !busy, onClick = { accessToken = ""; userId = ""; open = false }) { Text("Close") }
                }
            },
            dismissButton = {}
        )
    }
}

internal fun importHttp(method: String, url: String, anonKey: String, token: String?, body: JSONObject?): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("apikey", anonKey)
        connection.setRequestProperty("Accept", "application/json")
        if (method == "POST" && url.contains("/media_items")) connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation")
        if (method == "POST" && url.contains("/media_sources")) connection.setRequestProperty("Prefer", "resolution=ignore-duplicates,return=minimal")
        if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP $code: ${response.take(250)}")
        return if (response.isBlank()) JSONObject() else if (response.trimStart().startsWith("[")) JSONObject().put("data", JSONArray(response)) else JSONObject(response)
    } finally {
        connection.disconnect()
    }
}

internal fun normalizeImportTitle(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal fun mapImportType(type: TvType): String = when (type) {
    TvType.Anime, TvType.AnimeMovie -> "anime"
    TvType.Movie -> "movie"
    TvType.TvSeries, TvType.Cartoon, TvType.AsianDrama -> "series"
    TvType.OVA -> "ova"
    else -> "other"
}

internal fun importCloudStreamProviders(
    providers: List<MainAPI>,
    base: String,
    anonKey: String,
    token: String,
    ownerId: String,
    fromYear: Int,
    toYear: Int,
    categories: Set<String>
): Pair<Int, Int> {
    var mediaCount = 0
    var sourceCount = 0
    providers.forEach { provider ->
        provider.mainPage.take(20).forEach { page ->
            val home = runCatching {
                kotlinx.coroutines.runBlocking { provider.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages)) }
            }.getOrNull() ?: return@forEach
            home.items.flatMap { it.list }.take(100).forEach itemLoop@{ item ->
                try {
                    val loaded = kotlinx.coroutines.runBlocking { provider.load(item.url) } ?: return@itemLoop
                    val year = loaded.year
                    if (year != null && (year < fromYear || year > toYear)) return@itemLoop
                    val type = mapImportType(loaded.type)
                    if (type !in categories) return@itemLoop
                    val normalized = normalizeImportTitle(loaded.name)
                    if (normalized.isBlank()) return@itemLoop
                    val canonicalKey = "$normalized|$type|${year ?: 0}"
                    val mediaBody = JSONObject()
                        .put("owner_id", ownerId)
                        .put("canonical_key", canonicalKey)
                        .put("title", loaded.name)
                        .put("normalized_title", normalized)
                        .put("content_type", type)
                        .put("release_year", year ?: JSONObject.NULL)
                        .put("poster_url", loaded.posterUrl ?: JSONObject.NULL)
                        .put("description", loaded.plot ?: JSONObject.NULL)
                        .put("metadata", JSONObject().put("provider", provider.name).put("provider_item_url", loaded.url))
                    val mediaResponse = importHttp(
                        "POST",
                        "$base/rest/v1/media_items?on_conflict=canonical_key",
                        anonKey, token, mediaBody
                    )
                    val mediaId = mediaResponse.optJSONArray("data")?.optJSONObject(0)?.optString("id").orEmpty().ifBlank {
                        val query = "$base/rest/v1/media_items?select=id&canonical_key=eq.${java.net.URLEncoder.encode(canonicalKey, "UTF-8")}"
                        val rows = importHttp("GET", query, anonKey, token, null)
                        rows.optJSONArray("data")?.optJSONObject(0)?.optString("id").orEmpty()
                    }
                    if (mediaId.isBlank()) return@itemLoop
                    mediaCount++
                    val links = mutableListOf<ExtractorLink>()
                    runCatching {
                        kotlinx.coroutines.runBlocking {
                            provider.loadLinks(loaded.url, false, { }, { link -> links.add(link) })
                        }
                    }
                    links.distinctBy { it.url }.forEach { link ->
                        if (link.url.isBlank()) return@forEach
                        val sourceBody = JSONObject()
                            .put("media_item_id", mediaId)
                            .put("provider_name", provider.name)
                            .put("provider_item_id", item.url)
                            .put("page_url", loaded.url)
                            .put("direct_url", link.url)
                            .put("server_name", link.name.ifBlank { link.source })
                            .put("quality", link.quality.toString())
                            .put("metadata", JSONObject().put("referer", link.referer).put("stream_type", link.type.name))
                        runCatching {
                            importHttp(
                                "POST",
                                "$base/rest/v1/media_sources?on_conflict=media_item_id,direct_url",
                                anonKey, token, sourceBody
                            )
                            sourceCount++
                        }
                    }
                } catch (_: Throwable) {
                    // Continue importing other items/providers when one provider item fails.
                }
            }
        }
    }
    return mediaCount to sourceCount
}
