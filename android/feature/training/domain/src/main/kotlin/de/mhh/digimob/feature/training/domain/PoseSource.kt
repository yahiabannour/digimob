package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.Error
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.Pose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ein Bild mit seiner Pose. [image] ist das Bild, in dem erkannt wurde (mit Zoom: der Ausschnitt),
 * ungespiegelt; der Typ bleibt offen, damit domain kein Android kennt (die App: Bitmap).
 * [timeSeconds]: live die Uhr, im Aufnahmen-Test die Zeit im Video.
 */
data class PoseFrame<out Image>(val image: Image, val pose: Pose?, val timeSeconds: Double)

/** Warum eine Bildquelle keine Bilder mehr liefert. Danach endet der Flow. */
enum class SourceError : Error {
    NO_CAMERA,          // keine Kamera vorhanden oder nicht zu oeffnen
    CAMERA_FAILED,      // die Kamera liefert keine Bilder mehr
    POSE_FAILED,        // die Pose-Erkennung scheiterte POSE_FAILURES-mal hintereinander
    VIDEO_UNREADABLE    // Aufnahmen testen: Video fehlt oder ist nicht lesbar
}

/**
 * Kamera oder Video mit Pose-Erkennung. Live haelt die Quelle nur das neueste Bild (wie der
 * PoseThread der Desktop-App), ein Video liefert jedes Bild der Reihe nach. Ein Fehler kommt als
 * letztes Element. [zoom]: Kopf-Zoom an/aus, jederzeit umschaltbar.
 */
interface PoseSource<out Image> {
    fun frames(zoom: StateFlow<Boolean>): Flow<Result<PoseFrame<Image>, SourceError>>

    companion object {
        const val POSE_FAILURES = 50
    }
}

/** Die Live-Kamera. */
interface LivePoseSource<out Image> : PoseSource<Image>

/** Ein Aufnahme-Video als Bildquelle (Aufnahmen testen). */
fun interface RecordingPoseSourceFactory<out Image> {
    fun create(videoPath: String): PoseSource<Image>
}
