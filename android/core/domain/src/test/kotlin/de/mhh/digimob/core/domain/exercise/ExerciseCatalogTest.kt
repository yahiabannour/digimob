package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class ExerciseCatalogTest {

    private fun thresholds(id: ExerciseId, preset: Preset) = ExerciseCatalog.create(id, 5, preset).thresholds

    @Test
    fun `Keypoints je Uebung wie in der Python-App, nie die Huefte`() {
        val shouldersElbows = setOf(11, 12, 13, 14)
        val arms = shouldersElbows + setOf(15, 16)
        val expected = mapOf(
            ExerciseId.ABDUKTION to shouldersElbows,
            ExerciseId.SEITENHEBEN to shouldersElbows,
            ExerciseId.FLEXION_SCHULTER to shouldersElbows,
            ExerciseId.MOBILISATION_SCHULTER to shouldersElbows,
            ExerciseId.BIZEPSCURLS to arms,
            ExerciseId.BUTTERFLY to arms,
            ExerciseId.ROTATION to arms,
            ExerciseId.HALSWIRBELSAEULE to (0..6).toSet(),
        )
        assertThat(ExerciseId.entries.associateWith { ExerciseCatalog.landmarks(it) }).isEqualTo(expected)
    }

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
    fun `Schieberegler-Bereiche und Einheiten wie bereich in der Python-App`() {
        val expected = mapOf(
            ExerciseId.ABDUKTION to listOf(Triple("max_angle", 40.0..175.0, ThresholdUnit.DEGREES)),
            ExerciseId.SEITENHEBEN to listOf(Triple("max_angle", 40.0..175.0, ThresholdUnit.DEGREES)),
            ExerciseId.FLEXION_SCHULTER to listOf(Triple("max_angle", 40.0..175.0, ThresholdUnit.DEGREES)),
            ExerciseId.MOBILISATION_SCHULTER to listOf(Triple("max_angle", 40.0..175.0, ThresholdUnit.DEGREES)),
            ExerciseId.BIZEPSCURLS to listOf(
                Triple("min_angle", 30.0..155.0, ThresholdUnit.DEGREES),
                Triple("max_shoulder", 20.0..90.0, ThresholdUnit.DEGREES)
            ),
            ExerciseId.BUTTERFLY to listOf(Triple("max_distance", 1.2..3.0, ThresholdUnit.TIMES)),
            ExerciseId.ROTATION to listOf(Triple("max_distance", 1.2..3.0, ThresholdUnit.TIMES)),
            ExerciseId.HALSWIRBELSAEULE to listOf(Triple("max_angle", 20.0..80.0, ThresholdUnit.DEGREES)),
        )
        assertThat(ExerciseId.entries.associateWith { id -> ExerciseCatalog.thresholds(id).map { Triple(it.key, it.range, it.unit) } })
            .isEqualTo(expected)
    }

    @Test
    fun `Voll und Einfach liegen im Bereich (einheitentest)`() {
        ExerciseId.entries.forEach { id ->
            Preset.entries.forEach { preset ->
                assertThat(ExerciseCatalog.validate(id, ExerciseCatalog.values(id, preset)), "$id $preset").isNull()
            }
        }
    }

    @Test
    fun `Werte vom Schieberegler gelten vor dem Preset`() {
        val curls = ExerciseCatalog.create(ExerciseId.BIZEPSCURLS, 5, mapOf("min_angle" to 95.0)) as Bizepscurls
        assertThat(curls.minAngle).isEqualTo(95.0)
        assertThat(curls.maxShoulder).isEqualTo(50.0)       // fehlt: Voll
        assertThat(curls.maxAngle).isEqualTo(110.0)         // abgeleitet: min_angle + STRECKEN
    }

    @Test
    fun `welches Preset die Werte sind`() {
        assertThat(ExerciseCatalog.presetsOf(ExerciseId.ABDUKTION, mapOf("max_angle" to 120.0))).containsOnly(Preset.EINFACH)
        assertThat(ExerciseCatalog.presetsOf(ExerciseId.SEITENHEBEN, mapOf("max_angle" to 70.0)))
            .containsOnly(Preset.VOLL, Preset.EINFACH)
        assertThat(ExerciseCatalog.presetsOf(ExerciseId.ABDUKTION, mapOf("max_angle" to 121.0))).isEmpty()
        // Ein Wert zu viel ist kein Preset mehr
        assertThat(ExerciseCatalog.presetsOf(ExerciseId.ABDUKTION, mapOf("max_angle" to 150.0, "x" to 1.0))).isEmpty()
    }

    @Test
    fun `pruefe meldet jede Art Fehler`() {
        val id = ExerciseId.BIZEPSCURLS
        val ok = mapOf("min_angle" to 110.0, "max_shoulder" to 50.0)
        assertThat(ExerciseCatalog.validate(id, ok)).isNull()
        assertThat(ExerciseCatalog.validate(id, ok + ("max_angle_x" to 1.0))).isEqualTo(ThresholdError.Unknown("max_angle_x"))
        assertThat(ExerciseCatalog.validate(id, ok - "max_shoulder")).isEqualTo(ThresholdError.Missing("max_shoulder"))
        assertThat(ExerciseCatalog.validate(id, ok + ("min_angle" to 0.0))).isEqualTo(ThresholdError.NotPositive("min_angle"))
        assertThat(ExerciseCatalog.validate(id, ok + ("min_angle" to Double.NaN))).isEqualTo(ThresholdError.NotPositive("min_angle"))
        assertThat(ExerciseCatalog.validate(id, ok + ("max_shoulder" to 90.5))).isEqualTo(ThresholdError.OutOfRange("max_shoulder"))
        // Grenzen gehoeren dazu
        assertThat(ExerciseCatalog.validate(id, mapOf("min_angle" to 30.0, "max_shoulder" to 90.0))).isNull()
    }

    @Test
    fun `min_X ohne einstellbares max_X wird nicht verglichen`() {
        // Wie pruefe(): verglichen wird nur ein Paar min_X/max_X unter den Schwellen. Bizepscurls hat
        // min_angle, aber max_angle ist abgeleitet (min_angle + STRECKEN), also kein Paar. Heute hat
        // keine Uebung ein Paar, MinNotBelowMax ist fuer kuenftige Uebungen da.
        assertThat(ExerciseCatalog.validate(ExerciseId.BIZEPSCURLS, mapOf("min_angle" to 155.0, "max_shoulder" to 20.0))).isNull()
    }

    @Test
    fun `Schieberegler runden wie read_schwellen`() {
        val degrees = Threshold("a", 1.0, 1.0, 0.0..10.0, ThresholdUnit.DEGREES)
        val times = Threshold("b", 1.0, 1.0, 0.0..10.0, ThresholdUnit.TIMES)
        assertThat(degrees.round(149.6)).isEqualTo(150.0)
        assertThat(degrees.round(149.4)).isEqualTo(149.0)
        assertThat(times.round(1.2000000000000002)).isEqualTo(1.2)
        assertThat(times.round(1.4499999)).isEqualTo(1.45)
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
