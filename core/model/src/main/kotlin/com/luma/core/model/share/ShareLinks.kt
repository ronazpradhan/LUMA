package com.luma.core.model.share

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LUMA: where share links live. There is no share server: a link carries everything in its
 * fragment (after `#`), which browsers never send anywhere, and playluma.vercel.app is only a
 * static page that reads it. Nothing LUMA shares is stored by anyone.
 */
object ShareConfig {
    /** Every link the app builds. */
    const val BASE_URL = "https://playluma.vercel.app"

    /**
     * Share API hosts: none. Stash's share Worker (stashfm.app) is no longer contacted; this
     * stays empty so any leftover caller fails at once instead of reaching someone else's server.
     */
    val API_BASE_URLS: List<String> = emptyList()

    /** Hosts whose links open in LUMA. Keep in sync with the App Links filter in app/src/main/AndroidManifest.xml. */
    val HOSTS: Set<String> = setOf("playluma.vercel.app")

    /** Album-art CDNs a shared song's cover may point at. Anything else could log recipients' IPs. */
    val COVER_HOSTS: List<String> = listOf(
        "i.scdn.co", "mosaic.scdn.co",
        "i.ytimg.com", "lh3.googleusercontent.com", "yt3.googleusercontent.com", "yt3.ggpht.com",
        "lastfm.freetls.fastly.net", "lastfm-img.freetls.fastly.net",
        "static.qobuz.com", "c.saavncdn.com",
        "cdn-images.dzcdn.net", "e-cdns-images.dzcdn.net",
    )

    /** An https URL on [COVER_HOSTS], exactly or as a subdomain. Never throws. */
    fun isAllowedCover(url: String?): Boolean {
        val uri = runCatching { URI(url ?: return false) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && COVER_HOSTS.any { host == it || host.endsWith(".$it") }
    }
}

/** A playlist as a link carries it: short keys keep the link small. */
@Serializable
data class LinkPlaylist(
    @SerialName("v") val v: Int = 1,
    @SerialName("n") val name: String,
    @SerialName("by") val sharedBy: String? = null,
    @SerialName("t") val tracks: List<SharedTrack>,
    /**
     * The playlist's picture: one web cover (its own), or up to four song covers shown as a 2x2
     * mosaic. Only https links on [ShareConfig.COVER_HOSTS], so a link can't make its readers load
     * images from anywhere else; a photo from the phone's gallery can't travel without a server.
     */
    @SerialName("c") val covers: List<String> = emptyList(),
)

object ShareLinks {
    sealed interface Parsed {
        /** A whole playlist, decoded from the link itself. */
        data class Playlist(val playlist: LinkPlaylist) : Parsed
        data class Track(val track: SharedTrack) : Parsed
    }

    /** Songs a playlist link carries at most; longer playlists share their first ones. */
    const val MAX_LINK_TRACKS = 500

    /** Covers a playlist link carries: its own, or four songs' for a mosaic. */
    const val MAX_COVERS = 4

    /** Bytes a decoded playlist may take, so a hostile link can't exhaust memory. */
    private const val MAX_PLAYLIST_JSON = 2_000_000

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    /**
     * The link for a playlist: its name, who shared it and its songs, as raw-DEFLATE JSON in
     * base64url after `#`. Only what finds each song again is kept (no covers).
     */
    fun playlistUrl(name: String, sharedBy: String?, tracks: List<SharedTrack>, covers: List<String> = emptyList()): String {
        val payload = LinkPlaylist(
            name = name.trim().take(100).ifEmpty { "Playlist" },
            sharedBy = sharedBy?.trim()?.take(40)?.ifEmpty { null },
            tracks = tracks.take(MAX_LINK_TRACKS).map { it.copy(addedBy = null, artUrl = null) },
            covers = cleanCovers(covers),
        )
        val bytes = json.encodeToString(LinkPlaylist.serializer(), payload).toByteArray(Charsets.UTF_8)
        return "${ShareConfig.BASE_URL}/p#" + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(bytes))
    }

