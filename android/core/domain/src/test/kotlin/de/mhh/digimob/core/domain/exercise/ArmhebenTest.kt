package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ArmhebenTest {

    private val down = arms(20.0)
    private val up = arms(160.0)

    private fun count(frames: List<Pose?>, preset: Preset = Preset.VOLL) =
        Abduktion(target = null, preset = preset).run(frames).counter

    private val none = mapOf(Side.LEFT to 0, Side.RIGHT to 0)
    private val one = mapOf(Side.LEFT to 1, Side.RIGHT to 1)

    @Test
    fun `unten - oben - unten zaehlt eine Wiederholung`() {
        assertThat(count(down * 6 + up * 6 + down * 6)).isEqualTo(one)
    }

    @Test
    fun `Huefte unter dem Bildrand und unsichtbar zaehlt trotzdem (Tablet, nah vor der Kamera)`() {
        val hipOut = { angle: Double -> armPose(arm = angle to angle, hipInPicture = false) }
        assertThat(count(hipOut(20.0) * 6 + hipOut(160.0) * 6 + hipOut(20.0) * 6)).isEqualTo(one)
    }

    @Test
    fun `oben am Anfang zaehlt nicht (erst die Startposition)`() {
        assertThat(count(up * 6 + down * 6)).isEqualTo(none)
    }

    @Test
    fun `Ziel nicht erreicht zaehlt nicht`() {
        assertThat(count(down * 6 + arms(140.0) * 6 + down * 6)).isEqualTo(none)
    }

    @Test
    fun `Einfach zaehlt das, was bei Voll das Ziel verfehlt`() {
        assertThat(count(down * 6 + arms(140.0) * 6 + down * 6, Preset.EINFACH)).isEqualTo(one)
    }

    @Test
    fun `zwischen unten und Ziel bleibt der Zustand (Hysterese)`() {
        // 140 liegt ueber unten (135) und unter dem Ziel (150): das Zurueck ist noch nicht unten
        assertThat(count(down * 6 + up * 6 + arms(140.0) * 6)).isEqualTo(none)
    }

    @Test
    fun `unsichtbarer Arm zaehlt nicht`() {
        assertThat(count(down * 6 + arms(160.0, visibility = 0.3) * 6 + down * 6)).isEqualTo(none)
    }

    @Test
    fun `nur der unsichtbare Arm faellt heraus, der andere zaehlt`() {
        val leftHidden = armPose(arm = 160.0 to 160.0, leftVisibility = 0.3)
        assertThat(count(down * 6 + leftHidden * 6 + down * 6))
            .isEqualTo(mapOf(Side.LEFT to 0, Side.RIGHT to 1))
    }

    @Test
    fun `Sichtbarkeit genau an der Grenze zaehlt`() {
        assertThat(count(down * 6 + arms(160.0, visibility = 0.6) * 6 + down * 6)).isEqualTo(one)
    }

    @Test
    fun `Punkte ohne Sichtbarkeit gelten als sichtbar`() {
        assertThat(count(down * 6 + arms(160.0, visibility = null) * 6 + down * 6)).isEqualTo(one)
    }

    @Test
    fun `einzelner Ausreisser faellt durch den Median heraus`() {
        assertThat(count(down * 6 + arms(170.0) * 1 + down * 6)).isEqualTo(none)
    }

    @Test
    fun `fehlende Pose unterbricht die Wiederholung nicht`() {
        assertThat(count(down * 6 + up * 6 + List(10) { null } + down * 6)).isEqualTo(one)
    }

    @Test
    fun `Balken ueber dem Ziel mit Pfeil zurueck`() {
        val bar = Abduktion(target = 5, preset = Preset.VOLL).run(down * 6 + up * 6).progress().getValue(Side.LEFT)
        assertThat(bar.fill!!).isGreaterThan(1.0)
        assertThat(bar.back).isTrue()
        assertThat(bar.count).isEqualTo(5)
    }

    @Test
    fun `unten = Ziel minus 15 Grad`() {
        assertThat(Abduktion(target = 5, preset = Preset.EINFACH).minAngle).isEqualTo(105.0)
    }
}
