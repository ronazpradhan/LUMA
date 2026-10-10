package com.stash.core.data.listenbrainz

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Dedicated DataStore for the ListenBrainz connection — mirrors `lastfm_session`. */
private val Context.listenBrainzDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "listenbrainz",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Stores the user's ListenBrainz token and, just as importantly, **when they
 * connected**.
 *
 * Kept in its own DataStore with a corruption handler, following
 * [com.stash.core.data.lastfm.LastFmSessionPreference] — the Last.fm session key is
 * an equivalent-sensitivity credential stored the same way, so this follows the
 * codebase's existing posture rather than inventing a second one.
 *
 * ## Why the connect timestamp is stored at all
 *
 * It is the history-flood guard. Stash keeps every listening event it has ever
 * recorded, so without a cutoff the first drain after connecting would submit the
 * user's entire back catalogue of plays as one flood — thousands of listens they
 * never asked to import, all at once. [connectedAtMs] becomes the sink's
 * `listeningSinceMs`, so a fresh connection means "from now on".
 *
 * Deliberate backfill stays a separate, explicit action, the way Last.fm's
 * cold-start import already is.
 */
@Singleton
class ListenBrainzPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("user_token")
    private val connectedAtKey = longPreferencesKey("connected_at_ms")
    private val nowPlayingKey = stringPreferencesKey("now_playing_enabled")

    val token: Flow<String?> = context.listenBrainzDataStore.data.map { prefs ->
        prefs[tokenKey]?.takeIf { it.isNotBlank() }
    }

    /** Null when never connected. */
    val connectedAtMs: Flow<Long?> = context.listenBrainzDataStore.data.map { prefs ->
        prefs[connectedAtKey]
    }

    /** Users who want scrobbles but not a live "listening now" status. */
    val nowPlayingEnabled: Flow<Boolean> = context.listenBrainzDataStore.data.map { prefs ->
        prefs[nowPlayingKey] != "false"
    }

    suspend fun tokenNow(): String? = runCatching { token.first() }.getOrNull()

    suspend fun connectedAtNow(): Long? = runCatching { connectedAtMs.first() }.getOrNull()

    suspend fun nowPlayingEnabledNow(): Boolean =
        runCatching { nowPlayingEnabled.first() }.getOrDefault(true)

    /**
     * Saves a validated token. [nowMs] becomes the cutoff, so only listens from
     * this moment forward are ever submitted.
     *
     * Reconnecting with the same token does NOT move the cutoff forward — that
     * would silently drop anything queued during an outage the user was
     * reconnecting to fix.
     */
    suspend fun connect(token: String, nowMs: Long) {
        context.listenBrainzDataStore.edit { prefs ->
            val existing = prefs[tokenKey]
            prefs[tokenKey] = token.trim()
            if (existing != token.trim() || prefs[connectedAtKey] == null) {
                prefs[connectedAtKey] = nowMs
            }
        }
    }

    suspend fun setNowPlayingEnabled(enabled: Boolean) {
        context.listenBrainzDataStore.edit { prefs ->
            prefs[nowPlayingKey] = enabled.toString()
        }
    }

    /** Disconnects. The caller should also clear this target's submission rows. */
    suspend fun clear() {
        context.listenBrainzDataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(connectedAtKey)
        }
    }
}
