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
 * Frame in die Uebung, gezaehlt wird ohne Ziel (target = null) mit dem Preset der
 * Aufnahme. Gezaehlt werden muss genau expected_reps, und jede Seite wie erwartet.
 * Welche Liste, legt die Gradle-Aufgabe fest: systemTest python/systest.txt (PC-Webcam, Keypoints von
 * Python-MediaPipe), tabletTest android/systest_tablet.txt (Tablet-Kamera, Android-MediaPipe,
 * Testname "tablet/...").
 */
class SystemTest {

    // Die Tablet-Liste darf leer sein (noch keine Aufnahmen), dann laeuft kein Fall
    @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
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
    fun `die Liste nennt Aufnahmen`() {
        val files = recordingFiles()
        if (root == android) {
            if (files.isEmpty()) println("$list nennt noch keine Aufnahmen: erst aufnehmen, dann tools/pull_recordings.sh")
        } else {
            // Sonst liefe der parametrisierte Test ohne Fall und meldete trotzdem Erfolg
            assertThat(files).isNotEmpty()
        }
    }

    companion object {
        private fun property(name: String) = System.getProperty(name) ?: error("$name fehlt (nur ueber Gradle starten)")
        private val root = File(property("digimob.root"))
        private val list = property("digimob.list")
        private val android = File(property("digimob.android"))

        /** Eine Keypoints-Datei je Zeile, Pfad relativ zum Ordner der Liste; # und Leerzeilen zaehlen nicht. */
        private fun recordingFiles(): List<File> =
            root.resolve(list).readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { root.resolve(it) }

        @JvmStatic
        fun recordings(): List<Named<File>> = recordingFiles().map { Named.of(name(it), it) }

        // z.B. "abduktion/rec1" wie die Testnamen von systest.py, vom Tablet "tablet/abduktion/rec1"
        private fun name(keypoints: File): String {
            val recording = "${keypoints.parentFile.parentFile.name}/${keypoints.parentFile.name}"
            return if (keypoints.startsWith(android)) "tablet/$recording" else recording
        }

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
