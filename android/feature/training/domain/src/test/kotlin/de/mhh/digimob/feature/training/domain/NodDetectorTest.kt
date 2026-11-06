package de.mhh.digimob.feature.training.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import org.junit.jupiter.api.Test

class NodDetectorTest {

    private val nod = NodDetector()

    /** Hoehen im Abstand von 0.1 s; das Ergebnis je Bild. */
    private fun feed(vararg heights: Double, start: Double = 0.0) =
        heights.mapIndexed { i, h -> nod.update(head(h), start + 0.1 * i) }

    @Test
    fun `runter und wieder hoch ist ein Nicken, genau beim Hochkommen`() {
        assertThat(feed(1.0, 1.0, 0.8, 0.8, 0.95, 1.0)).containsExactly(false, false, false, false, true, false)
    }

    @Test
    fun `nur ein bisschen runter ist kein Nicken`() {
        assertThat(feed(1.0, 0.9, 1.0, 0.9, 1.0).any { it }).isFalse()
    }

    @Test
    fun `langsames Absinken und Zurueckkommen ist kein Nicken`() {
        // 0.05 Schulterbreiten je halbe Sekunde: das Fenster sinkt mit
        val down = (0..20).map { 1.0 - 0.01 * it }
        val heights = down + down.reversed()
        assertThat(heights.mapIndexed { i, h -> nod.update(head(h), 0.1 * i) }.any { it }).isFalse()
    }

    @Test
    fun `zu lange unten ist kein Nicken`() {
        val heights = listOf(1.0, 0.8) + List(16) { 0.8 } + listOf(1.0)    // 1.7 s unten
        assertThat(heights.mapIndexed { i, h -> nod.update(head(h), 0.1 * i) }.any { it }).isFalse()
    }

    @Test
    fun `verdeckte Schultern oder keine Pose setzen zurueck`() {
        nod.update(head(1.0), 0.0)
        nod.update(head(0.8), 0.1)
        nod.update(head(0.9, visibility = 0.2), 0.2)
        assertThat(nod.update(head(1.0), 0.3)).isFalse()
        nod.update(head(0.8), 0.4)
        nod.update(null, 0.5)
        assertThat(nod.update(head(1.0), 0.6)).isFalse()
    }
}
