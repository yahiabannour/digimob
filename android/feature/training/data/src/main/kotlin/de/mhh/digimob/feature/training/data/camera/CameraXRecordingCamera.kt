package de.mhh.digimob.feature.training.data.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.RecordingCamera
import de.mhh.digimob.feature.training.domain.SourceError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Die Kamera fuer Aufnahmen (tools/record_exercise.py): Pose wie live, ohne Zoom, dazu ein Video in
 * SD (480p) ohne Ton, ungespiegelt wie die Analyse. Das Video ist zum Ansehen da (Ground Truth
 * nachpruefen), gezaehlt wird im Systemtest mit den Keypoints.
 */
class CameraXRecordingCamera(private val context: Context) : RecordingCamera<Bitmap> {

    private val log = Logger.withTag("Aufnahme")

    // Nur auf dem Main-Thread benutzt (ViewModel, Kamera-Callbacks)
    private var videoCapture: VideoCapture<Recorder>? = null
    private var videoBound = false
    private var recording: Recording? = null
    private var finalized: CompletableDeferred<Boolean>? = null

    override fun frames(): Flow<Result<PoseFrame<Bitmap>, SourceError>> = flow {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture
        try {
            emitAll(cameraPoseFrames(context, NO_ZOOM, capture) { videoBound = it })
        } finally {
            // Kamera geloest: ein laufendes Video endet von selbst (Finalize mit Fehler)
            videoCapture = null
            videoBound = false
        }
    }

    override fun startVideo(path: String): Boolean {
        val capture = videoCapture?.takeIf { videoBound } ?: return false
        val done = CompletableDeferred<Boolean>()
        return try {
            recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(File(path)).build())
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (event.hasError()) log.e(event.cause) { "Video unvollstaendig (Fehler ${event.error})" }
                        done.complete(!event.hasError())
                    }
                }
            finalized = done
            true
        } catch (e: IllegalStateException) {
            log.e(e) { "Video nicht gestartet" }
            false
        }
    }

    override suspend fun stopVideo(): Boolean {
        val running = recording ?: return false
        recording = null
        running.stop()
        // Das Video ist erst nach Finalize abspielbar (moov-Atom geschrieben)
        return withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { finalized?.await() } ?: false
    }

    private companion object {
        val NO_ZOOM = MutableStateFlow(false)
        const val FINALIZE_TIMEOUT_MS = 5_000L
    }
}
