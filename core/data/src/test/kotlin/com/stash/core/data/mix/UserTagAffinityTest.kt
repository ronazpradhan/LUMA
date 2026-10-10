package com.stash.core.data.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class UserTagAffinityTest {

    @Test
    fun `affinity vector is L2-normalized weighted sum of recent plays' tag vectors`() {
        // Two recent plays, one with tags [indie:100, dream pop:50] and
        // weight 1.0, the other with [indie:80, shoegaze:40] and weight 0.5.
        // Expected: indie dominant, dream pop and shoegaze present.
        val plays = listOf(
            UserTagAffinity.PlayWithTags(
                weight = 1.0f,
                tags = mapOf("indie" to 100f, "dream pop" to 50f),
            ),
            UserTagAffinity.PlayWithTags(
                weight = 0.5f,
                tags = mapOf("indie" to 80f, "shoegaze" to 40f),
            ),
        )
        val v = UserTagAffinity.compute(plays)

        // L2-normalized → magnitude ≈ 1.0
        val mag = sqrt(v.values.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        assertEquals(1.0f, mag, 0.01f)

        // Indie weight should dominate (highest combined weight × tag weight).
        assertTrue("indie max", v.maxByOrNull { it.value }?.key == "indie")

        // All three tags present.
        assertTrue(v.keys == setOf("indie", "dream pop", "shoegaze"))
    }

    @Test
    fun `cosine similarity returns 1 0 for identical vectors and 0 0 for orthogonal`() {
        val a = mapOf("a" to 1f, "b" to 0f)
        val b = mapOf("a" to 1f, "b" to 0f)
        val c = mapOf("a" to 0f, "b" to 1f)

        assertEquals(1.0f, UserTagAffinity.cosine(a, b), 0.001f)
        assertEquals(0.0f, UserTagAffinity.cosine(a, c), 0.001f)
    }

    @Test
    fun `cosine returns 0 for empty vectors`() {
        assertEquals(0f, UserTagAffinity.cosine(emptyMap(), mapOf("x" to 1f)), 0.001f)
        assertEquals(0f, UserTagAffinity.cosine(mapOf("x" to 1f), emptyMap()), 0.001f)
        assertEquals(0f, UserTagAffinity.cosine(emptyMap(), emptyMap()), 0.001f)
    }

    @Test
    fun `compute returns empty for empty plays`() {
        assertEquals(emptyMap<String, Float>(), UserTagAffinity.compute(emptyList()))
    }
}
