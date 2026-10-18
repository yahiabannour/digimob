package de.mhh.digimob.feature.training.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.core.graphics.createBitmap
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.feature.training.data.pose.MediaPipePoseDetector
import de.mhh.digimob.feature.training.data.pose.PosePipeline
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.PoseSource
import de.mhh.digimob.feature.training.domain.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Ein Aufnahme-Video fuer "Aufnahmen testen" (FileCamera mit realtime=False): jedes Bild der Reihe
 * nach, so schnell das Geraet rechnet, mit seiner Zeit im Video, letterboxed in 640x480, mit einer
 * frischen Pose-Erkennung. So zaehlt jeder Durchlauf gleich. Zoom bleibt aus.
 */
class VideoPoseSource(private val context: Context, private val path: String) : PoseSource<Bitmap> {

    private val log = Logger.withTag("Video")

    override fun frames(zoom: StateFlow<Boolean>): Flow<Result<PoseFrame<Bitmap>, SourceError>> = flow {
        val times = try {
            frameTimes(path)
        } catch (e: Exception) {
            log.e(e) { "$path nicht lesbar" }
            emit(Result.Error(SourceError.VIDEO_UNREADABLE))
            return@flow
        }
        val retriever = MediaMetadataRetriever()
        val pipeline = PosePipeline { MediaPipePoseDetector(context) }
        try {
            retriever.setDataSource(path)
            val count = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
                ?: times.size
            var index = 0
            while (index < count) {
                val batch = retriever.getFramesAtIndex(index, minOf(BATCH, count - index))
                for (frame in batch) {
                    val time = times.getOrElse(index) { index / FALLBACK_FPS }
                    pipeline.process(letterbox(frame), time, zoom = false)?.let { emit(it) }
                    index++
                }
            }
        } catch (e: Exception) {
            log.e(e) { "$path: Abbruch beim Lesen" }
            emit(Result.Error(SourceError.VIDEO_UNREADABLE))
        } finally {
            retriever.release()
            pipeline.close()
        }
    }.flowOn(Dispatchers.Default)

    /** Zeit jedes Bildes in Sekunden, in Anzeigereihenfolge. */
    private fun frameTimes(path: String): List<Double> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            extractor.selectTrack(track)
            val times = mutableListOf<Double>()
            while (extractor.sampleTime >= 0) {
                times += extractor.sampleTime / 1e6
                extractor.advance()
            }
            return times.sorted()
        } finally {
            extractor.release()
        }
    }

    /** Wie FileCamera: Seitenverhaeltnis behalten, schwarze Raender, 640x480. */
    private fun letterbox(frame: Bitmap): Bitmap {
        if (frame.width == WIDTH && frame.height == HEIGHT) return frame
        val scale = minOf(WIDTH.toFloat() / frame.width, HEIGHT.toFloat() / frame.height)
        val w = (frame.width * scale).toInt()
        val h = (frame.height * scale).toInt()
        val out = createBitmap(WIDTH, HEIGHT)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(frame, null, Rect((WIDTH - w) / 2, (HEIGHT - h) / 2, (WIDTH + w) / 2, (HEIGHT + h) / 2), null)
        }
        return out
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
        const val BATCH = 30
        const val FALLBACK_FPS = 30.0
    }
}
