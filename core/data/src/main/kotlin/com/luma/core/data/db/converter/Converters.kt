package com.luma.core.data.db.converter

import androidx.room.TypeConverter
import com.luma.core.model.DownloadFailureType
import com.luma.core.model.DownloadStatus
import com.luma.core.model.MusicSource
import com.luma.core.model.PlaylistType
import com.luma.core.model.SyncState
import com.luma.core.model.SyncTrigger
import java.time.Instant

/**
 * Room [TypeConverter] collection for the LUMA database.
 *
 * Converts between domain types and their Room-storable representations:
 * - [Instant] <-> epoch-millis [Long]
 * - All enums <-> [String] (using `name` / `valueOf`)
 */
internal class Converters {

    // ── Instant ──────────────────────────────────────────────────────────

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // ── MusicSource ──────────────────────────────────────────────────────

    @TypeConverter
    fun musicSourceToString(value: MusicSource?): String? = value?.name

    @TypeConverter
    fun stringToMusicSource(value: String?): MusicSource? =
        value?.let { MusicSource.valueOf(it) }

    // ── PlaylistType ─────────────────────────────────────────────────────

    @TypeConverter
    fun playlistTypeToString(value: PlaylistType?): String? = value?.storedName

    @TypeConverter
    fun stringToPlaylistType(value: String?): PlaylistType? =
        value?.let { PlaylistType.fromStoredName(it) }

    // ── SyncState ────────────────────────────────────────────────────────

    @TypeConverter
    fun syncStateToString(value: SyncState?): String? = value?.name

    @TypeConverter
    fun stringToSyncState(value: String?): SyncState? =
        value?.let { SyncState.valueOf(it) }

    // ── SyncTrigger ──────────────────────────────────────────────────────

    @TypeConverter
    fun syncTriggerToString(value: SyncTrigger?): String? = value?.name

    @TypeConverter
    fun stringToSyncTrigger(value: String?): SyncTrigger? =
        value?.let { SyncTrigger.valueOf(it) }

    // ── DownloadStatus ───────────────────────────────────────────────────

    @TypeConverter
    fun downloadStatusToString(value: DownloadStatus?): String? = value?.name

    @TypeConverter
    fun stringToDownloadStatus(value: String?): DownloadStatus? =
        value?.let { DownloadStatus.valueOf(it) }

    // ── DownloadFailureType ──────────────────────────────────────────────

    @TypeConverter
    fun downloadFailureTypeToString(value: DownloadFailureType?): String? = value?.name

    @TypeConverter
    fun stringToDownloadFailureType(value: String?): DownloadFailureType? =
        value?.let { DownloadFailureType.valueOf(it) }
}
