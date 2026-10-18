package de.mhh.digimob.feature.training.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import de.mhh.digimob.core.domain.exercise.Bar
import de.mhh.digimob.feature.training.presentation.bar.BarFeedback
import de.mhh.digimob.feature.training.presentation.bar.BarUi
import org.junit.jupiter.api.Test

class BarFeedbackTest {

    private val clock = FakeClock()
    private val feedback = BarFeedback(clock)

    private fun bar(fill: Double?, back: Boolean = false, count: Int = 5) = Bar(fill, back, count, countOnly = false)

    @Test
    fun `Bestaetigung nach einer gezaehlten Wiederholung, fuer 0,6 s`() {
        assertThat(feedback.update(bar(0.5)).confirm).isFalse()
        clock.now = 1.0
        assertThat(feedback.update(bar(0.1, count = 4)).confirm).isTrue()    // Zahl geaendert
        clock.now = 1.0 + BarFeedback.CONFIRM_SECONDS + 0.01
        assertThat(feedback.update(bar(0.1, count = 4)).confirm).isFalse()
    }

    @Test
    fun `die erste Zahl ist keine gezaehlte Wiederholung`() {
        assertThat(feedback.update(bar(0.0, count = 5)).confirm).isFalse()
    }

    @Test
    fun `ohne Messwert bleibt der letzte Stand 0,4 s stehen, dann leer`() {
        feedback.update(bar(1.1, back = true))
        clock.now = BarFeedback.HOLD_SECONDS / 2
        feedback.update(bar(null)).let {
            assertThat(it.fill).isEqualTo(1.1)
            assertThat(it.back).isTrue()
        }
        clock.now = BarFeedback.HOLD_SECONDS + 0.01
        feedback.update(bar(null)).let {
            assertThat(it.fill).isNull()
            assertThat(it.back).isFalse()
        }
    }

    @Test
    fun `ohne je einen Messwert sofort leer`() {
        assertThat(feedback.update(bar(null)).fill).isNull()
    }

    @Test
    fun `hoechster Punkt erst sichtbar, wenn der Arm wieder tiefer ist`() {
        val peaks = listOf(0.2, 0.7, 0.5, 0.3).map { feedback.update(bar(it, back = it >= 1.0)).peak }
        assertThat(peaks).isEqualTo(listOf(null, null, 0.7, 0.7))
    }

    @Test
    fun `gezaehlt misst den hoechsten Punkt neu, ohne Messwert keine Linie`() {
        listOf(0.2, 0.7, 0.5).forEach { feedback.update(bar(it)) }
        feedback.update(bar(0.2, count = 4))
        assertThat(feedback.update(bar(0.1, count = 4)).peak).isNull()
        assertThat(feedback.update(bar(null, count = 4)).peak).isNull()
    }

    @Test
    fun `Zurueck geschafft (Pfeil weg) misst den hoechsten Punkt neu`() {
        listOf(0.2, 1.2).forEach { feedback.update(bar(it, back = true)) }
        assertThat(feedback.update(bar(0.3, back = false)).peak).isNull()
    }

    @Test
    fun `kein Balken (Einfuehrung, Prima) ist ganz leer`() {
        assertThat(feedback.update(null)).isEqualTo(BarUi())
    }
}
