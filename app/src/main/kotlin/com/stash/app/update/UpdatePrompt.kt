package com.stash.app.update

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stash.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** A LUMA release on GitHub newer than the installed one. */
data class LumaUpdate(val version: String, val notes: String, val downloadUrl: String)

/**
 * LUMA: checks the LUMA GitHub releases once per app start and offers a newer
 * version in a popup. "Later" silences that version; the next one asks again.
 */
@HiltViewModel
class UpdatePromptViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("luma_update", Context.MODE_PRIVATE)
    private val _update = MutableStateFlow<LumaUpdate?>(null)
    val update: StateFlow<LumaUpdate?> = _update.asStateFlow()

    init {
        viewModelScope.launch {
            val latest = runCatching { fetchLatest() }.getOrNull() ?: return@launch
            if (isNewer(latest.version, BuildConfig.VERSION_NAME) &&
                prefs.getString(KEY_DISMISSED, null) != latest.version
            ) {
                _update.value = latest
            }
        }
    }

    /** "Later": don't ask about this version again. */
    fun later() {
        _update.value?.let { prefs.edit().putString(KEY_DISMISSED, it.version).apply() }
        _update.value = null
    }

    fun close() {
        _update.value = null
    }

    private suspend fun fetchLatest(): LumaUpdate? = withContext(Dispatchers.IO) {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "LUMA/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        if (conn.responseCode != 200) return@withContext null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val version = json.optString("tag_name").removePrefix("v").takeIf { it.isNotBlank() } ?: return@withContext null
        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
        LumaUpdate(
            version = version,
            notes = plainNotes(json.optString("body")),
            downloadUrl = apk ?: json.optString("html_url"),
        )
    }

    internal companion object {
        const val LATEST_URL = "https://api.github.com/repos/ronazpradhan/LUMA/releases/latest"
        const val KEY_DISMISSED = "dismissed_version"

        /** True when [candidate] (e.g. "1.2.0") is a later version than [installed]. */
        fun isNewer(candidate: String, installed: String): Boolean {
            fun parts(v: String) = v.split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(installed)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** The release notes' bullet points as plain text, at most a few lines. */
        fun plainNotes(markdown: String): String =
            markdown.lines()
                .map { it.trim() }
                .filter { it.startsWith("- ") }
                .map { "• " + it.removePrefix("- ").replace("**", "").replace(Regex("""\[(.*?)]\(.*?\)"""), "$1") }
                .take(6)
                .joinToString("\n")
    }
}

/** LUMA: the "Update available" popup, shown over the app when GitHub has a newer release. */
@Composable
fun UpdatePrompt(viewModel: UpdatePromptViewModel = hiltViewModel()) {
    val update by viewModel.update.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val u = update ?: return
    AlertDialog(
        onDismissRequest = viewModel::close,
        title = { Text("Update available") },
        text = {
            Column {
                Text("LUMA ${u.version} is out. You have ${BuildConfig.VERSION_NAME}.", style = MaterialTheme.typography.bodyMedium)
                if (u.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(u.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { uriHandler.openUri(u.downloadUrl) }
                viewModel.close()
            }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = viewModel::later) { Text("Later") } },
    )
}

