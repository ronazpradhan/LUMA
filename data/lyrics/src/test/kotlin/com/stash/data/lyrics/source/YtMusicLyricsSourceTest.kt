package com.stash.data.lyrics.source

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YtMusicLyricsSourceTest {

    @Test fun `null youtubeVideoId returns null without InnerTube call`() = runTest {
        val innerTube = mockk<InnerTubeLyricsGateway>(relaxed = true)
        val source = YtMusicLyricsSource(innerTube)
        assertNull(source.resolve(query(videoId = null)))
    }

    @Test fun `videoId with no browseId returns null`() = runTest {
        val innerTube = mockk<InnerTubeLyricsGateway>()
        coEvery { innerTube.lyricsBrowseId(any()) } returns null
        val source = YtMusicLyricsSource(innerTube)
        assertNull(source.resolve(query(videoId = "abc")))
    }

    @Test fun `videoId with browseId returns plain text result`() = runTest {
        val innerTube = mockk<InnerTubeLyricsGateway>()
        coEvery { innerTube.lyricsBrowseId("abc") } returns "MPLYt_abc"
        coEvery { innerTube.fetchLyricsByBrowseId("MPLYt_abc") } returns "yesterday all my troubles"
        val source = YtMusicLyricsSource(innerTube)
        val result = source.resolve(query(videoId = "abc"))
        assertNotNull(result)
        assertEquals("innertube", result!!.sourceId)
        assertEquals("yesterday all my troubles", result.plainText)
        assertNull(result.syncedLrc)
        assertEquals(false, result.instrumental)
    }

    private fun query(videoId: String?) = LyricsQuery(
        trackId = 1L,
        title = "T", artist = "A", album = null, albumArtist = null,
        durationMs = null, youtubeVideoId = videoId,
    )
}
