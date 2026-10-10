// LoudnessStore.kt
package com.stash.core.media.equalizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Persistence wrapper for [LoudnessState].
 *
 * Stores the entire state as a single kotlinx.serialization JSON string in
 * one Preferences DataStore key. This guarantees atomic writes — a partial
 * write during process death cannot leave loudness in a half-written state.
 *
 * Missing key → default (enabled = true, targetLufs = -14). Corrupted JSON → default.
 * Loudness normalization is a "just works" feature by design, so the
 * default-on behavior is intentional and differs from [EqStore] (which
 * defaults off for legacy bug-prevention reasons).
 */
@Singleton
class LoudnessStore @Inject constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  suspend fun read(): LoudnessState {
    val raw = dataStore.data.first()[KEY] ?: return LoudnessState()
    return try {
      json.decodeFromString(LoudnessState.serializer(), raw)
    } catch (_: SerializationException) {
      LoudnessState()
    } catch (_: IllegalArgumentException) {
      LoudnessState()
    }
  }

  suspend fun write(state: LoudnessState) {
    dataStore.edit { it[KEY] = json.encodeToString(LoudnessState.serializer(), state) }
  }

  /** Test-only: write a raw string to simulate corruption. */
  internal suspend fun writeRaw(raw: String) {
    dataStore.edit { it[KEY] = raw }
  }

  companion object {
    private val KEY = stringPreferencesKey("loudness_state_v1_json")
  }
}
