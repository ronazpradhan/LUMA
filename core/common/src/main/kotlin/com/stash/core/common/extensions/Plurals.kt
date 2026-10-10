package com.stash.core.common.extensions

/**
 * Formats a count with its noun, pluralising only when it isn't 1 —
 * `1 track`, `2 tracks`, `0 tracks`.
 *
 * Exists because "${count} tracks" was hand-written at a dozen call sites and
 * every one of them rendered "1 tracks" on a single-item playlist. One helper
 * means the next call site can't reintroduce it.
 *
 * English-only by construction. When i18n lands this should become a
 * `plurals` resource — most languages don't split on "== 1", and several have
 * more than two forms.
 */
fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"
