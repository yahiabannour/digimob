package de.mhh.digimob.feature.training.data.recording

import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.feature.training.domain.Recording
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Der Teil der summary.json (tools/recording_format.py), den "Aufnahmen testen" braucht. */
@Serializable
data class RecordingSummaryDto(
    val exercise: String? = null,
    @SerialName("expected_reps") val expectedReps: Int? = null,
    @SerialName("expected_per_side") val expectedPerSide: Map<String, Int>? = null,
    val preset: String = "voll"
)

/** null: Uebung oder Preset unbekannt. [folderExercise] = Ordnername, wenn summary.json keine nennt. */
fun RecordingSummaryDto.toRecording(name: String, videoPath: String, folderExercise: String): Recording? {
    val id = ExerciseId.fromKey(exercise ?: folderExercise) ?: return null
    val preset = when (preset) {
        "voll" -> Preset.VOLL
        "einfach" -> Preset.EINFACH
        else -> return null
    }
    return Recording(
        name = name,
        videoPath = videoPath,
        exercise = id,
        expectedReps = expectedReps,
        expectedPerSide = expectedPerSide?.let { perSide ->
            mapOf(Side.LEFT to (perSide["left"] ?: return null), Side.RIGHT to (perSide["right"] ?: return null))
        },
        preset = preset
    )
}
