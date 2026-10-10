package com.stash.feature.search

import com.stash.core.model.TrackItem

/**
 * Derives the synthetic track id used by `PlayerRepositoryImpl.playFromStream`
 * (which is `item.videoId.hashCode().toLong()`). Centralised here so the
 * Search VM and SearchScreen compute the same key for the spinner-row
 * comparison. If `PlayerRepositoryImpl`'s derivation ever changes, update
 * this function in lockstep.
 */
internal fun TrackItem.syntheticId(): Long = videoId.hashCode().toLong()
