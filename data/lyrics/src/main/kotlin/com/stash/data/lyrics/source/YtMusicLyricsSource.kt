package com.stash.data.lyrics.source

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secondary lyrics source. Falls back to YouTube Music's InnerTube `next` ->
 * `browse` flow when LRCLIB misses (often the case for non-Western catalog).
 *
 * Returns plain-text only — InnerTube does not expose synced (LRC) lyrics.
 * A null [LyricsQuery.youtubeVideoId] short-circuits to null without any
 * HTTP call, so this source is a no-op for tracks whose Stash row lacks
 * a resolved YouTube videoId.
 */
@Singleton
class YtMusicLyricsSource @Inject constructor(
    private val innerTube: InnerTubeLyricsGateway,
) : LyricsSource {

    override val id: String = "innertube"
    override val displayName: String = "YouTube Music"

    override suspend fun resolve(query: LyricsQuery): LyricsResult? {
        val videoId = query.youtubeVideoId ?: return null
        val browseId = runCatching { innerTube.lyricsBrowseId(videoId) }.getOrNull() ?: return null
        val text = runCatching { innerTube.fetchLyricsByBrowseId(browseId) }.getOrNull() ?: return null
        if (text.isBlank()) return null
        return LyricsResult(
            sourceId = id,
            plainText = text.trim(),
            syncedLrc = null,
            instrumental = false,
            language = null,
            sourceLyricsId = null,
        )
    }
}
