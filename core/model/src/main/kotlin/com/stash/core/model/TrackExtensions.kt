package com.stash.core.model

/**
 * v0.9.18: convenience accessor for "is this a lossless file?". Used
 * by the Now Playing "Find in FLAC" dialog to hide the upgrade option
 * when the current track is already FLAC.
 *
 * Case-insensitive comparison guards against any future tagger that
 * writes "FLAC" instead of "flac" — the file extension is canonical
 * lowercase per [com.stash.data.download.files.FileOrganizer], but
 * a defensive comparison is essentially free here.
 */
val Track.isFlac: Boolean
    get() = fileFormat.equals("flac", ignoreCase = true)

/**
 * v0.9.27: "this row should be greyed-out in library views" predicate.
 *
 * True when the track is synced metadata that has been checked against
 * the streaming proxy and came back unresolvable — i.e. neither
 * downloaded locally nor playable via streaming. The row stays visible
 * (so the user knows the track exists in their library) but renders at
 * 50% opacity with no tap-to-play.
 *
 * The three states surfaced by library views and how this predicate
 * classifies each:
 *
 * | Downloaded | Streamable | CheckedAt | Predicate | UI treatment        |
 * |------------|------------|-----------|-----------|---------------------|
 * | true       | -          | -         | false     | normal, tap = play  |
 * | false      | true       | -         | false     | normal, tap = stream|
 * | false      | false      | non-null  | **true**  | grey, no tap        |
 * | false      | false      | null      | false     | normal (rare leak)  |
 *
 * The fourth row ("not yet checked") shouldn't appear in library views
 * because the DAO predicate `(is_downloaded = 1 OR (:includeStreamable
 * AND is_streamable = 1))` filters it out, but if a row ever leaks
 * through we render it normally — better than grey-with-no-action.
 */
val Track.isUnavailableForDisplay: Boolean
    get() = !isDownloaded && !isStreamable && isStreamableCheckedAt != null
