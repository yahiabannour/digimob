package de.mhh.digimob.feature.training.data.recording

import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.feature.training.domain.RecordedFrame
import de.mhh.digimob.feature.training.domain.RecordingSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.round

/** Die summary.json einer Aufnahme, dieselben Felder wie tools/record_exercise.py. */
@Serializable
internal data class RecordingSummaryDto(
    val exercise: String,
    @SerialName("expected_reps") val expectedReps: Int,
    @SerialName("expected_per_side") val expectedPerSide: Map<String, Int>? = null,
    val preset: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("pose_model") val poseModel: String,
    val video: String? = null,
    val keypoints: String,
    val camera: CameraDto,
    val resolution: List<Int>,
    @SerialName("measured_fps") val measuredFps: Double? = null,
    @SerialName("frames_recorded") val framesRecorded: Int,
    @SerialName("frames_with_pose") val framesWithPose: Int
)

@Serializable
internal data class CameraDto(val name: String)

internal fun RecordingSummary.toDto(startedAt: String, cameraName: String) = RecordingSummaryDto(
    exercise = exercise.key,
    expectedReps = groundTruth.reps,
    // "left"/"right" aus Sicht des Patienten, wie parse_reps()
    expectedPerSide = groundTruth.perSide?.let { mapOf("left" to it.getValue(Side.LEFT), "right" to it.getValue(Side.RIGHT)) },
    preset = if (preset == Preset.EINFACH) "einfach" else "voll",
    startedAt = startedAt,
    poseModel = POSE_MODEL,
    video = VIDEO_FILE.takeIf { hasVideo },
    keypoints = KEYPOINTS_FILE,
    camera = CameraDto(cameraName),
    resolution = listOf(width, height),
    measuredFps = measuredFps?.let { round(it * 10) / 10 },
    framesRecorded = framesRecorded,
    framesWithPose = framesWithPose
)

/**
 * Eine Zeile der keypoints_full.jsonl (recording_format.write_keypoints): Zeit auf Mikrosekunden
 * gerundet, je Landmark [x, y, z, visibility, presence], ohne erkannte Person null.
 */
internal fun RecordedFrame.toKeypointLine(): String = buildJsonObject {
    put("timestamp", round(timeSeconds * 1e6) / 1e6)
    put(
        "pose_landmarks",
        pose?.landmarks?.let { landmarks ->
            JsonArray(landmarks.map {
                JsonArray(listOf(it.x, it.y, it.z, it.visibility, it.presence).map(::JsonPrimitive))
            })
        } ?: JsonNull
    )
}.toString()

internal const val POSE_MODEL = "full"
internal const val KEYPOINTS_FILE = "keypoints_full.jsonl"
internal const val SUMMARY_FILE = "summary.json"
internal const val VIDEO_FILE = "video.mp4"
