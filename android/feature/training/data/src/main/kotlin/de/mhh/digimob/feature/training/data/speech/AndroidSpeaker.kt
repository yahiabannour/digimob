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
 * Sprachausgabe auf Deutsch (sprachausgabe.py, statt Piper). Ansagen stehen hintereinander an
 * (QUEUE_ADD), stop() bricht die laufende ab und verwirft die wartenden, wie das Hochzaehlen von
 * generation auf dem Desktop. Bis die Stimme geladen ist, warten die Ansagen.
 * Stimme: Googles maennliche deutsche Stimme "deb", offline (vom Nutzer am Tablet ausgewaehlt: die
 * Samsung-Stimme sprach "jetzt" falsch). Fehlt sie, Googles deutsche Standardstimme; fehlt Google
 * oder dessen Deutsch, die Standardstimme des Geraets (das Log nennt die Stimme).
 */
class AndroidSpeaker(private val context: Context) : Speaker {

    private val log = Logger.withTag("Sprache")
    private val lock = Any()
    private val pending = mutableListOf<String>()
    private var ready = false

    @Volatile
    private var tts: TextToSpeech = TextToSpeech(context, { status -> onInit(status, preferred = true) }, GOOGLE_ENGINE)

    private fun onInit(status: Int, preferred: Boolean) {
        val german = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.GERMANY).let {
            it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED
        }
        // Ist Google nicht installiert, nimmt Android selbst die Standardstimme; kann Google kein Deutsch:
        if (preferred && !german) {
            log.w { "Keine deutsche Google-Stimme (Status $status), Standardstimme des Geraets" }
            tts.shutdown()
            tts = TextToSpeech(context) { onInit(it, preferred = false) }
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            log.e { "TextToSpeech nicht verfuegbar ($status), es wird nichts gesprochen" }
            return
        }
        if (!german) log.e { "Keine deutsche Stimme installiert" }
        if (preferred) tts.voices.orEmpty().firstOrNull { it.name == GOOGLE_VOICE }?.let { tts.voice = it }
        log.i { "Stimme ${tts.voice?.name}" }
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

    private companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val GOOGLE_VOICE = "de-de-x-deb-local"
    }
}
