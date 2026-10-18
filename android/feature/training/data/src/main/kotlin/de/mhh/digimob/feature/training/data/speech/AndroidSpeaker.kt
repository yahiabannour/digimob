package de.mhh.digimob.feature.training.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.feature.training.data.R
import de.mhh.digimob.feature.training.domain.Announcement
import de.mhh.digimob.feature.training.domain.Speaker
import java.util.Locale
import java.util.UUID

/**
 * Sprachausgabe mit der deutschen Stimme des Geraets (sprachausgabe.py, statt Piper). Ansagen
 * stehen hintereinander an (QUEUE_ADD), stop() bricht die laufende ab und verwirft die wartenden,
 * wie das Hochzaehlen von generation auf dem Desktop. Bis die Stimme geladen ist, warten die Ansagen.
 */
class AndroidSpeaker(private val context: Context) : Speaker {

    private val log = Logger.withTag("Sprache")
    private val lock = Any()
    private val pending = mutableListOf<String>()
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context) { status -> onInit(status) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            log.e { "TextToSpeech nicht verfuegbar ($status), es wird nichts gesprochen" }
            return
        }
        val language = tts.setLanguage(Locale.GERMANY)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            log.e { "Keine deutsche Stimme installiert ($language)" }
        }
        synchronized(lock) {
            ready = true
            pending.forEach(::speak)
            pending.clear()
        }
    }

    override fun say(announcement: Announcement) {
        val text = text(announcement)
        synchronized(lock) {
            if (ready) speak(text) else pending += text
        }
    }

    override fun stop() {
        synchronized(lock) {
            pending.clear()
            if (ready) tts.stop()
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    private fun text(announcement: Announcement): String = when (announcement) {
        is Announcement.Intro -> context.getString(introRes(announcement.exercise))
        is Announcement.Start -> plural(R.plurals.speech_start, announcement.repetitions)
        Announcement.Halfway -> context.getString(R.string.speech_halfway)
        is Announcement.Remaining -> context.getString(R.string.speech_remaining, announcement.repetitions)
        Announcement.Last -> context.getString(R.string.speech_last)
        is Announcement.Done -> plural(R.plurals.speech_done, announcement.repetitions)
    }

    private fun plural(id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    private fun introRes(id: ExerciseId): Int = when (id) {
        ExerciseId.ABDUKTION -> R.string.speech_intro_abduktion
        ExerciseId.BIZEPSCURLS -> R.string.speech_intro_bizepscurls
        ExerciseId.BUTTERFLY -> R.string.speech_intro_butterfly
        ExerciseId.FLEXION_SCHULTER -> R.string.speech_intro_flexion_schulter
        ExerciseId.HALSWIRBELSAEULE -> R.string.speech_intro_halswirbelsaeule
        ExerciseId.MOBILISATION_SCHULTER -> R.string.speech_intro_mobilisation_schulter
        ExerciseId.ROTATION -> R.string.speech_intro_rotation
        ExerciseId.SEITENHEBEN -> R.string.speech_intro_seitenheben
    }
}
