package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side

/**
 * Eine Aufnahme von tools/record_exercise.py fuer "Aufnahmen testen": Video und die Ground Truth
 * aus ihrer summary.json.
 */
data class Recording(
    val name: String,                       // z.B. "abduktion/rec1"
    val videoPath: String,
    val exercise: ExerciseId,
    val expectedReps: Int?,                 // null: ohne Ground Truth, nur zaehlen
    val expectedPerSide: Map<Side, Int>?,   // nur wenn pro Seite eingegeben (Sicht des Patienten)
    val preset: Preset
) {
    /**
     * tools/recording_format.py erwartet_je_seite(): ohne Angabe pro Seite gilt expectedReps fuer jede
     * Seite ("5" im Recorder heisst beide Seiten 5), so faellt auch eine Seite auf, die zu viel zaehlt.
     */
    fun expectedFor(sides: List<Side>): Map<Side, Int>? =
        expectedPerSide ?: expectedReps?.let { n -> sides.associateWith { n } }
}

/** Die Aufnahmen aus videomodus.txt und was an den uebrigen Zeilen nicht stimmte. */
data class RecordingList(val recordings: List<Recording>, val problems: List<String>)

interface RecordingRepository {
    /** NOT_FOUND: videomodus.txt fehlt. */
    suspend fun recordings(): Result<RecordingList, DataError.Local>
}
