package de.mhh.digimob.feature.training.data.pose

import android.graphics.Bitmap
import androidx.core.graphics.scale
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.Landmarks
import de.mhh.digimob.feature.training.domain.HeadZoom
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.PoseSource
import de.mhh.digimob.feature.training.domain.SourceError
import java.io.Closeable

/**
 * Ein Bild -> (Zoom) -> Pose, fuer Kamera und Video gleich. Mit Zoom: Nase aus einer eigenen
 * Erkennung auf dem ganzen Bild (erst beim ersten gezoomten Bild erzeugt), Ausschnitt um den Kopf
 * auf die volle Groesse gebracht, die Pose der Uebung wird darauf erkannt (bildoperation_opencv.py).
 */
internal class PosePipeline(private val newDetector: () -> MediaPipePoseDetector) : Closeable {

    private val log = Logger.withTag("PosePipeline")
    private val detector by lazy(newDetector)
    private var noseDetector: MediaPipePoseDetector? = null
    private val headZoom = HeadZoom()
    private var failures = 0

    /** null: Bild uebersprungen (einzelner Fehler); Error: zu viele Fehler hintereinander. */
    fun process(bitmap: Bitmap, timeSeconds: Double, zoom: Boolean): Result<PoseFrame<Bitmap>, SourceError>? {
        val timestampMs = (timeSeconds * 1000).toLong()
        return try {
            val image = if (zoom) zoomed(bitmap, timestampMs) else bitmap.also { headZoom.reset() }
            val pose = detector.detect(image, timestampMs)
            failures = 0
            Result.Success(PoseFrame(image, pose, timeSeconds))
        } catch (e: Exception) {
            failures++
            log.e(e) { "Pose-Erkennung: Bild uebersprungen ($failures in Folge)" }
            if (failures >= PoseSource.POSE_FAILURES) Result.Error(SourceError.POSE_FAILED) else null
        }
    }

    private fun zoomed(bitmap: Bitmap, timestampMs: Long): Bitmap {
        val nose = (noseDetector ?: newDetector().also { noseDetector = it })
            .detect(bitmap, timestampMs)
            ?.landmarks?.get(Landmarks.NASE)
        val crop = headZoom.crop(bitmap.width, bitmap.height, nose?.x, nose?.y)
        val cut = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
        return cut.scale(bitmap.width, bitmap.height)
    }

    override fun close() {
        detector.close()
        noseDetector?.close()
    }
}
