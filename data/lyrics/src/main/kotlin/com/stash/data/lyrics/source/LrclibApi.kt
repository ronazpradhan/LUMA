package com.stash.data.lyrics.source

import kotlinx.serialization.Serializable

@Serializable
internal data class LrclibGetResponse(
    val id: Long,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    // LRCLIB returns duration as a JSON Number with a decimal (e.g. 265.0). Modelled as
    // Double — strict kotlinx.serialization throws if we try to deserialize a float into Int,
    // which would silently miss every successful fetch (runCatching swallows the throw → null
    // → caller treats it as a 404 even though the body was a real hit).
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
