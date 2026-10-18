package de.mhh.digimob.feature.training.presentation

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Landmark
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.PresetRepository
import de.mhh.digimob.feature.training.domain.Announcement
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.LivePoseSource
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.PoseSource
import de.mhh.digimob.feature.training.domain.RecordingList
import de.mhh.digimob.feature.training.domain.RecordingPoseSourceFactory
import de.mhh.digimob.feature.training.domain.RecordingRepository
import de.mhh.digimob.feature.training.domain.SourceError
import de.mhh.digimob.feature.training.domain.Speaker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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

class FakePresetRepository(initial: Map<ExerciseId, Preset> = emptyMap()) : PresetRepository {
    private val presets = MutableStateFlow(ExerciseId.entries.associateWith { initial[it] ?: Preset.VOLL })
    override fun observePresets(): Flow<Map<ExerciseId, Preset>> = presets
    override suspend fun setPreset(id: ExerciseId, preset: Preset): EmptyResult<DataError.Local> = Result.Success(Unit)
}

/** Kamera zum Fuettern von Hand: send() ein Bild (das "Bild" ist ein String). */
class FakeLivePoseSource : LivePoseSource<String> {
    private val channel = Channel<Result<PoseFrame<String>, SourceError>>(Channel.UNLIMITED)
    var lastZoom: StateFlow<Boolean>? = null
    var collections = 0

    override fun frames(zoom: StateFlow<Boolean>): Flow<Result<PoseFrame<String>, SourceError>> {
        lastZoom = zoom
        collections++
        return channel.receiveAsFlow()
    }

    fun send(pose: Pose?, time: Double, image: String = "bild") {
        channel.trySend(Result.Success(PoseFrame(image, pose, time)))
    }

    fun fail(error: SourceError) {
        channel.trySend(Result.Error(error))
    }
}

/** Videos als feste Bildfolgen je Pfad. */
class FakeRecordingSources(private val videos: Map<String, List<Result<PoseFrame<String>, SourceError>>>) :
    RecordingPoseSourceFactory<String> {
    override fun create(videoPath: String): PoseSource<String> = object : PoseSource<String> {
        override fun frames(zoom: StateFlow<Boolean>) = flowOf(*videos.getValue(videoPath).toTypedArray())
    }
}

class FakeRecordingRepository(private val result: Result<RecordingList, DataError.Local>) : RecordingRepository {
    override suspend fun recordings(): Result<RecordingList, DataError.Local> = result
}

/** Beide Arme im Winkel [arm] (Huefte-Schulter-Ellbogen). */
fun arms(arm: Double): Pose {
    val points = MutableList(33) { 0.5 to 0.5 }
    listOf(11 to 23, 12 to 24).forEachIndexed { k, (shoulderIndex, hipIndex) ->
        val sx = if (k == 0) 0.4 else 0.6
        val sign = if (k == 0) -1 else 1
        val a = Math.toRadians(arm)
        points[hipIndex] = sx to 0.8
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
