package com.stash.core.data.mix

import kotlin.math.sqrt

/**
 * v0.9.16: Per-user tag-affinity vector + cosine similarity. Replaces
 * the previous "count of overlapping top-tags" heuristic in
 * [com.stash.core.data.mix.MixGenerator] with proper weighted vector
 * math.
 *
 * Conceptually a TF-IDF-flavored bag-of-tags model:
 *   user_vector = L2_normalize( Σ_{plays} weight_p × tag_vector_p )
 *   score(track) = cosine(track.tag_vector, user_vector)
 *
 * Per-play `weight` is computed by the caller from recency (decay),
 * play count (log-norm), completion-rate, and loved-boost. The math
 * here treats it as an opaque scalar.
 */
object UserTagAffinity {

    data class PlayWithTags(val weight: Float, val tags: Map<String, Float>)

    /**
     * L2-normalized weighted sum of per-play tag vectors. Empty input
     * returns an empty vector (matches "no signal yet" semantics —
     * callers handle by skipping the cosine term entirely).
     */
    fun compute(plays: List<PlayWithTags>): Map<String, Float> {
        if (plays.isEmpty()) return emptyMap()
        val acc = HashMap<String, Float>()
        for (p in plays) {
            for ((tag, w) in p.tags) {
                acc.merge(tag, p.weight * w) { a, b -> a + b }
            }
        }
        return l2Normalize(acc)
    }

    /**
     * Standard cosine similarity over two sparse vectors. Returns 0
     * when either vector is empty or has zero magnitude.
     */
    fun cosine(a: Map<String, Float>, b: Map<String, Float>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        // Iterate the smaller map for the dot product — minor perf
        // win on lopsided inputs (tiny user vector vs. large track tags).
        val (smaller, larger) = if (a.size < b.size) a to b else b to a
        var dot = 0f
        for ((k, v) in smaller) {
            dot += v * (larger[k] ?: 0f)
        }
        val magA = magnitude(a)
        val magB = magnitude(b)
        if (magA == 0f || magB == 0f) return 0f
        return dot / (magA * magB)
    }

    private fun l2Normalize(v: Map<String, Float>): Map<String, Float> {
        val mag = magnitude(v)
        if (mag == 0f) return v
        return v.mapValues { it.value / mag }
    }

    private fun magnitude(v: Map<String, Float>): Float =
        sqrt(v.values.sumOf { it.toDouble() * it.toDouble() }).toFloat()
}
