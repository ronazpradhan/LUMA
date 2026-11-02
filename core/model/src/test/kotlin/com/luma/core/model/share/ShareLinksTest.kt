package com.luma.core.model.share

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import java.util.zip.Deflater
import org.junit.Test

class ShareLinksTest {
    private val base = ShareConfig.BASE_URL

    @Test fun `links live on LUMA's site and no share server is contacted`() {
        assertThat(base).isEqualTo("https://playluma.vercel.app")
        assertThat(ShareConfig.API_BASE_URLS).isEmpty()
        assertThat(ShareConfig.HOSTS).containsExactly("playluma.vercel.app")
    }

    @Test fun `a playlist travels whole inside its link and comes back the same`() {
        val songs = listOf(
            SharedTrack("Song & Dance", "Aphex Twin", "Drukqs", 125_000, "GBBPW0100025", "abc", "xyz12345678"),
            SharedTrack("Teardrop", "Massive Attack", youtubeId = "u7K72X4eo_s"),
        )
        val url = ShareLinks.playlistUrl("Night drive", "Ronaz", songs)
        assertThat(url).startsWith("$base/p#")
        assertThat(url).doesNotContain("Night") // compressed, not readable text
        val parsed = ShareLinks.parse(url) as ShareLinks.Parsed.Playlist
        assertThat(parsed.playlist.name).isEqualTo("Night drive")
        assertThat(parsed.playlist.sharedBy).isEqualTo("Ronaz")
        assertThat(parsed.playlist.tracks).isEqualTo(songs)
    }

    @Test fun `covers and Listen Together fields stay out of playlist links, and long playlists are capped`() {
        val many = (1..600).map { SharedTrack("Song $it", "Artist", artUrl = "https://i.ytimg.com/vi/x/a.jpg", addedBy = "m1") }
        val parsed = ShareLinks.parse(ShareLinks.playlistUrl("Big", null, many)) as ShareLinks.Parsed.Playlist
        assertThat(parsed.playlist.tracks).hasSize(ShareLinks.MAX_LINK_TRACKS)
        assertThat(parsed.playlist.tracks.all { it.artUrl == null && it.addedBy == null }).isTrue()
    }

    @Test fun `a hundred-song playlist makes a link that fits in a message`() {
        val songs = (1..100).map { SharedTrack("Some Song Title $it", "Some Artist $it", "Album $it", 200_000L + it, youtubeId = "abcdefgh${it % 10}xy") }
        assertThat(ShareLinks.playlistUrl("Mine", null, songs).length).isLessThan(8_000)
    }

    @Test fun `a playlist's picture travels as up to four covers, from known image hosts only`() {
        val covers = listOf(
            "https://i.scdn.co/image/a", "https://evil.example/track.gif", "https://i.scdn.co/image/a",
            "http://i.ytimg.com/vi/x/hq.jpg", "https://i.ytimg.com/vi/b/hq.jpg", "https://lh3.googleusercontent.com/c",
            "https://c.saavncdn.com/d.jpg", "https://i.scdn.co/image/e",
        )
        val p = (ShareLinks.parse(ShareLinks.playlistUrl("Pics", null, listOf(SharedTrack("T", "A")), covers)) as ShareLinks.Parsed.Playlist).playlist
        assertThat(p.covers).containsExactly(
            "https://i.scdn.co/image/a", "https://i.ytimg.com/vi/b/hq.jpg", "https://lh3.googleusercontent.com/c", "https://c.saavncdn.com/d.jpg",
        ).inOrder()
    }

    @Test fun `a six-song playlist like a real one makes a short link`() {
        val songs = listOf(
            SharedTrack("ADRIAN PRADHAN - NASAMJHA (UNPLUGGED)", "Adrian Pradhan", durationMs = 245_000, youtubeId = "abcdefghij1"),
            SharedTrack("Jham Jham", "Kta Haru", durationMs = 210_000, youtubeId = "abcdefghij2"),
            SharedTrack("Sajilai Birsideu", "Prateek Khadka", durationMs = 233_000, youtubeId = "abcdefghij3"),
            SharedTrack("Nasamjha", "Adrian Pradhan", durationMs = 251_000, youtubeId = "abcdefghij4"),
            SharedTrack("Khaseka Tara", "Albatross", durationMs = 300_000, youtubeId = "abcdefghij5"),
            SharedTrack("Bagaicha", "Kta Haru", durationMs = 222_000, youtubeId = "abcdefghij6"),
        )
        assertThat(ShareLinks.playlistUrl("nepali songs", null, songs).length).isLessThan(400)
    }

