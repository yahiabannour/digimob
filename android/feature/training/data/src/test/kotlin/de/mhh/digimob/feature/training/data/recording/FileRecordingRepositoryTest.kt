package de.mhh.digimob.feature.training.data.recording

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Landmark
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.feature.training.domain.GroundTruth
import de.mhh.digimob.feature.training.domain.RecordedFrame
import de.mhh.digimob.feature.training.domain.RecordingSummary
import de.mhh.digimob.feature.training.domain.Take
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDateTime

/** Mit echten Dateien im Temp-Ordner (Datei-I/O): Format und Ordner wie tools/record_exercise.py. */
@OptIn(ExperimentalCoroutinesApi::class)
class FileRecordingRepositoryTest {

    @TempDir
    lateinit var root: File

    private val repository by lazy {
        FileRecordingRepository(
            root = root,
            cameraName = "samsung SM-X210",
            now = { LocalDateTime.of(2026, 10, 19, 14, 30, 5, 123_000_000) },
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    private val pose = Pose(List(33) { Landmark(0.5, 0.25 + it / 100.0, -0.1, 0.99, 0.98) }, 640, 480)

    private fun summary(groundTruth: GroundTruth = GroundTruth(5, null), hasVideo: Boolean = false) = RecordingSummary(
        exercise = ExerciseId.ABDUKTION,
        preset = Preset.EINFACH,
        groundTruth = groundTruth,
        width = 640,
        height = 480,
        framesRecorded = 2,
        framesWithPose = 1,
        measuredFps = 9.14,
        hasVideo = hasVideo
    )

    private suspend fun newTake(id: ExerciseId = ExerciseId.ABDUKTION): Take =
        (repository.newTake(id) as Result.Success).data

    private fun summaryJson(take: Take) = Json.parseToJsonElement(root.resolve(take.name).resolve("summary.json").readText()).jsonObject

    @Test
    fun `Aufnahmen werden rec1, rec2 je Uebung nummeriert`() = runTest {
        val first = newTake()
        repository.save(first, emptyList(), summary())
        val second = newTake()
        val other = newTake(ExerciseId.ROTATION)
        assertThat(listOf(first.name, second.name, other.name)).containsExactly("abduktion/rec1", "abduktion/rec2", "rotation/rec1")
        assertThat(second.videoPath).isEqualTo(root.resolve("abduktion/rec2/video.mp4").path)
    }

    @Test
    fun `unfertige Aufnahme von frueher wird entfernt, fertige bleiben`() = runTest {
        val done = newTake()
        repository.save(done, emptyList(), summary())
        val aborted = newTake()                                     // App beendet: keine summary.json
        root.resolve(aborted.name).resolve("video.mp4").writeText("halb")
        val next = newTake()
        assertThat(next.name).isEqualTo("abduktion/rec2")          // die Nummer der unfertigen
        assertThat(root.resolve(done.name).resolve("summary.json").exists()).isTrue()
        assertThat(root.resolve(next.name).resolve("video.mp4").exists()).isFalse()
    }

    @Test
    fun `summary json mit den Feldern von record_exercise`() = runTest {
        val take = newTake()
        repository.save(take, emptyList(), summary())
        val json = summaryJson(take)
        assertThat(json.getValue("exercise").jsonPrimitive.content).isEqualTo("abduktion")
        assertThat(json.getValue("expected_reps").jsonPrimitive.int).isEqualTo(5)
        assertThat(json["expected_per_side"]).isNull()                 // beide Seiten gleich: ohne
        assertThat(json.getValue("preset").jsonPrimitive.content).isEqualTo("einfach")
        assertThat(json.getValue("started_at").jsonPrimitive.content).isEqualTo("2026-10-19T14:30:05")
        assertThat(json.getValue("pose_model").jsonPrimitive.content).isEqualTo("full")
        assertThat(json.getValue("keypoints").jsonPrimitive.content).isEqualTo("keypoints_full.jsonl")
        assertThat(json.getValue("camera").jsonObject.getValue("name").jsonPrimitive.content).isEqualTo("samsung SM-X210")
        assertThat(json.getValue("resolution").jsonArray.map { it.jsonPrimitive.int }).containsExactly(640, 480)
        assertThat(json.getValue("measured_fps").jsonPrimitive.double).isEqualTo(9.1)
        assertThat(json.getValue("frames_recorded").jsonPrimitive.int).isEqualTo(2)
        assertThat(json.getValue("frames_with_pose").jsonPrimitive.int).isEqualTo(1)
    }

    @Test
    fun `Seiten verschieden - expected_per_side links und rechts`() = runTest {
        val take = newTake()
        repository.save(take, emptyList(), summary(GroundTruth.of(5, 3)))
        val perSide = summaryJson(take).getValue("expected_per_side").jsonObject
        assertThat(perSide.getValue("left").jsonPrimitive.int).isEqualTo(5)
        assertThat(perSide.getValue("right").jsonPrimitive.int).isEqualTo(3)
        assertThat(summaryJson(take).getValue("expected_reps").jsonPrimitive.int).isEqualTo(3)
    }

    @Test
    fun `video steht nur in der summary, wenn die Datei da ist`() = runTest {
        val withoutFile = newTake()
        repository.save(withoutFile, emptyList(), summary(hasVideo = true))   // Video nicht fertig geworden
        assertThat(summaryJson(withoutFile)["video"]).isNull()

        val withFile = newTake()
        File(withFile.videoPath).writeBytes(byteArrayOf(1, 2, 3))
        repository.save(withFile, emptyList(), summary(hasVideo = true))
        assertThat(summaryJson(withFile).getValue("video").jsonPrimitive.content).isEqualTo("video.mp4")
    }

    @Test
    fun `Keypoints eine Zeile je Frame, ohne Pose null, Zeit auf Mikrosekunden`() = runTest {
        val take = newTake()
        repository.save(take, listOf(RecordedFrame(0.0, pose), RecordedFrame(0.1234567, null)), summary())
        val lines = root.resolve(take.name).resolve("keypoints_full.jsonl").readLines()
        assertThat(lines.size).isEqualTo(2)
        val first = Json.parseToJsonElement(lines[0]).jsonObject
        val landmarks = first.getValue("pose_landmarks").jsonArray
        assertThat(landmarks.size).isEqualTo(33)
        assertThat(landmarks[2].jsonArray.map { it.jsonPrimitive.double }).containsExactly(0.5, 0.27, -0.1, 0.99, 0.98)
        val second = Json.parseToJsonElement(lines[1]).jsonObject
        assertThat(second.getValue("timestamp").jsonPrimitive.double).isEqualTo(0.123457)
        assertThat(second.getValue("pose_landmarks")).isEqualTo(JsonNull)
    }

    @Test
    fun `verwerfen loescht den Ordner samt Video`() = runTest {
        val take = newTake()
        File(take.videoPath).writeText("video")
        repository.discard(take)
        assertThat(root.resolve(take.name).exists()).isFalse()
        assertThat(newTake().name).isEqualTo("abduktion/rec1")
    }

    @Test
    fun `nicht beschreibbarer Ordner ergibt einen Fehler statt eines Absturzes`() = runTest {
        root.resolve("abduktion").writeText("eine Datei, kein Ordner")
        assertThat(repository.newTake(ExerciseId.ABDUKTION)).isInstanceOf<Result.Error<*>>()
    }
}
