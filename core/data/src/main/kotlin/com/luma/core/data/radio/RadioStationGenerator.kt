package com.luma.core.data.radio

import com.luma.core.data.lastfm.LastFmApiClient
import com.luma.core.data.lastfm.LastFmSimilarArtist
import com.luma.core.model.MusicSource
import com.luma.core.model.Track
import com.luma.core.model.YouTubeVideoId
import com.luma.data.ytmusic.YTMusicApiClient
import com.luma.data.ytmusic.model.TrackSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and extends a balanced radio station: Last.fm chooses the taste graph,
 * YouTube supplies real playable videoIds. Stateless across stations — all live
 * state lives in the returned [RadioSession]. Plain `@Inject` (both clients are
 * injectable); no Hilt module needed.
 */
@Singleton
class RadioStationGenerator @Inject constructor(
    private val lastFm: LastFmApiClient,
    private val yt: YTMusicApiClient,
) {
    /** Start a station: returns the session + the first playable batch. */
    suspend fun start(seed: RadioSeed): Pair<RadioSession, List<Track>> = when (seed) {
        is RadioSeed.Artist -> startArtist(seed)
        is RadioSeed.Song -> startSong(seed)
    }

    private suspend fun startArtist(seed: RadioSeed.Artist): Pair<RadioSession, List<Track>> {
        // Fetch the FULL ranked neighbor list once; use the first NEIGHBOR_POOL now
        // and page the rest during widening (see widen()). Fetching the same top-N
        // again later would only re-return already-played neighbors.
        val allNeighbors = lastFm.getSimilarArtists(seed.name, limit = NEIGHBOR_MAX)
            .getOrDefault(emptyList())
        val neighbors = allNeighbors.take(NEIGHBOR_POOL)
        // Resolve seed (via known browseId) + each neighbor's popular list, in parallel.
        val artistTracks: Map<String, List<TrackSummary>> = coroutineScope {
            val seedJob = async { seed.name to seedPopular(seed) }
            val neighborJobs = neighbors.map { n ->
                async { n.name to resolvePopular(n.name) }
            }
            (listOf(seedJob) + neighborJobs).awaitAll().toMap()
        }
        val pool = buildArtistPool(seed.name, neighbors, artistTracks)
        val session = RadioSession(seed, RadioInterleaver.order(pool).toMutableList(), mutableSetOf())
        session.remainingNeighbors.addAll(allNeighbors.drop(NEIGHBOR_POOL))
        session.neighborsLoaded = true
        return session to drainBatch(session, FIRST_BATCH)
    }

    private suspend fun seedPopular(seed: RadioSeed.Artist): List<TrackSummary> {
        val browseId = seed.ytBrowseId ?: yt.resolveArtist(seed.name)?.id ?: return emptyList()
        return runCatching { yt.getArtist(browseId).popular }.getOrDefault(emptyList())
    }

    private suspend fun resolvePopular(name: String): List<TrackSummary> {
        val id = yt.resolveArtist(name)?.id ?: return emptyList()
        return runCatching { yt.getArtist(id).popular }.getOrDefault(emptyList())
    }

    /**
     * Seed weight tuned so that WITHIN the current pool the seed is the single
     * most frequent artist — weight = SEED_SHARE/(1-SEED_SHARE) * W (W = total
     * neighbor weight) makes the seed's weight ~1/3 of the total, so the
     * interleaver front-loads the seed's tracks across the opening. The seed has
     * a finite catalog (~[TRACKS_PER_ARTIST] popular tracks), so as the station
     * widens to more neighbors the seed naturally recedes — "recognizable open,
     * always drifting outward." A literal steady 1/3-forever is impossible
     * without repeating the seed's few tracks, which the no-repeat set forbids.
     */
    private fun buildArtistPool(
        seedName: String,
        neighbors: List<LastFmSimilarArtist>,
        tracks: Map<String, List<TrackSummary>>,
    ): List<RadioCandidate> {
        val neighborWeightSum = neighbors.sumOf { it.match.coerceAtLeast(0.05f).toDouble() }.toFloat()
        val seedWeight = if (neighborWeightSum <= 0f) 1f
            else (SEED_SHARE / (1f - SEED_SHARE)) * neighborWeightSum
        val out = ArrayList<RadioCandidate>()
        tracks[seedName]?.take(TRACKS_PER_ARTIST)?.forEach {
            out += RadioCandidate(seedName, it.title, it.videoId, seedWeight, it.thumbnailUrl)
        }
        for (n in neighbors) {
            val w = n.match.coerceAtLeast(0.05f)
            tracks[n.name]?.take(TRACKS_PER_ARTIST)?.forEach {
                out += RadioCandidate(n.name, it.title, it.videoId, w, it.thumbnailUrl)
            }
        }
        return out
    }

    /** Walk the ordered pool from the cursor, emit up to [size] fresh tracks,
     *  resolving any candidate that still lacks a videoId. Skips dupes + misses.
     *  Does NOT set `exhausted` — widen() owns that (the pool may still widen). */
    internal suspend fun drainBatch(session: RadioSession, size: Int): List<Track> {
        val out = ArrayList<Track>(size)
        while (out.size < size && session.cursor < session.ordered.size) {
            val cand = session.ordered[session.cursor++]
            val id = cand.identity()
            if (id in session.played) continue
            // Candidates with a known videoId (artist-radio, seeded song) keep
            // their own thumbnail; those without one resolve via YT Music search,
            // which also yields the song's square album cover — so no-thumbnail
            // candidates get a crisp cover instead of the low-res video frame.
            val existingId = cand.videoId?.takeIf { it.isNotBlank() }
            val videoId: String
            val resolvedArt: String?
            if (existingId != null) {
                videoId = existingId
                resolvedArt = null
            } else {
                val match = yt.searchCanonicalMatch(cand.artist, cand.title) ?: continue
                videoId = match.videoId
                resolvedArt = match.thumbnailUrl
            }
            // Two different candidates (e.g. a seed-artist top track and a similar
            // track) can resolve to the SAME videoId — guard on the resolved id too
            // so the same audio never plays twice in one station.
            val vidKey = "vid:$videoId"
            if (vidKey in session.played) continue
            session.played += id
            session.played += vidKey
            out += cand.toTrack(videoId, resolvedArt)
        }
        return out
    }

    /** The next playable batch. Widens the pool when it nears exhaustion. */
    suspend fun nextBatch(session: RadioSession): List<Track> {
        if (session.cursor >= session.ordered.size - WIDEN_THRESHOLD && !session.exhausted) {
            widen(session)
        }
        return drainBatch(session, GROW_BATCH)
    }

    /** Append more candidates by PAGING the unused neighbor list (never
     *  re-fetching the same top-N, which would only re-return already-played
     *  neighbors). Song radio lazy-loads the neighbor list here on first widen;
     *  artist radio filled it at start. Sets [exhausted] only when no neighbors
     *  remain to try. */
    private suspend fun widen(session: RadioSession) {
        if (session.ytRadio) {
            // Keep following YouTube Music: the radio of the station's last track.
            // Nothing new there hands the station over to Last.fm below.
            val lastId = session.ordered.lastOrNull()?.videoId
            val queued = session.ordered.mapTo(HashSet()) { it.identity() }
            val fresh = lastId?.let { ytRadioCandidates(it) }.orEmpty()
                .filter { it.identity() !in queued && it.identity() !in session.played }
            if (fresh.isNotEmpty()) {
                session.ordered += fresh
                return
            }
            session.ytRadio = false
        }
        if (!session.neighborsLoaded) {
            val seedArtistName = when (val s = session.seed) {
                is RadioSeed.Artist -> s.name
                is RadioSeed.Song -> s.artist
            }
            session.remainingNeighbors.addAll(
                lastFm.getSimilarArtists(seedArtistName, limit = NEIGHBOR_MAX).getOrDefault(emptyList()),
            )
            session.neighborsLoaded = true
        }
        if (session.remainingNeighbors.isEmpty()) { session.exhausted = true; return }
        // Page the next slice of never-used neighbors.
        val next = ArrayList<LastFmSimilarArtist>()
        while (next.size < NEIGHBOR_POOL && session.remainingNeighbors.isNotEmpty()) {
            next += session.remainingNeighbors.removeFirst()
        }
        val fresh = ArrayList<RadioCandidate>()
        for (n in next) {
            val id = yt.resolveArtist(n.name)?.id ?: continue
            val popular = runCatching { yt.getArtist(id).popular }.getOrDefault(emptyList())
            popular.take(TRACKS_PER_ARTIST).forEach {
                val cand = RadioCandidate(n.name, it.title, it.videoId, n.match.coerceAtLeast(0.05f), it.thumbnailUrl)
                if (cand.identity() !in session.played) fresh += cand
            }
        }
        if (fresh.isEmpty()) {
            // This slice yielded nothing new; only truly done when no neighbors remain.
            if (session.remainingNeighbors.isEmpty()) session.exhausted = true
            return
        }
        session.ordered += RadioInterleaver.order(fresh)
    }

    private fun RadioCandidate.toTrack(videoId: String, resolvedArt: String? = null) = Track(
        id = videoId.hashCode().toLong(),
        title = title,
        artist = artist,
        durationMs = 0L,
        // Album art, best-first: the candidate's own InnerTube cover (artist-radio
        // popular tracks carry a square 544px one), else the square album cover
        // pulled from the YT Music search that resolved the videoId (song-radio /
        // Last.fm neighbours), else the low-res video frame as a last resort. This
        // is the single chokepoint every emitted radio Track flows through.
        albumArtUrl = thumbnailUrl ?: resolvedArt ?: ytThumbnail(videoId),
        youtubeId = videoId,
        source = MusicSource.YOUTUBE,
        isStreamable = true,
    )

    /** YouTube thumbnail for a videoId, used only when a track has no richer
     *  cover. `maxresdefault` (1280x720) is the full 16:9 frame with NO black
     *  letterbox bars (unlike `hqdefault`/`sddefault`, which pad 16:9 into a 4:3
     *  frame), so it center-crops cleanly to a square without black space —
     *  same reason this used to ask for `mqdefault`, but four times the width.
     *  Videos without one are walked back down by `ArtFallbackInterceptor`.
     *  The player upgrades this to the square Qobuz cover once qbdlx resolves.
     *  Null for an id without the YouTube video-id shape: the id came from a
     *  server response and is only built into a URL when it has it. */
    private fun ytThumbnail(videoId: String): String? =
        videoId.takeIf(YouTubeVideoId::isValid)?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }

    private suspend fun startSong(seed: RadioSeed.Song): Pair<RadioSession, List<Track>> {
        ytSongRadio(seed)?.let { return it }
        val similar = lastFm.getSimilarTracks(seed.artist, seed.title, limit = NEIGHBOR_LIMIT)
            .getOrDefault(emptyList())
            .take(SONG_SIMILAR_POOL)
        val seedTop = lastFm.getArtistTopTracks(seed.artist, limit = TRACKS_PER_ARTIST)
            .getOrDefault(emptyList())

        val similarWeightSum = similar.sumOf { it.match.coerceAtLeast(0.05f).toDouble() }.toFloat()
        val seedWeight = if (similarWeightSum <= 0f) 1f
            else (SEED_SHARE / (1f - SEED_SHARE)) * similarWeightSum

        val pool = ArrayList<RadioCandidate>()
        // Seed share: the seeded track first, then the seed artist's top tracks.
        pool += RadioCandidate(seed.artist, seed.title, seed.ytVideoId, seedWeight)
        seedTop.filterNot { it.title.equals(seed.title, ignoreCase = true) }
            .forEach { pool += RadioCandidate(seed.artist, it.title, null, seedWeight) }
        // Neighbors: each similar track, weighted by match; artist keyed for interleave.
        for (s in similar) {
            pool += RadioCandidate(s.artist, s.title, null, s.match.coerceAtLeast(0.05f))
        }
        val session = RadioSession(seed, RadioInterleaver.order(pool).toMutableList(), mutableSetOf())
        return session to drainBatch(session, FIRST_BATCH)
    }

    /**
     * The station YouTube Music itself plays after [seed] (its "Up next" radio),
     * in YouTube's order. The seed leads the pool under its own title and artist,
     * so the player can find it in the first batch and keep it playing. Null when
     * YouTube has no radio for it; Last.fm builds the station then.
     */
    private suspend fun ytSongRadio(seed: RadioSeed.Song): Pair<RadioSession, List<Track>>? {
        val videoId = seed.ytVideoId?.takeIf { it.isNotBlank() }
            ?: runCatching { yt.searchCanonicalVideoId(seed.artist, seed.title) }.getOrNull()
            ?: return null
        val radio = ytRadioCandidates(videoId)
        if (radio.isEmpty()) return null
        val pool = listOf(RadioCandidate(seed.artist, seed.title, videoId, 1f)) + radio
        val session = RadioSession(seed, pool.toMutableList(), mutableSetOf())
        session.ytRadio = true
        val batch = drainBatch(session, FIRST_BATCH)
        return if (batch.size > 1) session to batch else null
    }

    /** YouTube Music's song radio for [videoId], minus the seed itself. Low-res video frames are dropped for the cover fallback. */
    private suspend fun ytRadioCandidates(videoId: String): List<RadioCandidate> =
        runCatching { yt.getSongRadio(videoId) }.getOrDefault(emptyList())
            .filter { it.videoId != videoId && it.artist.isNotBlank() }
            .map { RadioCandidate(it.artist, it.title, it.videoId, 1f, it.thumbnailUrl?.takeUnless { url -> "/vi/" in url }) }

    companion object {
        const val SEED_SHARE = 1f / 3f
        const val NEIGHBOR_LIMIT = 30    // Last.fm page size for song getSimilarTracks
        const val NEIGHBOR_MAX = 100     // full artist-neighbor list (paged by widen)
        const val NEIGHBOR_POOL = 12     // neighbors consumed per pool/widen slice
        const val TRACKS_PER_ARTIST = 6
        const val FIRST_BATCH = 12
        const val GROW_BATCH = 12
        const val WIDEN_THRESHOLD = 6
        const val SONG_SIMILAR_POOL = 40
    }
}
