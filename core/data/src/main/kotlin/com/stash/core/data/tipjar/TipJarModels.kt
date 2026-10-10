package com.stash.core.data.tipjar

import kotlinx.serialization.Serializable

/**
 * v0.9.13: Tip-jar feature models — simplified.
 *
 * The tip jar surfaces a rotating list of supporters on Home and routes
 * a tap straight to ko-fi. There's no in-app goal, no progress bar, no
 * cycle math — those concerns live on ko-fi.com/rawnald where the
 * actual donation tracking happens.
 *
 * Wire format (`supporters.json` at a public URL):
 *
 * ```json
 * {
 *   "supporters": [
 *     {"name": "Cedric", "amountUsd": 10, "message": "Just downloaded..."},
 *     {"name": "Slowcab", "amountUsd": 5, "message": "Amazing work!..."}
 *   ]
 * }
 * ```
 *
 * `amountUsd` is rendered alongside the name (right-aligned mono) and
 * is the only number the user sees in-app — no totals, no goals.
 */
@Serializable
data class Supporter(
    val name: String,
    val amountUsd: Int,
    val message: String = "",
)

@Serializable
data class TipJarPayload(
    val supporters: List<Supporter> = emptyList(),
)

/**
 * Domain object the UI consumes. Just the supporter list — the pill
 * crossfades through them on Home.
 */
data class TipJarState(
    val supporters: List<Supporter>,
) {
    companion object {
        val EMPTY = TipJarState(supporters = emptyList())
    }
}

fun TipJarPayload.toState(): TipJarState = TipJarState(supporters = supporters)
