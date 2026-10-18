package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.exercise.ExerciseId

/** Was gesagt wird; den Wortlaut waehlt die Sprachausgabe. */
sealed interface Announcement {
    data class Intro(val exercise: ExerciseId) : Announcement
    data class Start(val repetitions: Int) : Announcement      // "Super, versuchen Sie N Wiederholungen zu schaffen"
    data object Halfway : Announcement                          // "Super, die Hälfte ist geschafft"
    data class Remaining(val repetitions: Int) : Announcement  // "noch N"
    data object Last : Announcement                             // "Letzte Wiederholung"
    data class Done(val repetitions: Int) : Announcement       // "Tolle Arbeit! Sie haben alle N ..."
}

interface Speaker {
    /** Spricht nach der laufenden Ansage, ohne die Bildverarbeitung aufzuhalten. */
    fun say(announcement: Announcement)

    /** Laufende Ansage abbrechen und wartende verwerfen (Stop, naechste Uebung). */
    fun stop()
}
