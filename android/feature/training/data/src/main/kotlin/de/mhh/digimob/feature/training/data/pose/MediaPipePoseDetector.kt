package de.mhh.digimob.feature.training.data.pose

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import de.mhh.digimob.core.domain.exercise.Landmark
import de.mhh.digimob.core.domain.exercise.Pose
import java.io.Closeable

/**
 * MediaPipe-Pose (mediapipe_modelle.py): PoseLandmarker im VIDEO-Modus, ein Mensch, Erkennung und
 * Tracking ab 0.5, Modell "full" (darauf sind die Winkelgrenzen der Uebungen eingestellt).
 * VIDEO glaettet ueber die Zeit, die Zeitstempel muessen daher streng steigen. CPU wie auf dem
 * Desktop. Nicht threadsicher: nur von einem Thread aus benutzen.
 */
class MediaPipePoseDetector(private val context: Context) : Closeable {

    private var landmarker: PoseLandmarker? = null
    private var lastTimestampMs = Long.MIN_VALUE

    /** Ohne Tracking-Gedaechtnis neu anfangen (jede Uebungsfolge, jede Aufnahme). */
    fun reset() {
        close()
        lastTimestampMs = Long.MIN_VALUE
    }

    /** [bitmap] ungespiegelt; null, wenn niemand erkannt wurde. */
    fun detect(bitmap: Bitmap, timestampMs: Long): Pose? {
        val timestamp = maxOf(timestampMs, lastTimestampMs + 1)
        lastTimestampMs = timestamp
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val result = landmarker().detectForVideo(BitmapImageBuilder(argb).build(), timestamp)
        val landmarks = result.landmarks().firstOrNull() ?: return null
        return Pose(
            landmarks.map {
                Landmark(
                    x = it.x().toDouble(),
                    y = it.y().toDouble(),
                    z = it.z().toDouble(),
                    visibility = it.visibility().orElse(null)?.toDouble(),
                    presence = it.presence().orElse(null)?.toDouble()
                )
            },
            argb.width,
            argb.height
        )
    }

    private fun landmarker(): PoseLandmarker = landmarker ?: PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    ).also { landmarker = it }

    override fun close() {
        landmarker?.close()
        landmarker = null
    }

    companion object {
        /** Aus python/models/, beim Bauen in die Assets kopiert (app/build.gradle.kts). */
        const val MODEL = "pose_landmarker_full.task"
    }
}
