package de.mhh.digimob.feature.training.data.recording

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import org.junit.jupiter.api.Test

class RecordingSummaryDtoTest {

    private fun map(dto: RecordingSummaryDto, folder: String = "abduktion") =
        dto.toRecording("abduktion/rec1", "/v.mp4", folderExercise = folder)

    @Test
    fun `Uebung, Ground Truth und Preset aus der summary json`() {
        val recording = map(RecordingSummaryDto("bizepscurls", 5, mapOf("left" to 5, "right" to 0), "einfach"))!!
        assertThat(recording.exercise).isEqualTo(ExerciseId.BIZEPSCURLS)
        assertThat(recording.expectedReps).isEqualTo(5)
        assertThat(recording.expectedPerSide).isEqualTo(mapOf(Side.LEFT to 5, Side.RIGHT to 0))
        assertThat(recording.preset).isEqualTo(Preset.EINFACH)
    }

    @Test
    fun `ohne Uebung in der summary json gilt der Ordner, ohne Preset Voll`() {
        val recording = map(RecordingSummaryDto(), folder = "rotation")!!
        assertThat(recording.exercise).isEqualTo(ExerciseId.ROTATION)
        assertThat(recording.preset).isEqualTo(Preset.VOLL)
        assertThat(recording.expectedReps).isNull()
    }

    @Test
    fun `unbekannte Uebung oder unbekanntes Preset ist unbrauchbar`() {
        assertThat(map(RecordingSummaryDto(exercise = "kniebeuge"))).isNull()
        assertThat(map(RecordingSummaryDto(preset = "mittel"))).isNull()
        assertThat(map(RecordingSummaryDto(), folder = "videos")).isNull()
    }

    @Test
    fun `pro Seite nur links ist unbrauchbar statt halb geprueft`() {
        assertThat(map(RecordingSummaryDto("abduktion", 3, mapOf("left" to 3)))).isNull()
        assertThat(map(RecordingSummaryDto("abduktion", 3, mapOf("left" to 3, "right" to 3)))).isNotNull()
    }
}
