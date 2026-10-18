package de.mhh.digimob.feature.training.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HeadZoomTest {

    @Test
    fun `Nase in der Mitte gibt den mittleren Ausschnitt halber Groesse`() {
        assertThat(HeadZoom().crop(640, 480, 0.5, 0.5)).isEqualTo(CropRect(160, 120, 320, 240))
    }

    @Test
    fun `ohne Pose die Bildmitte`() {
        assertThat(HeadZoom().crop(640, 480, null, null)).isEqualTo(CropRect(160, 120, 320, 240))
    }

    @Test
    fun `Nase am Rand verschiebt den Ausschnitt ganz ins Bild`() {
        assertThat(HeadZoom().crop(640, 480, 0.0, 0.0)).isEqualTo(CropRect(0, 0, 320, 240))
        assertThat(HeadZoom().crop(640, 480, 1.0, 1.0)).isEqualTo(CropRect(320, 240, 320, 240))
        assertThat(HeadZoom().crop(640, 480, -0.3, 1.7)).isEqualTo(CropRect(0, 240, 320, 240))
    }

    @Test
    fun `ungerade Groesse rundet wie Python (zur geraden Zahl)`() {
        // 641 / 2 = 320.5 -> 320, 481 / 2 = 240.5 -> 240
        val crop = HeadZoom().crop(641, 481, 1.0, 1.0)
        assertThat(crop).isEqualTo(CropRect(321, 241, 320, 240))
    }

    @Test
    fun `Glaettung folgt der Nase zu 20 Prozent je Bild`() {
        val zoom = HeadZoom()
        zoom.crop(640, 480, 0.5, 0.5)                                   // Mitte 320, 240
        val crop = zoom.crop(640, 480, 420.0 / 640, 340.0 / 480)        // Ziel 420, 340
        // 0.8 * 320 + 0.2 * 420 = 340, 0.8 * 240 + 0.2 * 340 = 260
        assertThat(crop).isEqualTo(CropRect(340 - 160, 260 - 120, 320, 240))
    }

    @Test
    fun `reset vergisst die geglaettete Mitte`() {
        val zoom = HeadZoom()
        zoom.crop(640, 480, 0.0, 0.0)
        zoom.reset()
        assertThat(zoom.crop(640, 480, 0.5, 0.5)).isEqualTo(CropRect(160, 120, 320, 240))
    }
}
