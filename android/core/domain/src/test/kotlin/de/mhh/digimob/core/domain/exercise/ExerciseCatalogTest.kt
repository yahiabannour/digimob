package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class ExerciseCatalogTest {

    private fun thresholds(id: ExerciseId, preset: Preset) = ExerciseCatalog.create(id, 5, preset).thresholds

    @Test
    fun `Voll und Einfach wie in der Python-App`() {
        val expected = mapOf(
            ExerciseId.ABDUKTION to (mapOf("max_angle" to 150.0) to mapOf("max_angle" to 120.0)),
            ExerciseId.SEITENHEBEN to (mapOf("max_angle" to 70.0) to mapOf("max_angle" to 70.0)),
            ExerciseId.FLEXION_SCHULTER to (mapOf("max_angle" to 140.0) to mapOf("max_angle" to 110.0)),
            ExerciseId.MOBILISATION_SCHULTER to (mapOf("max_angle" to 70.0) to mapOf("max_angle" to 70.0)),
            ExerciseId.BIZEPSCURLS to (mapOf("min_angle" to 110.0, "max_shoulder" to 50.0) to
                mapOf("min_angle" to 120.0, "max_shoulder" to 60.0)),
            ExerciseId.BUTTERFLY to (mapOf("max_distance" to 1.8) to mapOf("max_distance" to 1.5)),
            ExerciseId.ROTATION to (mapOf("max_distance" to 1.4) to mapOf("max_distance" to 1.3)),
            ExerciseId.HALSWIRBELSAEULE to (mapOf("max_angle" to 35.0) to mapOf("max_angle" to 28.0)),
        )
        assertThat(ExerciseId.entries.associateWith { thresholds(it, Preset.VOLL) to thresholds(it, Preset.EINFACH) })
            .isEqualTo(expected)
    }

    @Test
    fun `Voll = Einfach nur bei Seitheben und Mobilisation`() {
        assertThat(ExerciseId.entries.filter(ExerciseCatalog::presetsEqual))
            .isEqualTo(listOf(ExerciseId.MOBILISATION_SCHULTER, ExerciseId.SEITENHEBEN))
    }

    @Test
    fun `jede Uebung erzeugt sich selbst, frisch`() {
        ExerciseId.entries.forEach { id ->
            assertThat(ExerciseCatalog.create(id, 5, Preset.VOLL).id).isEqualTo(id)
        }
    }

    @Test
    fun `Schluessel wie die Ordner unter recordings`() {
        assertThat(ExerciseId.fromKey("flexion_schulter")).isEqualTo(ExerciseId.FLEXION_SCHULTER)
        assertThat(ExerciseId.fromKey("Flexion_Schulter")).isNull()
        assertThat(ExerciseId.fromKey("")).isNull()
    }

    @Test
    fun `Tage und OP-Arten wie in uebungen py`() {
        assertThat(PostOpDay.entries.map { it.repetitions }).isEqualTo(listOf(5, 6, 7, 8, 10))
        assertThat(OpType.BRUST_OP.exercises)
            .isEqualTo(listOf(ExerciseId.HALSWIRBELSAEULE, ExerciseId.BUTTERFLY, ExerciseId.ROTATION))
        assertThat(OpType.BAUCH_OP.exercises.toSet()).isEqualTo(ExerciseId.entries.toSet())
    }
}
