package com.luma.core.ui.components

import com.google.common.truth.Truth.assertThat
import com.luma.core.model.Track
import com.luma.core.model.share.ShareLinks
import com.luma.core.model.share.SharedTrack
import org.junit.Test

class ShareLinksTest {

    @Test
    fun `spotify uri converts to open-spotify link`() {
        assertThat(spotifyShareUrl("spotify:track:4uLU6hMCjMI75M1A2tKUQC"))
            .isEqualTo("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
    }

    @Test
    fun `full spotify url passes through`() {
        val url = "https://open.spotify.com/track/abc123"
        assertThat(spotifyShareUrl(url)).isEqualTo(url)
    }

    @Test
    fun `unknown or blank spotify identity yields null`() {
        assertThat(spotifyShareUrl(null)).isNull()
        assertThat(spotifyShareUrl("")).isNull()
        assertThat(spotifyShareUrl("spotify:album:xyz")).isNull()
    }

    private val track = Track(
        title = "Song & Dance", artist = "Aphex Twin", album = "Drukqs", durationMs = 125_000, isrc = "GBBPW0100025",
        spotifyUri = "spotify:track:abc", youtubeId = "xyz", albumArtUrl = "https://i.scdn.co/image/a",
    )
    @Test
    fun `luma link is built on the phone, on LUMA's site, and never carries the cover`() {
        assertThat(lumaSongLink(track)).isEqualTo(
            "https://playluma.vercel.app/s#t=Song+%26+Dance&a=Aphex+Twin&al=Drukqs&d=125000&isrc=GBBPW0100025&sp=abc&yt=xyz",
        )
        assertThat(lumaSongLink(track.copy(albumArtUrl = "file:///data/user/0/com.luma.app/files/art/7.jpg"))).doesNotContain("file")
        assertThat(ShareLinks.parse(lumaSongLink(track))).isEqualTo(
            ShareLinks.Parsed.Track(SharedTrack("Song & Dance", "Aphex Twin", "Drukqs", 125_000, "GBBPW0100025", "abc", "xyz")),
        )
    }

    @Test
    fun `youtube id builds a music watch link`() {
        assertThat(youtubeShareUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(youtubeShareUrl(null)).isNull()
        assertThat(youtubeShareUrl(" ")).isNull()
    }
}
