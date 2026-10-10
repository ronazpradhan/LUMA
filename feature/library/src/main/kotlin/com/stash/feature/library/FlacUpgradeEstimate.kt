package com.stash.feature.library

import com.stash.core.model.Track
import java.util.Locale

/** FLAC-typical average bitrate for the pre-flight size estimate (spec §3). */
private const val FLAC_BYTES_PER_SECOND = 125_000L // ~1000 kbps

/** Lossless set duplicated from LibraryViewModel (same justification). */
private val LOSSLESS = setOf("flac", "alac", "wav", "ape", "tta", "wv", "aiff")

/** Tracks the batch can actually act on: downloaded and still lossy. */
fun eligibleForFlacUpgrade(tracks: List<Track>): List<Track> =
    tracks.filter { it.isDownloaded && it.fileFormat.lowercase() !in LOSSLESS }

/** Duration-based size estimate — honest for FLAC, which tracks playtime. */
fun estimateFlacBytes(tracks: List<Track>): Long =
    tracks.sumOf { (it.durationMs / 1000L) * FLAC_BYTES_PER_SECOND }

fun formatGb(bytes: Long): String =
    String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