    /** The link for one song: its fields as a query after `#`. */
    fun trackUrl(t: SharedTrack): String = buildString {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        append(ShareConfig.BASE_URL).append("/s#t=").append(enc(t.title)).append("&a=").append(enc(t.artist))
        t.album?.let { append("&al=").append(enc(it)) }
        t.durationMs?.let { append("&d=").append(it) }
        t.isrc?.let { append("&isrc=").append(enc(it)) }
        t.spotifyId?.let { append("&sp=").append(enc(it)) }
        t.youtubeId?.let { append("&yt=").append(enc(it)) }
    }

    /** A LUMA share link (https or `luma://`, playlist or song, or the legacy `stash://track`), or null when it isn't one. */
    fun parse(link: String?): Parsed? {
        if (link == null) return null
        // Links arrive from any app or page, so nothing here may throw.
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val path = uri.path.orEmpty().trimEnd('/')
        return when {
            scheme == "https" && host in ShareConfig.HOSTS -> when (path) {
                "/p" -> uri.rawFragment?.let(::playlistFrom)
                "/s" -> runCatching { query(uri.rawFragment) }.getOrNull()?.let { q ->
                    trackFrom(q["t"], q["a"], q["al"], q["d"], q["isrc"], q["sp"], q["yt"])
                }
                else -> null
            }
            // The website's "Open in LUMA" button: the same links on LUMA's own scheme, which browsers hand
            // to the app with the fragment intact (an https link to the page they're on would just reload it).
            scheme == "luma" && host == "p" -> uri.rawFragment?.let(::playlistFrom)
            scheme == "luma" && host == "s" -> runCatching { query(uri.rawFragment) }.getOrNull()?.let { q ->
                trackFrom(q["t"], q["a"], q["al"], q["d"], q["isrc"], q["sp"], q["yt"])
            }
            scheme == "stash" && host == "track" -> runCatching { query(uri.rawQuery) }.getOrNull()?.let { q ->
                trackFrom(q["t"], q["a"], null, null, null, spotifyTrackId(q["s"]), q["y"])
            }
            else -> null
        }
    }

    private fun playlistFrom(fragment: String): Parsed? = runCatching {
        val bytes = inflate(Base64.getUrlDecoder().decode(fragment.trimEnd('=')), MAX_PLAYLIST_JSON) ?: return null
        val p = json.decodeFromString(LinkPlaylist.serializer(), bytes.toString(Charsets.UTF_8))
        if (p.v > 1) return null
        val tracks = p.tracks.take(MAX_LINK_TRACKS).mapNotNull { t ->
            (trackFrom(t.title, t.artist, t.album, t.durationMs?.toString(), t.isrc, t.spotifyId, t.youtubeId) as? Parsed.Track)?.track
        }
        val name = p.name.trim().take(100).ifEmpty { "Playlist" }
        Parsed.Playlist(
            LinkPlaylist(name = name, sharedBy = p.sharedBy?.trim()?.take(40)?.ifEmpty { null }, tracks = tracks, covers = cleanCovers(p.covers)),
        )
    }.getOrNull()

    /** At most [MAX_COVERS] distinct, short https covers on the allowed hosts. */
    private fun cleanCovers(covers: List<String>): List<String> =
        covers.map { it.trim() }.filter { it.length <= 500 && ShareConfig.isAllowedCover(it) }.distinct().take(MAX_COVERS)

    private fun trackFrom(t: String?, a: String?, al: String?, d: String?, isrc: String?, sp: String?, yt: String?): Parsed? {
        // Bounded: a hostile link can't push huge strings into the database.
        fun String?.field(max: Int) = this?.trim()?.take(max)?.ifEmpty { null }
        val title = t.field(500) ?: return null
        val artist = a.field(500) ?: return null
        return Parsed.Track(
            SharedTrack(title, artist, al.field(500), d?.toLongOrNull()?.takeIf { it > 0 },
                isrc.field(20), sp.field(40), yt.field(20)),
        )
    }

    private fun query(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { '=' in it }.associate { part ->
            val (k, v) = part.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun deflate(input: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(input)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    /** Raw inflate, refusing anything that grows past [limit] bytes (a zip bomb in a link). */
    private fun inflate(input: ByteArray, limit: Int): ByteArray? {
        val i = Inflater(true)
        return try {
            i.setInput(input)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!i.finished()) {
                val n = i.inflate(buf)
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > limit) return null
            }
            out.toByteArray()
        } finally {
            i.end()
        }
    }
}
