package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.jupiter.api.Test

class BizepscurlsTest {

    private val stretched = armPose(arm = 10.0 to 10.0, elbow = 175.0 to 175.0)
    private val bent = armPose(arm = 10.0 to 10.0, elbow = 40.0 to 40.0)
    private val none = mapOf(Side.LEFT to 0, Side.RIGHT to 0)
    private val one = mapOf(Side.LEFT to 1, Side.RIGHT to 1)

    private fun curls(frames: List<Pose?>, preset: Preset = Preset.VOLL) =
        Bizepscurls(target = null, preset = preset).run(frames)

    @Test
    fun `kurzes Beugen zaehlt nicht, langes schon`() {
        assertThat(curls(stretched * 15 + bent * 3 + stretched * 15).counter).isEqualTo(none)
        assertThat(curls(stretched * 15 + bent * 15 + stretched * 15).counter).isEqualTo(one)
    }

    @Test
    fun `Huefte unter dem Bildrand und unsichtbar zaehlt trotzdem`() {
        val out = armPose(arm = 10.0 to 10.0, elbow = 175.0 to 175.0, hipInPicture = false)
        val outBent = armPose(arm = 10.0 to 10.0, elbow = 40.0 to 40.0, hipInPicture = false)
        assertThat(curls(out * 15 + outBent * 15 + out * 15).counter).isEqualTo(one)
    }

    @Test
    fun `Haltezeit misst die Zeit der Frames, nicht ihre Anzahl`() {
        // 6 gebeugte Frames: bei 30 fps 0.2 s (zaehlt), bei 60 fps 0.1 s (zu kurz)
        val frames = stretched * 15 + bent * 6 + stretched * 15
        assertThat(Bizepscurls(null, Preset.VOLL).run(frames, fps = 30.0).counter).isEqualTo(one)
        assertThat(Bizepscurls(null, Preset.VOLL).run(frames, fps = 60.0).counter).isEqualTo(none)
    }

    @Test
    fun `Oberarm weg vom Koerper zaehlt nicht (Gate)`() {
        val away = armPose(arm = 70.0 to 70.0, elbow = 175.0 to 175.0)
        val awayBent = armPose(arm = 70.0 to 70.0, elbow = 40.0 to 40.0)
        val exercise = curls(away * 15 + awayBent * 15 + away * 15)
        assertThat(exercise.counter).isEqualTo(none)
        assertThat(exercise.valid).isFalse()
    }

    @Test
    fun `Einfach laesst den Oberarm weiter vom Koerper`() {
        val away = armPose(arm = 55.0 to 55.0, elbow = 175.0 to 175.0)
        val awayBent = armPose(arm = 55.0 to 55.0, elbow = 40.0 to 40.0)
        val frames = away * 15 + awayBent * 15 + away * 15
        assertThat(curls(frames, Preset.VOLL).counter).isEqualTo(none)
        assertThat(curls(frames, Preset.EINFACH).counter).isEqualTo(one)
    }

    @Test
    fun `Luecke ohne Pose verwirft die angefangene Haltezeit`() {
        // je 3 gebeugte Frames (0.1 s) vor und nach der Luecke: zusammen lang genug, einzeln nicht
        val frames = stretched * 15 + bent * 3 + listOf<Pose?>(null) + bent * 3 + stretched * 15
        assertThat(curls(frames).counter).isEqualTo(none)
    }

    @Test
    fun `Beugen ohne vorheriges Strecken zaehlt nicht`() {
        assertThat(curls(bent * 15 + armPose(arm = 10.0 to 10.0, elbow = 115.0 to 115.0) * 15).counter).isEqualTo(none)
    }
}
