package de.mhh.digimob.feature.training.data.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.feature.training.data.BuildConfig
import de.mhh.digimob.feature.training.data.pose.MediaPipePoseDetector
import de.mhh.digimob.feature.training.data.pose.PosePipeline
import de.mhh.digimob.feature.training.domain.LivePoseSource
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors

/** Die Live-Kamera der Uebungen (camera/camera.py), siehe [cameraPoseFrames]. */
class CameraXPoseSource(private val context: Context) : LivePoseSource<Bitmap> {
    override fun frames(zoom: StateFlow<Boolean>): Flow<Result<PoseFrame<Bitmap>, SourceError>> =
        cameraPoseFrames(context, zoom, video = null)
}

private val log = Logger.withTag("Kamera")
private val keypointLog = Logger.withTag("Keypoints")
private val SIZE = Size(640, 480)

/**
 * Kamera mit Pose, fuer die Uebungen und fuer Aufnahmen: Frontkamera (sonst die hintere), 640x480
 * wie die Aufnahmen. ImageAnalysis behaelt nur das neueste Bild; die Pose wird auf dem
 * Analyse-Thread erkannt, so bleibt die Oberflaeche fluessig. Die Kamera haengt an einem eigenen
 * Lifecycle, der mit dem Sammeln des Flows beginnt und endet.
 * [video]: zusaetzlich ein Video (Aufnahmen). Kann die Kamera beides nicht zugleich, laeuft sie
 * ohne Video und [onVideoBound] meldet false.
 */
internal fun cameraPoseFrames(
    context: Context,
    zoom: StateFlow<Boolean>,
    video: UseCase?,
    onVideoBound: (Boolean) -> Unit = {}
): Flow<Result<PoseFrame<Bitmap>, SourceError>> = callbackFlow<Result<PoseFrame<Bitmap>, SourceError>> {
    val provider = try {
        ProcessCameraProvider.awaitInstance(context)
    } catch (e: Exception) {
        log.e(e) { "Kamera nicht verfuegbar" }
        send(Result.Error(SourceError.NO_CAMERA))
        close()
        return@callbackFlow
    }
    val selector = when {
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        else -> null
    }
    if (selector == null) {
        send(Result.Error(SourceError.NO_CAMERA))
        close()
        return@callbackFlow
    }

    val executor = Executors.newSingleThreadExecutor()
    val pipeline = PosePipeline { MediaPipePoseDetector(context) }
    val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
    val start = SystemClock.elapsedRealtimeNanos() / 1e9
    analysis.setAnalyzer(executor) { proxy ->
        val rotation = proxy.imageInfo.rotationDegrees
        val bitmap = proxy.use { it.uprightBitmap() }
        val now = SystemClock.elapsedRealtimeNanos() / 1e9
        pipeline.process(bitmap, now, zoom.value)?.let { result ->
            if (BuildConfig.DEBUG && result is Result.Success) logKeypoints(result.data, now - start, rotation)
            trySend(result)
            if (result is Result.Error) close()
        }
    }

    val lifecycleOwner = SourceLifecycleOwner()
    withContext(Dispatchers.Main) {
        lifecycleOwner.start()
        val camera = if (video == null) {
            provider.bindToLifecycle(lifecycleOwner, selector, analysis)
        } else {
            try {
                provider.bindToLifecycle(lifecycleOwner, selector, analysis, video).also { onVideoBound(true) }
            } catch (e: IllegalArgumentException) {
                // Die Kombination Analyse + Video kann diese Kamera nicht: Aufnahme nur mit Keypoints
                log.w(e) { "Video und Pose zugleich nicht moeglich, Aufnahmen ohne Video" }
                onVideoBound(false)
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            }
        }
        camera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
            val error = state.error ?: return@observe
            log.e { "Kamerafehler ${error.code} (${state.type})" }
            if (state.type == CameraState.Type.CLOSED || error.type == CameraState.ErrorType.CRITICAL) {
                trySend(Result.Error(SourceError.CAMERA_FAILED))
                close()
            }
        }
    }

    awaitClose {
        // Erst keine Bilder mehr an den Analyse-Thread, dann dort das Modell schliessen: ein
        // spaeteres Bild legte es sonst neu an (nativer Speicher, nie freigegeben)
        analysis.clearAnalyzer()
        Dispatchers.Main.dispatch(NonCancellable) {
            provider.unbind(analysis)
            video?.let { provider.unbind(it) }
            lifecycleOwner.destroy()
        }
        executor.execute { pipeline.close() }
        executor.shutdown()
    }
}.buffer(Channel.CONFLATED)   // wie der PoseThread: nur das neueste Ergebnis, ein Fehler kommt als letztes

/**
 * Nur Debug-Build: jedes Bild als Zeile im Format der keypoints_<modell>.jsonl der Aufnahmen
 * (tools/recording_format.py), dazu Bildgroesse und Sensor-Drehung. Mit
 * `adb logcat -s Keypoints` mitschneiden und mit den Python-Zaehlern nachrechnen.
 */
private fun logKeypoints(frame: PoseFrame<Bitmap>, time: Double, rotation: Int) {
    val landmarks = frame.pose?.landmarks?.joinToString(",", "[", "]") {
        "[%.5f,%.5f,%.5f,%.4f,%.4f]".format(Locale.ROOT, it.x, it.y, it.z, it.visibility ?: 1.0, it.presence ?: 1.0)
    } ?: "null"
    keypointLog.i {
        """{"timestamp": ${"%.4f".format(Locale.ROOT, time)}, "size": [${frame.image.width}, ${frame.image.height}], "rotation": $rotation, "pose_landmarks": $landmarks}"""
    }
}

/** Aufrecht (Sensor-Drehung ausgeglichen) und auf 640x480 gebracht. */
private fun ImageProxy.uprightBitmap(): Bitmap {
    val source = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val rotatedWidth = if (rotation % 180 == 0) source.width else source.height
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        val scale = SIZE.width.toFloat() / maxOf(rotatedWidth, 1)
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private class SourceLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
