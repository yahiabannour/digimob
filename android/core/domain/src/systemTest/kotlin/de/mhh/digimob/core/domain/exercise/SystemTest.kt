package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

/**
 * python/systest.py in Kotlin: die Keypoints einer Aufnahme (keypoints_full.jsonl) Frame fuer
 * Frame in die Uebung, gezaehlt wird wie in "Aufnahmen testen" (target = null) mit dem Preset der
 * Aufnahme. Gezaehlt werden muss genau expected_reps, und jede Seite wie erwartet.
 */
class SystemTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordings")
    fun `zaehlt die Wiederholungen der Aufnahme`(keypoints: File) {
        val summary = Json.parseToJsonElement(keypoints.resolveSibling("summary.json").readText()).jsonObject
        val exercise = exerciseFor(summary)
        val (width, height) = summary.getValue("resolution").jsonArray.map { it.jsonPrimitive.int }

        keypoints.useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val frame = Json.parseToJsonElement(line).jsonObject
                val points = frame.getValue("pose_landmarks")
                val pose = if (points is JsonNull) null else Pose(points.jsonArray.map(::landmark), width, height)
                exercise.update(pose, frame.getValue("timestamp").jsonPrimitive.double)
            }
        }

        val expected = summary.getValue("expected_reps").jsonPrimitive.int
        val expectedPerSide = expectedPerSide(exercise, expected, summary["expected_per_side"] as? JsonObject)
        val preset = summary["preset"]?.jsonPrimitive?.content ?: "voll"
        // "<gezaehlt>/<erwartet> <preset>" wie systest.py, bei zwei Seiten jede Seite dahinter
        val perSide = if (exercise.sides.size > 1) {
            " (links ${exercise.counter[Side.LEFT]}, rechts ${exercise.counter[Side.RIGHT]})"
        } else {
            ""
        }
        println("${name(keypoints)}: ${exercise.total}/$expected $preset$perSide")

        assertThat(exercise.total to exercise.counter).isEqualTo(expected to expectedPerSide)
    }

    @Test
    fun `systest txt nennt Aufnahmen`() {
        // Sonst liefe der parametrisierte Test ohne Fall und meldete trotzdem Erfolg
        assertThat(recordingFiles()).isNotEmpty()
    }

    companion object {
        private val root = File(System.getProperty("digimob.python") ?: error("digimob.python fehlt"))

        private fun recordingFiles(): List<File> =
            root.resolve("systest.txt").readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { root.resolve(it) }

        @JvmStatic
        fun recordings(): List<Named<File>> = recordingFiles().map { Named.of(name(it), it) }

        // z.B. "abduktion/rec1", wie die Testnamen von systest.py
        private fun name(keypoints: File): String = "${keypoints.parentFile.parentFile.name}/${keypoints.parentFile.name}"

        private fun landmark(element: kotlinx.serialization.json.JsonElement): Landmark {
            val v = element.jsonArray.map { it.jsonPrimitive.doubleOrNull }
            return Landmark(v[0]!!, v[1]!!, v[2]!!, v.getOrNull(3), v.getOrNull(4))
        }

        private fun exerciseFor(summary: JsonObject): Exercise {
            val id = ExerciseId.fromKey(summary.getValue("exercise").jsonPrimitive.content)
                ?: error("Unbekannte Uebung in summary.json: ${summary["exercise"]}")
            val preset = when (val name = summary["preset"]?.jsonPrimitive?.content ?: "voll") {
                "voll" -> Preset.VOLL
                "einfach" -> Preset.EINFACH
                else -> error("Unbekanntes Preset: $name")
            }
            return ExerciseCatalog.create(id, target = null, preset = preset)
        }

        // tools/recording_format.py erwartet_je_seite(): ohne Angabe pro Seite gilt expected_reps fuer jede Seite
        private fun expectedPerSide(exercise: Exercise, expected: Int, perSide: JsonObject?): Map<Side, Int> {
            if (perSide == null) return exercise.sides.associateWith { expected }
            return mapOf(
                Side.LEFT to perSide.getValue("left").jsonPrimitive.int,
                Side.RIGHT to perSide.getValue("right").jsonPrimitive.int
            )
        }
    }
}
