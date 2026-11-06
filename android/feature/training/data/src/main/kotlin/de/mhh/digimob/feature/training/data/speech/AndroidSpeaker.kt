package de.mhh.digimob.feature.training.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
 * [isSpeaking] folgt den eigenen Ansagen ueber einen UtteranceProgressListener (kein Aufruf ins
 * TTS je Kamerabild). Lob, Haelfte, letzte Wiederholung und fertig wechseln ab, nie zweimal
 * hintereinander derselbe Satz.
 */
class AndroidSpeaker(private val context: Context) : Speaker {

    private val log = Logger.withTag("Sprache")
    private val lock = Any()
    private val pending = mutableListOf<String>()
    private var ready = false
    private var failed = false                      // keine Stimme: nichts wartet aufs Sprechen
    private val inFlight = mutableSetOf<String>()   // gesprochen oder in der TTS-Warteschlange
    private val lastPick = mutableMapOf<Int, Int>() // Satzliste -> zuletzt gewaehlter Satz

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
            synchronized(lock) {
                failed = true
                pending.clear()
            }
            return
        }
        if (!german) log.e { "Keine deutsche Stimme installiert" }
        if (preferred) tts.voices.orEmpty().firstOrNull { it.name == GOOGLE_VOICE }?.let { tts.voice = it }
        log.i { "Stimme ${tts.voice?.name}" }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finished(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)
        })
        synchronized(lock) {
            ready = true
            pending.forEach(::speak)
            pending.clear()
        }
    }

    override val isSpeaking: Boolean
        get() = synchronized(lock) { inFlight.isNotEmpty() || pending.isNotEmpty() }

    override fun say(announcement: Announcement) {
        val text = text(announcement)
        synchronized(lock) {
            when {
                ready -> speak(text)
                !failed -> pending += text
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            pending.clear()
            inFlight.clear()
            if (ready) tts.stop()
        }
    }

    /** Unter [lock]. */
    private fun speak(text: String) {
        val id = UUID.randomUUID().toString()
        inFlight += id      // vor speak(): onDone kann kommen, bevor speak() zurueck ist
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) inFlight -= id
    }

    private fun finished(utteranceId: String) {
        synchronized(lock) { inFlight -= utteranceId }
    }

    private fun text(announcement: Announcement): String = when (announcement) {
        is Announcement.Intro -> context.getString(introRes(announcement.exercise))
        is Announcement.Start -> plural(R.plurals.speech_start, announcement.repetitions)
        Announcement.Halfway -> variant(R.array.speech_halfway)
        is Announcement.Remaining -> variant(R.array.speech_remaining).format(announcement.repetitions)
        Announcement.Last -> variant(R.array.speech_last)
        is Announcement.Done -> context.resources.getQuantityString(
            R.plurals.speech_done, announcement.repetitions, announcement.repetitions, variant(R.array.speech_done_praise)
        )
    }

    private fun plural(id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    private fun variant(arrayId: Int): String {
        val texts = context.resources.getStringArray(arrayId)
        val index = synchronized(lock) { pickOther(texts.size, lastPick[arrayId]).also { lastPick[arrayId] = it } }
        return texts[index]
    }

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

/** Ein zufaelliger Index in 0 until [size], nicht [last] (ausser es gibt nur einen). */
internal fun pickOther(size: Int, last: Int?, random: kotlin.random.Random = kotlin.random.Random): Int {
    if (size <= 1 || last == null || last !in 0 until size) return random.nextInt(size)
    return random.nextInt(size - 1).let { if (it >= last) it + 1 else it }
}
