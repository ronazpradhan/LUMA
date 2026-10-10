package com.stash.data.lyrics.source

/**
 * Adapter interface around the InnerTube client. Lets [YtMusicLyricsSource]
 * unit-test cleanly without instantiating the real client; the production
 * binding wraps the existing `:data:ytmusic` client in [InnerTubeLyricsGatewayImpl].
 *
 * Two-step lyrics discovery flow:
 * 1. POST `/youtubei/v1/next` with `videoId` -> the watch-next response includes
 *    a tab whose `browseEndpoint.browseId` is the `MPLY...` lyrics page.
 * 2. POST `/youtubei/v1/browse` with that `MPLY...` id -> the response contains
 *    a `musicDescriptionShelfRenderer` whose `description.runs[*].text` is the
 *    plain-text lyrics body.
 *
 * InnerTube does not provide synced (LRC) lyrics; this source is plain-text only.
 */
interface InnerTubeLyricsGateway {
    /** Returns the lyrics browseId (`MPLY...`) from the `next` response, or null if not surfaced. */
    suspend fun lyricsBrowseId(videoId: String): String?

    /** Fetches plain-text lyrics for the given browseId, or null on miss / error. */
    suspend fun fetchLyricsByBrowseId(browseId: String): String?
}
