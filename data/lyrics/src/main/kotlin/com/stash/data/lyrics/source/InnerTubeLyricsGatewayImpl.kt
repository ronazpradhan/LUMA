package com.stash.data.lyrics.source

import com.stash.data.ytmusic.InnerTubeClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production binding of [InnerTubeLyricsGateway]. Wraps the existing
 * `:data:ytmusic` [InnerTubeClient] so [YtMusicLyricsSource] stays
 * unit-testable without spinning up the real client.
 *
 * Two-step discovery flow:
 * 1. [InnerTubeClient.next] -> walk the watch-next tab list, return the
 *    first `browseId` matching `MPLY*` (the lyrics page).
 * 2. [InnerTubeClient.browse] on that id -> project the
 *    `musicDescriptionShelfRenderer.description.runs[*].text` chain into
 *    a single joined plain-text string.
 *
 * Returns null on any missing path / shape drift rather than throwing so
 * the source-chain can move on to a later source (or give up gracefully).
 */
@Singleton
class InnerTubeLyricsGatewayImpl @Inject constructor(
    private val client: InnerTubeClient,
) : InnerTubeLyricsGateway {

    override suspend fun lyricsBrowseId(videoId: String): String? {
        val response = client.next(videoId) ?: return null
        val tabs: JsonArray = response["contents"]
            ?.jsonObject?.get("singleColumnMusicWatchNextResultsRenderer")
            ?.jsonObject?.get("tabbedRenderer")
            ?.jsonObject?.get("watchNextTabbedResultsRenderer")
            ?.jsonObject?.get("tabs")
            ?.jsonArray
            // Fallback shape: some response variants omit the
            // watchNextTabbedResultsRenderer wrapper and put `tabs` directly
            // on the tabbedRenderer node.
            ?: response["contents"]
                ?.jsonObject?.get("singleColumnMusicWatchNextResultsRenderer")
                ?.jsonObject?.get("tabbedRenderer")
                ?.jsonObject?.get("tabs")
                ?.jsonArray
            ?: return null

        for (tab in tabs) {
            val browseId = tab.jsonObject["tabRenderer"]?.jsonObject
                ?.get("endpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.content
                ?: continue
            if (browseId.startsWith("MPLY")) return browseId
        }
        return null
    }

    override suspend fun fetchLyricsByBrowseId(browseId: String): String? {
        val response = client.browse(browseId) ?: return null
        val sections: JsonArray = response["contents"]
            ?.jsonObject?.get("sectionListRenderer")
            ?.jsonObject?.get("contents")
            ?.jsonArray
            ?: return null

        for (section in sections) {
            val shelf = section.jsonObject["musicDescriptionShelfRenderer"]?.jsonObject
                ?: continue
            val runs: JsonArray = shelf["description"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?: continue
            val text = runs.joinToString(separator = "") { run ->
                run.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
            }
            if (text.isNotBlank()) return text
        }
        return null
    }
}
