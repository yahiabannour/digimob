package de.mhh.digimob.feature.training.domain

import kotlin.math.round

/** Ausschnitt eines Bildes in Pixeln. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Zoom auf den Kopf (bildoperation_opencv.py): Mitte = Nase, mit EMA geglaettet, Ausschnitt in der
 * Groesse Bild / zoomFactor mit dem Seitenverhaeltnis des Bildes, ganz im Bild. Gerechnet mit
 * ganzen Pixeln wie in Python.
 */
class HeadZoom(
    private val zoomFactor: Double = ZOOM_FACTOR,
    private val smoothFactor: Double = SMOOTH_FACTOR
) {
    private var prevX: Int? = null
    private var prevY: Int? = null

    /** Zoom aus: beim naechsten Einschalten ohne Gedaechtnis anfangen. */
    fun reset() {
        prevX = null
        prevY = null
    }

    /** [noseX]/[noseY] relativ zur Bildgroesse (Landmark 0), null ohne Pose: Bildmitte. */
    fun crop(width: Int, height: Int, noseX: Double?, noseY: Double?): CropRect {
        var cx = if (noseX != null && noseY != null) (noseX * width).toInt() else width / 2
        var cy = if (noseX != null && noseY != null) (noseY * height).toInt() else height / 2
        val px = prevX
        val py = prevY
        if (px == null || py == null) {
            prevX = cx
            prevY = cy
        } else {
            prevX = ((1 - smoothFactor) * px + smoothFactor * cx).toInt()
            prevY = ((1 - smoothFactor) * py + smoothFactor * cy).toInt()
        }
        cx = prevX!!
        cy = prevY!!

        val cropW = maxOf(1, round(width / zoomFactor).toInt())
        val cropH = maxOf(1, round(height / zoomFactor).toInt())
        val halfLeft = cropW / 2
        val halfRight = cropW - halfLeft    // deckt ungerade Breiten ab
        val halfTop = cropH / 2
        val halfBottom = cropH - halfTop

        // Mitte so verschieben, dass der Ausschnitt ganz im Bild liegt
        cx = cx.coerceIn(halfLeft, maxOf(halfLeft, width - halfRight))
        cy = cy.coerceIn(halfTop, maxOf(halfTop, height - halfBottom))
        val left = (cx - halfLeft).coerceIn(0, maxOf(0, width - cropW))
        val top = (cy - halfTop).coerceIn(0, maxOf(0, height - cropH))
        return CropRect(left, top, cropW, cropH)
    }

    companion object {
        const val ZOOM_FACTOR = 2.0
        const val SMOOTH_FACTOR = 0.2
    }
}
