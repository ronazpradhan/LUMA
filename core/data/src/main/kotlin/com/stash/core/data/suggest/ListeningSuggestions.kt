package com.stash.core.data.suggest

import com.stash.core.data.db.dao.TrackDao
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.TrackSummary
import com.stash.data.ytmusic.model.YTHomeItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Songs suggested from what you listen to. [basedOn] names a song they grew from; null means no history yet. */
data class Suggestions(val basedOn: String?, val songs: List<TrackSummary>)

/**
 * LUMA: "Suggested for you", shared by Home and Search. Seeds are a few songs
 * picked at random from your recent plays; their YouTube Music song radios
 * are interleaved, minus what you just played, then shuffled — so every
 * refresh brings a different list. With no listening history yet, your
 * YouTube Music Quick picks, shuffled.
 */
@Singleton
class ListeningSuggestions @Inject constructor(
    private val trackDao: TrackDao,
    private val ytMusic: YTMusicApiClient,
) {
    suspend fun load(limit: Int = 20): Suggestions {
        val recent = trackDao.getRecentlyPlayed(SEED_POOL)
        val seeds = recent.filter { !it.youtubeId.isNullOrBlank() }.distinctBy { it.youtubeId }.shuffled().take(SEEDS)
        if (seeds.isEmpty()) {
            val quickPicks = ytMusic.getHomeFeed()
                .flatMap { shelf -> shelf.items.filterIsInstance<YTHomeItem.Song>().map { it.track } }
                .distinctBy { it.videoId }
                .shuffled()
                .take(limit)
            return Suggestions(basedOn = null, songs = quickPicks)
        }
        val radios = coroutineScope {
            seeds.map { seed ->
                async { runCatching { ytMusic.getSongRadio(seed.youtubeId!!) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        val played = recent.mapNotNull { it.youtubeId }.toSet()
        val mixed = buildList {
            val iters = radios.map { r -> r.filter { it.videoId !in played && it.artist.isNotBlank() }.shuffled().iterator() }
            while (iters.any { it.hasNext() }) iters.forEach { if (it.hasNext()) add(it.next()) }
        }.distinctBy { it.videoId }.take(limit)
        return Suggestions(basedOn = seeds.first().title, songs = mixed)
    }

    private companion object {
        const val SEED_POOL = 15
        const val SEEDS = 3
    }
}
