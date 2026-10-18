package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class HoldTimeTest {

    @Test
    fun `bestaetigt erst nach der Haltezeit, genau an der Grenze schon`() {
        val hold = HoldTime<String>(0.25)
        assertThat(hold.confirm("up", 0.0)).isNull()
        assertThat(hold.confirm("up", 0.125)).isNull()
        assertThat(hold.confirm("up", 0.25)).isEqualTo("up")
    }

    @Test
    fun `zweimal dieselbe Zeit bestaetigt nicht`() {
        val hold = HoldTime<String>(0.25)
        hold.confirm("up", 1.0)
        assertThat(hold.confirm("up", 1.0)).isNull()
    }

    @Test
    fun `neue Phase startet die Haltezeit neu`() {
        val hold = HoldTime<String>(0.25)
        hold.confirm("up", 0.0)
        hold.confirm("down", 0.2)
        assertThat(hold.confirm("down", 0.25)).isNull()
        assertThat(hold.confirm("down", 0.5)).isEqualTo("down")
    }

    @Test
    fun `Luecke ohne Phase (null) unterbricht`() {
        val hold = HoldTime<String>(0.25)
        hold.confirm("up", 0.0)
        hold.confirm(null, 0.1)
        assertThat(hold.confirm("up", 0.3)).isNull()
    }

    @Test
    fun `reset verwirft die laufende Haltezeit`() {
        val hold = HoldTime<String>(0.25)
        hold.confirm("up", 0.0)
        hold.reset()
        assertThat(hold.confirm("up", 0.3)).isNull()
    }

    @Test
    fun `rueckwaerts laufende Uhr bestaetigt nicht`() {
        val hold = HoldTime<String>(0.25)
        hold.confirm("up", 10.0)
        assertThat(hold.confirm("up", 5.0)).isNull()
    }
}
