package com.luma.core.data.suggest

import com.luma.core.data.blocklist.BlocklistKey
import com.luma.core.data.db.dao.TrackBlocklistDao
import com.luma.core.data.db.dao.TrackDao
import com.luma.core.data.db.dao.TrackSkipEventDao
import com.luma.core.data.db.entity.TrackEntity
import com.luma.core.data.sync.TrackMatcher
import com.luma.data.ytmusic.YTMusicApiClient
import com.luma.data.ytmusic.model.TrackSummary
import com.luma.data.ytmusic.model.YTHomeItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.random.Random

/**
 * Songs suggested from what you listen to. [basedOn] names a song they grew from; null means no
 * history yet. [seedArtists] are the artists behind the seeds, for a "from X, Y and more" line.
 */
data class Suggestions(
    val basedOn: String?,
    val songs: List<TrackSummary>,
    val seedArtists: List<String> = emptyList(),
)

/**
 * LUMA: "Suggested for you", shared by Home and Search.
 *
 * Seeds are drawn from what you actually like: recent plays and your most-played
 * songs, weighted by how recently and how often you played them (liked songs count
 * extra), one per artist, never a song you keep skipping. Each seed's YouTube Music
 * song radio is fetched and the candidates are ranked by [SuggestionRanker]: a song
 * several of your seeds point at beats one only a single radio offered, and YouTube's
 * own order inside each radio counts. Songs you already play, blocked songs and
 * early-skipped songs are dropped, and no artist gets more than two slots. A little
 * randomness in the seeds and scores keeps each refresh fresh without going random.
 * With no listening history yet, your YouTube Music Quick picks.
 */
