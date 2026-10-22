package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import kotlinx.coroutines.flow.Flow

/*
 * Aufnahmen auf dem Tablet mit Ground Truth (tools/record_exercise.py): Video, Keypoints und
 * summary.json, fuer den Systemtest mit der Tablet-Kamera (android/systest_tablet.txt).
 */

/** Ein Frame einer Aufnahme: Sekunden seit dem ersten Frame, die Pose (null ohne erkannte Person). */
data class RecordedFrame(val timeSeconds: Double, val pose: Pose?)

/**
 * So viele Wiederholungen soll die App zaehlen. [perSide] nur, wenn die Seiten verschieden sind;
 * ohne gilt [reps] fuer jede Seite (recording_format.erwartet_je_seite).
 */
data class GroundTruth(val reps: Int, val perSide: Map<Side, Int>?) {
    companion object {
        /**
         * parse_reps(): [right] null bei Uebungen mit einer Seite (Butterfly, Rotation). Bei zwei
         * Seiten ist reps die kleinere Zahl, wie Exercise.total.
         */
        fun of(left: Int, right: Int?): GroundTruth = when {
            right == null || right == left -> GroundTruth(left, null)
            else -> GroundTruth(minOf(left, right), mapOf(Side.LEFT to left, Side.RIGHT to right))
        }
    }
}

/** Was in die summary.json kommt, ausser dem, was nur die Datenschicht weiss (Kamera, Startzeit). */
data class RecordingSummary(
    val exercise: ExerciseId,
    val preset: Preset,
    val groundTruth: GroundTruth,
    val width: Int,
    val height: Int,
    val framesRecorded: Int,
    val framesWithPose: Int,
    val measuredFps: Double?,
    val hasVideo: Boolean
)

/** Eine angefangene Aufnahme. [name] z.B. "abduktion/rec3", [videoPath] wohin das Video geht. */
data class Take(val name: String, val videoPath: String)

/** Der Ordner der Aufnahmen auf dem Tablet. */
interface RecordingRepository {
    /** Legt den Ordner der naechsten Aufnahme an (rec<N>), unfertige von frueher werden entfernt. */
    suspend fun newTake(exercise: ExerciseId): Result<Take, DataError.Local>

    /** Schreibt Keypoints und summary.json; erst danach ist die Aufnahme fertig. */
    suspend fun save(take: Take, frames: List<RecordedFrame>, summary: RecordingSummary): EmptyResult<DataError.Local>

    /** Ohne Ground Truth wird nichts behalten. */
    suspend fun discard(take: Take)
}

/** Die Kamera fuer Aufnahmen: Bilder mit Pose wie live (ohne Zoom), dazu ein Video. */
interface RecordingCamera<out Image> {
    /** Laeuft, solange gesammelt wird; nur dann kann ein Video laufen. */
    fun frames(): Flow<Result<PoseFrame<Image>, SourceError>>

    /** Video nach [path] beginnen. false: kein Video (die Kamera kann Pose und Video nicht zugleich). */
    fun startVideo(path: String): Boolean

    /** Video beenden. true, wenn die Datei vollstaendig ist. */
    suspend fun stopVideo(): Boolean
}
