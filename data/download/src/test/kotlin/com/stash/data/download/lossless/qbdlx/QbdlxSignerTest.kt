package com.stash.data.download.lossless.qbdlx

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QbdlxSignerTest {
    private val secret = "abb21364945c0583309667d13ca3d93a"

    @Test fun `getFileUrl signature matches HAR vector (fmt27)`() {
        val signer = QbdlxSigner()
        val sig = signer.signGetFileUrl(ts = 1782781652L, trackId = 2841459, formatId = 27, appSecret = secret)
        assertThat(sig).isEqualTo("013c10042c5e15ca5f1d85610bdd62ad")
    }

    @Test fun `getFileUrl signature matches HAR vector (fmt6)`() {
        val signer = QbdlxSigner()
        val sig = signer.signGetFileUrl(ts = 1782781565L, trackId = 3144087, formatId = 6, appSecret = secret)
        assertThat(sig).isEqualTo("ff083dedd464374d86affbb22daeae01")
    }

    @Test fun `lyricsUrl signature matches HAR vector`() {
        val signer = QbdlxSigner()
        val sig = signer.signLyricsUrl(ts = 1782781552L, trackId = 2841459, appSecret = secret)
        assertThat(sig).isEqualTo("e8149d392b9654ade72856fa3150d5a9")
    }

    @Test fun `signing with a different secret changes the signature`() {
        val signer = QbdlxSigner()
        val a = signer.signGetFileUrl(ts = 1782781652L, trackId = 2841459, formatId = 27, appSecret = secret)
        val b = signer.signGetFileUrl(ts = 1782781652L, trackId = 2841459, formatId = 27, appSecret = "e79f8b9be485692b0e5f9dd895826368")
        assertThat(a).isNotEqualTo(b)
    }

    @Test fun `ts comes from the injected clock`() {
        val signer = QbdlxSigner { 1782781652L }
        assertThat(signer.requestTs()).isEqualTo(1782781652L)
    }
}
