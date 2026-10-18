package de.mhh.digimob.core.domain.exercise

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExerciseMathTest {

    @Test
    fun `rechter Winkel`() {
        assertThat(ExerciseMath.angle(Point(1.0, 0.0), Point(0.0, 0.0), Point(0.0, 1.0))).isCloseTo(90.0, 1e-9)
    }

    @Test
    fun `zwei Punkte aufeinander ist ein Fehler, kein NaN`() {
        assertFailure { ExerciseMath.angle(Point(0.3, 0.3), Point(0.3, 0.3), Point(0.0, 1.0)) }
            .isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `fast gerade Linie bleibt endlich (cos wird auf -1 begrenzt)`() {
        // Rundung drueckt cos bei manchen Punkten knapp unter -1, acos waere dann NaN
        for (i in 1..1000) {
            val d = i * 0.0013
            val angle = ExerciseMath.angle(Point(0.1 + d, 0.3 * d), Point(0.1, 0.0), Point(0.1 - 0.7 * d, -0.21 * d))
            assertThat(angle).isBetween(179.99, 180.0)
        }
    }

    @Test
    fun `Fortschritt von Start bis Ziel, begrenzt`() {
        assertThat(ExerciseMath.progress(85.0, 20.0, 150.0)).isEqualTo(0.5)
        assertThat(ExerciseMath.progress(10.0, 20.0, 150.0)).isEqualTo(0.0)
        assertThat(ExerciseMath.progress(200.0, 20.0, 150.0)).isEqualTo(1.0)
        assertThat(ExerciseMath.progress(200.0, 20.0, 150.0, upper = OVER_TARGET)).isEqualTo(OVER_TARGET)
    }

    @Test
    fun `Ziel unter dem Start zaehlt rueckwaerts (Bizepscurls)`() {
        assertThat(ExerciseMath.progress(135.0, 160.0, 110.0)).isEqualTo(0.5)
    }

    @Test
    fun `ohne Bereich (Start = Ziel) leer statt Division durch null`() {
        assertThat(ExerciseMath.progress(5.0, 3.0, 3.0)).isEqualTo(0.0)
    }

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY])
    fun `kein Messwert gibt einen leeren Balken`(value: Double) {
        assertThat(ExerciseMath.progress(value, 20.0, 150.0, OVER_TARGET)).isEqualTo(0.0)
    }
}
