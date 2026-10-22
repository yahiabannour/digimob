package de.mhh.digimob.core.domain.exercise

/** Seite aus Sicht des Patienten. Butterfly und Rotation zaehlen eine Bewegung beider Arme: BOTH. */
enum class Side { LEFT, RIGHT, BOTH }

/**
 * Wie die Oberflaeche die Balken zeichnet: SPLIT = ein senkrechter Balken je Seite neben dem Video,
 * CENTER = ein waagerechter Balken unter dem Video, der aus der Mitte nach links/rechts waechst
 * (Kopfdrehung), SINGLE = ein Balken (Seite BOTH) unter dem Video, der aus der Mitte nach beiden
 * Seiten waechst (Arme oeffnen).
 */
enum class BarStyle { SPLIT, CENTER, SINGLE }

/** Schwellen-Preset: VOLL = Klassenwerte, EINFACH = gelockert fuer Patienten. */
enum class Preset { VOLL, EINFACH }

/** Die Meldung im Videobild; den Text waehlt die Oberflaeche. */
sealed interface ExerciseMessage {
    data object Start : ExerciseMessage                 // "Jetzt koennen Sie mit dieser Uebung starten"
    data class Target(val repetitions: Int, val praise: Boolean) : ExerciseMessage  // "Okay, versuchen Sie N mal ..."
    data object Done : ExerciseMessage                  // "Prima, gute Leistung!"
    data object CountOnly : ExerciseMessage             // ohne Ziel (Systemtest): nur zaehlen
}

const val OVER_TARGET = 1.25    // der Balken reicht ein Viertel ueber das Ziel: die Ziellinie liegt bei 80 %

/**
 * Was die Oberflaeche je Balken zeichnet:
 *   fill       0 .. OVER_TARGET, 1 = Ziel erreicht (die Ziellinie), null = kein Messwert (leer)
 *   back       Ziel erreicht, die Bewegung muss erst zurueck, bevor sie zaehlt (Pfeil)
 *   count      die grosse Zahl im Balken: verbleibend ("noch"), oder gezaehlt wenn countOnly
 */
data class Bar(val fill: Double?, val back: Boolean, val count: Int, val countOnly: Boolean)

/**
 * Basisklasse aller Uebungen (uebung.py). Eine Uebung rechnet nur: Pose rein, Zaehlerstand raus.
 * Kamera, Bild, Ton, Einfuehrung und Wechsel zur naechsten Uebung macht die TrainingSession.
 * Eine Uebung fuellt measure(), count() und fill() aus, bei Bedarf ready() (Gate, z.B. Oberarme am
 * Koerper), lost() (kein gueltiger Messwert) und back().
 *
 * target = null: nur zaehlen (Systemtest), nie done.
 */
abstract class Exercise(
    val target: Int?,
    val sides: List<Side> = listOf(Side.LEFT, Side.RIGHT)
) {
    abstract val id: ExerciseId
    open val barStyle: BarStyle = BarStyle.SPLIT

    /** Butterfly und Rotation sagen "Versuchen Sie ..." ohne "Okay,". */
    open val praise: Boolean = true

    /** Die Schwellen dieses Durchgangs (Name wie in Python -> Wert). */
    abstract val thresholds: Thresholds

    private val _counter = sides.associateWith { 0 }.toMutableMap()
    val counter: Map<Side, Int> get() = _counter

    /** Hatte der letzte Frame einen gueltigen Messwert? */
    var valid: Boolean = false
        private set

    var message: ExerciseMessage = ExerciseMessage.Start
        private set

    val total: Int get() = _counter.values.min()

    val done: Boolean get() = target != null && total >= target

    protected fun countRepetition(side: Side) {
        _counter[side] = _counter.getValue(side) + 1
    }

    /** Ein Frame. pose: null = keine Person erkannt, t: Zeit in Sekunden. */
    fun update(pose: Pose?, t: Double) {
        valid = false
        if (pose != null) {
            valid = try {
                measure(pose)
                ready()
            } catch (e: Exception) {
                // Messung nicht moeglich (z.B. zwei Punkte aufeinander): ungueltiger Frame
                false
            }
        }
        if (!valid) {
            lost()
            return
        }
        if (!done) count(t)

        message = when {
            target == null -> ExerciseMessage.CountOnly
            done -> ExerciseMessage.Done
            else -> ExerciseMessage.Target(target, praise)
        }
    }

    /**
     * Ein Balken je Seite. Gleiches Gate wie beim Zaehlen, per Konstruktion: ungueltiger Frame ->
     * fill null (leer), back false; die Zahl bleibt stehen.
     */
    fun progress(): Map<Side, Bar> = sides.associateWith { side ->
        Bar(
            fill = if (valid) fill(side) else null,
            back = valid && back(side),
            count = shownCount(side),
            countOnly = target == null
        )
    }

    /**
     * Im Videobild: verbleibende Wiederholungen, ohne Ziel die gezaehlten. Nie unter 0:
     * eine Seite, die vorne liegt, zaehlt weiter, bis die andere das Ziel erreicht.
     */
    fun shownCount(side: Side): Int {
        val n = _counter.getValue(side)
        return if (target == null) n else maxOf(0, target - n)
    }

    // --- Das fuellt jede Uebung aus ------------------------------------------
    protected abstract fun measure(pose: Pose)            // Winkel/Abstaende berechnen
    protected open fun ready(): Boolean = true            // Gate, z.B. Oberarme am Koerper
    protected abstract fun count(t: Double)               // Zustandsautomat -> countRepetition()
    protected abstract fun fill(side: Side): Double?      // Balken 0..OVER_TARGET einer Seite
    protected open fun back(side: Side): Boolean = false  // Ziel erreicht, erst zurueck, dann zaehlt es
    protected open fun lost() {}                          // kein Messwert, z.B. Haltezeit verwerfen
}

/** Bestaetigt eine Phase erst, wenn sie confirmTime Sekunden ununterbrochen anliegt. */
class HoldTime<T : Any>(private val confirmTime: Double) {
    private var candidate: T? = null
    private var since: Double? = null

    fun reset() {
        candidate = null
        since = null
    }

    fun confirm(candidate: T?, t: Double): T? {
        // Neue Phase erkannt -> Haltezeit neu starten
        if (candidate != this.candidate) {
            this.candidate = candidate
            since = t
        }
        val start = since
        if (candidate != null && start != null && t - start >= confirmTime) return candidate
        return null
    }
}

/** Median der letzten Werte wie in Python: sorted(werte)[len // 2]. */
internal fun List<Double>.median(): Double = sorted()[size / 2]

/** Ohne Angabe (null) gilt ein Punkt als sichtbar. */
internal val Landmark.visibilityOrOne: Double get() = visibility ?: 1.0
