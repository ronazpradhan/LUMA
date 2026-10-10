package com.stash.core.model

/** Per-row state in the batch FLAC-upgrade queue (spec 2026-07-22 §3). Room persists the name. */
enum class FlacUpgradeStatus { PENDING, DONE, NO_MATCH, FAILED }
