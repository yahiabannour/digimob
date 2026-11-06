package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.exercise.Landmarks
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Side
import kotlin.math.hypot

/**
 * Kopfnicken ueberspringt die Einfuehrung (nur Android): das Tablet steht am Bettende, ein Doppeltipp
 * ist fuer Patienten nicht erreichbar (Feedback 2026-11-04).
 * Gemessen wird die Nase ueber der Schulterlinie, in Schulterbreiten. Das gleicht den Kopf-Zoom aus
 * (der Ausschnitt folgt der Nase) und den Abstand zur Kamera. Ein Nicken: die Hoehe faellt
 * [NOD_DEPTH] unter den hoechsten Wert der letzten [WINDOW_SECONDS] und kommt binnen
 * [NOD_SECONDS] wieder auf die halbe Tiefe zurueck. Langsames Absinken folgt das Fenster mit.
 */
class NodDetector {
    private val history = ArrayDeque<Pair<Double, Double>>()   // (t, Hoehe)
    private var downSince: Double? = null
    private var top = 0.0

    fun reset() {
        history.clear()
        downSince = null
    }

    /** Ein Frame; true genau bei dem Bild, mit dem ein Nicken fertig ist. */
    fun update(pose: Pose?, t: Double): Boolean {
        val h = pose?.let(::height)
        if (h == null) {
            reset()
            return false
        }
        val down = downSince
        if (down != null && t - down <= NOD_SECONDS) {
            if (h < top - NOD_DEPTH / 2) return false
            reset()
            return true
        }
        downSince = null
        history.addLast(t to h)
        while (t - history.first().first > WINDOW_SECONDS) history.removeFirst()
        val highest = history.maxOf { it.second }
        if (h <= highest - NOD_DEPTH) {
            downSince = t
            top = highest
        }
        return false
    }

    private fun height(pose: Pose): Double? {
        val nose = pose.landmarks[Landmarks.NASE]
        val left = pose.landmarks[Landmarks.SCHULTER.getValue(Side.LEFT)]
        val right = pose.landmarks[Landmarks.SCHULTER.getValue(Side.RIGHT)]
        if (listOf(nose, left, right).any { (it.visibility ?: 1.0) < MIN_VISIBILITY }) return null
        val shoulderWidth = hypot((left.x - right.x) * pose.width, (left.y - right.y) * pose.height)
        if (shoulderWidth <= 0.0) return null
        return ((left.y + right.y) / 2 - nose.y) * pose.height / shoulderWidth
    }

    companion object {
        // shortcut: Startwerte geschaetzt, nicht am Bett gemessen; mit Keypoints-Log von echten Nicken einstellen
        const val NOD_DEPTH = 0.15          // Schulterbreiten
        const val NOD_SECONDS = 1.5         // runter und wieder hoch, hoechstens so lange
        const val WINDOW_SECONDS = 1.0      // Bezug: hoechster Wert in dieser Zeit
        const val MIN_VISIBILITY = 0.5
    }
}
