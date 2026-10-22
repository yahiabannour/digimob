package de.mhh.digimob.core.domain.exercise

import de.mhh.digimob.core.domain.Error
import kotlin.math.roundToLong

/** Die Einheit einer Schwelle und wie fein ihr Schieberegler stellt (Hauptprogramm.schwellen_regler). */
enum class ThresholdUnit(val step: Double) {
    DEGREES(1.0),   // "°", ganze Grad
    TIMES(0.05)     // "x", Vielfaches eines Abstands (Schulterbreite, Ellbogenabstand)
}

/**
 * Eine einstellbare Schwelle einer Uebung, wie in Python ein Eintrag in schwellen (welche Schwelle),
 * die Klassenwerte (Voll), einfach und bereich: nur Werte, mit denen die Uebung zaehlen kann, so weit
 * reicht der Schieberegler.
 */
data class Threshold(
    val key: String,
    val voll: Double,
    val einfach: Double,
    val range: ClosedFloatingPointRange<Double>,
    val unit: ThresholdUnit
) {
    fun value(preset: Preset): Double = if (preset == Preset.EINFACH) einfach else voll

    /** Wie read_schwellen(): Grad ganzzahlig, Vielfache auf zwei Stellen. */
    fun round(value: Double): Double = when (unit) {
        ThresholdUnit.DEGREES -> value.roundToLong().toDouble()
        ThresholdUnit.TIMES -> (value * 100).roundToLong() / 100.0
    }
}

/** Die Werte der Schwellen einer Uebung, Schluessel wie die Python-Attribute, z.B. "max_angle". */
typealias Thresholds = Map<String, Double>

/** Warum Schwellen nicht zusammenpassen (Uebung.pruefe). */
sealed interface ThresholdError : Error {
    val key: String

    data class Missing(override val key: String) : ThresholdError
    data class Unknown(override val key: String) : ThresholdError
    data class NotPositive(override val key: String) : ThresholdError
    data class OutOfRange(override val key: String) : ThresholdError
    /** Eine untere Schwelle min_X muss unter ihrer oberen max_X liegen, sonst fehlt die Hysterese. */
    data class MinNotBelowMax(override val key: String, val upper: String) : ThresholdError
}

/** Der Wert einer Schwelle: aus [values], sonst aus dem Preset (wie Uebung.__init__ mit schwellen). */
internal fun List<Threshold>.valueOf(key: String, preset: Preset, values: Thresholds): Double =
    values[key] ?: first { it.key == key }.value(preset)