@Singleton
class ListeningSuggestions @Inject constructor(
    private val trackDao: TrackDao,
    private val ytMusic: YTMusicApiClient,
    private val blocklistDao: TrackBlocklistDao,
    private val skipEventDao: TrackSkipEventDao,
    private val matcher: TrackMatcher,
) {
    suspend fun load(limit: Int = 25): Suggestions {
        val recent = trackDao.getRecentlyPlayed(RECENT_POOL)
        val mostPlayed = runCatching { trackDao.getMostPlayed(TOP_POOL).first() }.getOrDefault(emptyList())
        val blocked = runCatching { blocklistDao.getAllKeys().toSet() }.getOrDefault(emptySet())
        val skipped = runCatching {
            skipEventDao.getEarlySkipBannedCanonicalKeys(
                minSkips = 2,
                sinceMs = System.currentTimeMillis() - SKIP_WINDOW_MS,
                maxPositionMs = EARLY_SKIP_MS,
            ).toSet()
        }.getOrDefault(emptySet())

        val seeds = SuggestionRanker.pickSeeds(
            recent = recent.map { it.toSeedCandidate() },
            mostPlayed = mostPlayed.map { it.toSeedCandidate() },
            excludedKeys = blocked + skipped,
            count = SEEDS,
        )
        if (seeds.isEmpty()) {
            val quickPicks = ytMusic.getHomeFeed()
                .flatMap { shelf -> shelf.items.filterIsInstance<YTHomeItem.Song>().map { it.track } }
                .distinctBy { it.videoId }
                .filter { keyOf(it) !in blocked }
                .shuffled()
                .take(limit)
            return Suggestions(basedOn = null, songs = quickPicks)
        }

        val radios = coroutineScope {
            seeds.map { seed ->
                async { runCatching { ytMusic.getSongRadio(seed.videoId) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        // Everything you already know is left out: the point is songs you haven't found yet.
        val known = recent + mostPlayed
        val knownIds = known.mapNotNull { it.youtubeId }.toSet()
        val knownKeys = known.map { BlocklistKey.fromStoredCanonicals(it.canonicalArtist, it.canonicalTitle) }.toSet()
        val songs = SuggestionRanker.rank(
            radios = seeds.zip(radios) { seed, radio -> seed.weight to radio },
            isExcluded = { t ->
                t.videoId in knownIds || keyOf(t).let { it in knownKeys || it in blocked || it in skipped }
            },
            limit = limit,
        )
        return Suggestions(
            basedOn = seeds.first().title,
            songs = songs,
            seedArtists = seeds.map { it.artist }.distinct(),
        )
    }

    private fun keyOf(t: TrackSummary) = BlocklistKey.of(t.artist, t.title, matcher)

    private fun TrackEntity.toSeedCandidate() = SeedCandidate(
        videoId = youtubeId.orEmpty(),
        title = title,
        artist = artist,
        key = BlocklistKey.fromStoredCanonicals(canonicalArtist, canonicalTitle),
        playCount = playCount,
        liked = lumaLikedAt != null,
    )

    private companion object {
        const val RECENT_POOL = 40
        const val TOP_POOL = 30
        const val SEEDS = 4
        const val SKIP_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        const val EARLY_SKIP_MS = 30_000L
    }
}

/** A song you played, as a possible seed. [key] is its canonical "artist|title". */
data class SeedCandidate(
    val videoId: String,
    val title: String,
    val artist: String,
    val key: String,
    val playCount: Int,
    val liked: Boolean,
)

/** A chosen seed and how much its radio counts. */
data class Seed(val videoId: String, val title: String, val artist: String, val weight: Double)

/** The pure part of [ListeningSuggestions]: which seeds to grow from and how to rank what they bring. */
object SuggestionRanker {

    /**
     * Scores each playable candidate (recency rank in [recent], play count, liked),
     * then draws [count] of them weighted by score, at most one per artist.
     */
    fun pickSeeds(
        recent: List<SeedCandidate>,
        mostPlayed: List<SeedCandidate>,
        excludedKeys: Set<String>,
        count: Int,
        random: Random = Random.Default,
    ): List<Seed> {
        val scores = LinkedHashMap<String, Pair<SeedCandidate, Double>>()
        fun add(c: SeedCandidate, recency: Double) {
            if (c.videoId.isBlank() || c.key in excludedKeys) return
            val base = recency + ln(1.0 + c.playCount) * 0.6 + if (c.liked) 0.5 else 0.0
            val prev = scores[c.videoId]
            if (prev == null || prev.second < base) scores[c.videoId] = c to base
        }
        recent.forEachIndexed { i, c -> add(c, 1.0 / (1.0 + i * 0.15)) }
        mostPlayed.forEach { c -> add(c, 0.0) }

        val pool = scores.values.filter { it.second > 0.0 }.toMutableList()
        val picked = mutableListOf<Seed>()
        val artists = mutableSetOf<String>()
        while (picked.size < count && pool.isNotEmpty()) {
            var r = random.nextDouble() * pool.sumOf { it.second }
            var idx = pool.indexOfFirst { r -= it.second; r <= 0.0 }
            if (idx < 0) idx = pool.lastIndex
            val (c, w) = pool.removeAt(idx)
            if (!artists.add(c.artist.lowercase())) continue
            picked += Seed(c.videoId, c.title, c.artist, w)
        }
        return picked
    }

    /**
     * Ranks radio candidates. Each appearance adds seedWeight / (1 + 0.08 * position)
     * (a radio's first row is its seed, so it is skipped), so songs that several seeds
     * lead to rise. Scores get a ±15% jitter, then the list keeps at most [perArtist]
     * songs per artist and never puts one artist twice in a row when it can avoid it.
     */
    fun rank(
        radios: List<Pair<Double, List<TrackSummary>>>,
        isExcluded: (TrackSummary) -> Boolean,
        limit: Int,
        perArtist: Int = 2,
        random: Random = Random.Default,
    ): List<TrackSummary> {
        val score = HashMap<String, Double>()
        val tracks = LinkedHashMap<String, TrackSummary>()
        for ((weight, radio) in radios) {
            radio.drop(1).forEachIndexed { pos, t ->
                if (t.artist.isBlank() || isExcluded(t)) return@forEachIndexed
                tracks.putIfAbsent(t.videoId, t)
                score[t.videoId] = (score[t.videoId] ?: 0.0) + weight / (1.0 + pos * 0.08)
            }
        }
        val ordered = tracks.values
            .map { it to score.getValue(it.videoId) * (0.85 + random.nextDouble() * 0.3) }
            .sortedByDescending { it.second }
            .map { it.first }

        val perArtistCount = HashMap<String, Int>()
        val capped = ordered.filter { t ->
            val n = perArtistCount.merge(t.artist.lowercase(), 1, Int::plus)!!
            n <= perArtist
        }.take(limit).toMutableList()

        val out = mutableListOf<TrackSummary>()
        while (capped.isNotEmpty()) {
            val last = out.lastOrNull()?.artist?.lowercase()
            // An artist holding more than half of what's left has to go now, or it ends up back to back.
            val (top, topCount) = capped.groupingBy { it.artist.lowercase() }.eachCount().maxBy { it.value }
            val i = if (topCount * 2 > capped.size && top != last) {
                capped.indexOfFirst { it.artist.lowercase() == top }
            } else {
                capped.indexOfFirst { it.artist.lowercase() != last }.coerceAtLeast(0)
            }
            out += capped.removeAt(i)
        }
        return out
    }
}