    @Test fun `a song link carries every field after the hash and parses back`() {
        val t = SharedTrack("Song & Dance", "Aphex Twin", "Drukqs", 125_000, "GBBPW0100025", "abc", "xyz")
        val url = ShareLinks.trackUrl(t)
        assertThat(url).startsWith("$base/s#t=Song+%26+Dance&a=Aphex+Twin")
        assertThat(ShareLinks.parse(url)).isEqualTo(ShareLinks.Parsed.Track(t))
    }

    @Test fun `the website's Open in LUMA links carry the same playlist or song`() {
        val songs = listOf(SharedTrack("Teardrop", "Massive Attack", youtubeId = "u7K72X4eo_s"))
        val web = ShareLinks.playlistUrl("Mine", null, songs)
        val app = "luma://p#" + web.substringAfter('#')
        assertThat(ShareLinks.parse(app)).isEqualTo(ShareLinks.parse(web))
        val song = ShareLinks.trackUrl(songs.single())
        assertThat(ShareLinks.parse("luma://s#" + song.substringAfter('#'))).isEqualTo(ShareLinks.Parsed.Track(songs.single()))
        assertThat(ShareLinks.parse("luma://other#t=a&a=b")).isNull()
    }

    @Test fun `legacy luma track links still parse`() {
        val legacy = "stash://track?t=Avril+14th&a=Aphex+Twin&s=https%3A%2F%2Fopen.spotify.com%2Ftrack%2Fabc&y=xyz"
        assertThat(ShareLinks.parse(legacy)).isEqualTo(
            ShareLinks.Parsed.Track(SharedTrack("Avril 14th", "Aphex Twin", spotifyId = "abc", youtubeId = "xyz")),
        )
    }

    @Test fun `Stash's old links, other sites and broken links are not LUMA links`() {
        assertThat(ShareLinks.parse("https://stashfm.app/m/Kx7Qa2pL")).isNull()
        assertThat(ShareLinks.parse("https://stash-share.rawnaldclark.workers.dev/t?t=a&a=b")).isNull()
        assertThat(ShareLinks.parse("https://evil.example/p#abc")).isNull()
        assertThat(ShareLinks.parse("$base/p")).isNull()
        assertThat(ShareLinks.parse("$base/p#not-really-a-playlist")).isNull()
        assertThat(ShareLinks.parse("$base/s#a=OnlyArtist")).isNull()
        assertThat(ShareLinks.parse("$base/s#t=%zz&a=A")).isNull()
        assertThat(ShareLinks.parse("$base/other#t=a&a=b")).isNull()
        assertThat(ShareLinks.parse(null)).isNull()
        assertThat(ShareLinks.parse("not a url")).isNull()
    }

    @Test fun `hostile links are bounded and a decompression bomb is refused`() {
        val long = "x".repeat(5000)
        val t = (ShareLinks.parse("$base/s#t=$long&a=A") as ShareLinks.Parsed.Track).track
        assertThat(t.title).hasLength(500)
        // 50 MB of spaces compresses to a few kB: it must be refused, not inflated.
        val bomb = ByteArray(50_000_000) { ' '.code.toByte() }
        val d = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bomb); finish() }
        val buf = ByteArray(1_000_000)
        val n = d.deflate(buf)
        val link = "$base/p#" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf.copyOf(n))
        assertThat(ShareLinks.parse(link)).isNull()
    }

    @Test fun `a playlist from a newer LUMA asks for an update instead of half-opening`() {
        val raw = """{"v":2,"n":"Future","t":[]}""".toByteArray()
        val d = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(raw); finish() }
        val buf = ByteArray(1024)
        val n = d.deflate(buf)
        assertThat(ShareLinks.parse("$base/p#" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf.copyOf(n)))).isNull()
    }

    @Test fun `cover allowlist matches hosts exactly or as a subdomain, https only`() {
        assertThat(ShareConfig.isAllowedCover("https://i.scdn.co/image/a")).isTrue()
        assertThat(ShareConfig.isAllowedCover("https://x.i.ytimg.com/vi/b.jpg")).isTrue()
        assertThat(ShareConfig.isAllowedCover("https://evil.example/a.jpg")).isFalse()
        assertThat(ShareConfig.isAllowedCover("https://i.scdn.co.evil.example/a")).isFalse()
        assertThat(ShareConfig.isAllowedCover("http://i.scdn.co/a")).isFalse()
        assertThat(ShareConfig.isAllowedCover(null)).isFalse()
    }
}
