package de.mhh.digimob.feature.training.presentation

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Landmark
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.ThresholdRepository
import de.mhh.digimob.core.domain.exercise.Thresholds
import de.mhh.digimob.feature.training.domain.Announcement
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.LivePoseSource
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.RecordedFrame
import de.mhh.digimob.feature.training.domain.RecordingCamera
import de.mhh.digimob.feature.training.domain.RecordingRepository
import de.mhh.digimob.feature.training.domain.RecordingSummary
import de.mhh.digimob.feature.training.domain.Take
import de.mhh.digimob.feature.training.domain.SourceError
import de.mhh.digimob.feature.training.domain.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.cos
import kotlin.math.sin

class FakeClock(var now: Double = 0.0) : Clock {
    override fun nowSeconds(): Double = now
}

class FakeSpeaker : Speaker {
    val said = mutableListOf<Announcement>()
    var stops = 0
    override fun say(announcement: Announcement) {
        said += announcement
    }
    override fun stop() {
        stops++
    }
}

class FakeThresholdRepository(initial: Map<ExerciseId, Thresholds> = emptyMap()) : ThresholdRepository {
    private val thresholds =
        MutableStateFlow(ExerciseId.entries.associateWith { initial[it] ?: ExerciseCatalog.values(it, Preset.VOLL) })
    override fun observeThresholds(): Flow<Map<ExerciseId, Thresholds>> = thresholds
    override suspend fun setThresholds(id: ExerciseId, values: Thresholds): EmptyResult<DataError.Local> = Result.Success(Unit)
}

/** Kamera zum Fuettern von Hand: send() ein Bild (das "Bild" ist ein String). */
class FakeLivePoseSource : LivePoseSource<String> {
    private val channel = Channel<Result<PoseFrame<String>, SourceError>>(Channel.UNLIMITED)
    var lastZoom: StateFlow<Boolean>? = null
    var collections = 0
    /** Ob die Kamera gerade laeuft (jemand sammelt ihre Bilder). */
    var isRunning = false

    override fun frames(zoom: StateFlow<Boolean>): Flow<Result<PoseFrame<String>, SourceError>> {
        lastZoom = zoom
        collections++
        return channel.receiveAsFlow().onStart { isRunning = true }.onCompletion { isRunning = false }
    }

    fun send(pose: Pose?, time: Double, image: String = "bild") {
        channel.trySend(Result.Success(PoseFrame(image, pose, time)))
    }

    fun fail(error: SourceError) {
        channel.trySend(Result.Error(error))
    }
}

/** Kamera fuer Aufnahmen: Bilder von Hand, Video-Start/-Ende einstellbar. */
class FakeRecordingCamera(var videoPossible: Boolean = true, var videoComplete: Boolean = true) : RecordingCamera<String> {
    private val channel = Channel<Result<PoseFrame<String>, SourceError>>(Channel.UNLIMITED)
    val videoPaths = mutableListOf<String>()
    var stops = 0
    var collections = 0
    var isRunning = false

    override fun frames(): Flow<Result<PoseFrame<String>, SourceError>> {
        collections++
        return channel.receiveAsFlow().onStart { isRunning = true }.onCompletion { isRunning = false }
    }

    override fun startVideo(path: String): Boolean {
        if (videoPossible) videoPaths += path
        return videoPossible
    }

    /** Gesetzt: das Video wird erst fertig geschrieben, wenn der Test es abschliesst. */
    var videoFinish: CompletableDeferred<Unit>? = null

    override suspend fun stopVideo(): Boolean {
        stops++
        videoFinish?.await()
        return videoComplete
    }

    fun send(pose: Pose?, time: Double) {
        channel.trySend(Result.Success(PoseFrame("bild", pose, time)))
    }

    fun fail(error: SourceError) {
        channel.trySend(Result.Error(error))
    }
}

/** Aufnahmen im Speicher statt in Dateien. */
class FakeRecordingRepository : RecordingRepository {
    var newTakeError: DataError.Local? = null
    var saveError: DataError.Local? = null
    val saved = mutableMapOf<String, Pair<List<RecordedFrame>, RecordingSummary>>()
    val discarded = mutableListOf<String>()
    private var number = 0

    override suspend fun newTake(exercise: ExerciseId): Result<Take, DataError.Local> {
        newTakeError?.let { return Result.Error(it) }
        number++
        return Result.Success(Take("${exercise.key}/rec$number", "/aufnahmen/${exercise.key}/rec$number/video.mp4"))
    }

    override suspend fun save(take: Take, frames: List<RecordedFrame>, summary: RecordingSummary): EmptyResult<DataError.Local> {
        saveError?.let { return Result.Error(it) }
        saved[take.name] = frames to summary
        return Result.Success(Unit)
    }

    override suspend fun discard(take: Take) {
        discarded += take.name
    }
}

/** Beide Arme [arm] Grad aus der Senkrechten (zur virtuellen Huefte rund 5 Grad mehr), ohne Huefte. */
fun arms(arm: Double): Pose {
    val points = MutableList(33) { 0.5 to 0.5 }
    listOf(11, 12).forEachIndexed { k, shoulderIndex ->
        val sx = if (k == 0) 0.4 else 0.6
        val sign = if (k == 0) -1 else 1
        val a = Math.toRadians(arm)
        points[shoulderIndex] = sx to 0.4
        points[shoulderIndex + 2] = (sx + 0.2 * sign * sin(a)) to (0.4 + 0.2 * cos(a))
        points[shoulderIndex + 4] = (sx + 0.4 * sign * sin(a)) to (0.4 + 0.4 * cos(a))
    }
    return Pose(points.map { (x, y) -> Landmark(x, y, 0.0, 1.0, 1.0) }, 640, 480)
}

val DOWN = arms(20.0)
val UP = arms(160.0)

/** Eine Wiederholung beider Arme (Abduktion), Bilder alle 1/30 s. */
fun oneRep(): List<Pose> = List(6) { DOWN } + List(6) { UP } + List(6) { DOWN }
