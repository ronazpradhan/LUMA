package com.luma.core.media.streaming

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * LUMA: a least-recently-used evictor whose size limit can change while the app
 * runs (Media3's own LRU evictor fixes it at construction). Same policy: the
 * spans touched longest ago go first, until the cache fits under [maxBytes].
 * SimpleCache calls every callback under its own lock; [trim] takes that lock too.
 */
@OptIn(UnstableApi::class)
class AdjustableLruEvictor(initialMaxBytes: Long) : CacheEvictor {

    @Volatile
    var maxBytes: Long = initialMaxBytes

    private val spans = TreeSet<CacheSpan> { a, b ->
        if (a.lastTouchTimestamp != b.lastTouchTimestamp) {
            a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
        } else {
            a.compareTo(b)
        }
    }
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        currentSize += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /** Evicts down to the current [maxBytes] now (after the limit was lowered). */
    fun trim(cache: SimpleCache) = synchronized(cache) { evict(cache, 0) }

    private fun evict(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes && spans.isNotEmpty()) {
            cache.removeSpan(spans.first())
        }
    }
}

/**
 * LUMA: how much streamed music LUMA keeps on the phone (Settings › Library &
 * Storage › Streaming cache). Songs you replay from the cache start instantly
 * and cost no data; a bigger cache keeps more of them. The limit applies at
 * once: lowering it frees space straight away.
 */
@Singleton
@OptIn(UnstableApi::class)
class StreamCacheLimit @Inject constructor(
    @ApplicationContext context: Context,
    @StreamCache private val cache: Provider<SimpleCache>,
    private val evictor: AdjustableLruEvictor,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _limitMb = MutableStateFlow(readLimitMb(prefs))
    val limitMb: StateFlow<Long> = _limitMb.asStateFlow()

    suspend fun setLimitMb(mb: Long) {
        prefs.edit().putLong(KEY_LIMIT_MB, mb).apply()
        _limitMb.value = mb
        evictor.maxBytes = mb * MB
        withContext(Dispatchers.IO) { evictor.trim(cache.get()) }
    }

    /** Bytes the cache holds right now. */
    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { cache.get().cacheSpace }

    /** Empties the cache; downloads are not touched. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        val c = cache.get()
        c.keys.toList().forEach { runCatching { c.removeResource(it) } }
    }

    companion object {
        /** The sizes Settings offers, in MB. */
        val OPTIONS_MB = listOf(256L, 512L, 1024L, 2048L, 4096L)
        const val DEFAULT_MB = 512L
        private const val MB = 1024L * 1024L
        private const val PREFS = "stream_cache"
        private const val KEY_LIMIT_MB = "limit_mb"

        private fun readLimitMb(prefs: android.content.SharedPreferences) =
            prefs.getLong(KEY_LIMIT_MB, DEFAULT_MB)

        /** The saved limit in bytes, read when the cache is first built. */
        fun savedLimitBytes(context: Context): Long =
            readLimitMb(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) * MB
    }
}
