package com.stash.core.media.preview

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import javax.inject.Inject

/**
 * Maps a [DataSpec] to a stable cache key.
 *
 * The search-tab path sets the key two ways depending on entry point:
 *   - Preview: `MediaItem.Builder.setCustomCacheKey(...)` → propagates
 *     through `BaseMediaSource` to `DataSpec.key`.
 *   - Download: `DataSpec.Builder.setKey(...)` directly.
 *
 * Both surface as `spec.key` here. Falls back to the URI itself for
 * any non-search-tab caller of the same SimpleCache.
 *
 * Earlier versions read a `?trackKey=` URI param as a fallback. That
 * was load-bearing because preview was *appending* the key to the URL
 * (which corrupted Qobuz/YouTube signed URLs, so every byte 403'd —
 * the v0.9.12 launch-day "preview hangs" symptom). Both writers now
 * use the proper API; the URI is left untouched.
 *
 * Note: media3 renamed `DataSpec.customCacheKey` (older docs) to
 * `DataSpec.key` in current versions; the field is `public final
 * String key;` and the builder is `Builder.setKey(String)`.
 */
class TrackKeyCacheKeyFactory @Inject constructor() : CacheKeyFactory {
    override fun buildCacheKey(spec: DataSpec): String =
        spec.key ?: spec.uri.toString()
}
