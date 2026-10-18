package de.mhh.digimob.feature.training.presentation.bar

import de.mhh.digimob.core.domain.exercise.Bar
import de.mhh.digimob.feature.training.domain.Clock

/** Was ein Balken zeigt. fill/peak null = leer bzw. keine Markierung, count null = keine Zahl. */
data class BarUi(
    val fill: Double? = null,
    val back: Boolean = false,      // Pfeil: Ziel erreicht, erst zurueck
    val confirm: Boolean = false,   // gruen mit Haken: eben gezaehlt
    val peak: Double? = null,       // hoechster Punkt der laufenden Wiederholung
    val count: Int? = null,
    val countOnly: Boolean = false  // "gezählt" statt "noch"
)

/**
 * Was ein Balken ausser dem Fuellstand zeigt (balken.py BalkenZustand): Bestaetigung nach einer
 * gezaehlten Wiederholung, kurzes Stehenlassen ohne Messwert, hoechster Punkt der laufenden
 * Wiederholung. Einer je Balken; die Uhr ist austauschbar.
 */
class BarFeedback(private val clock: Clock) {
    private var count: Int? = null      // zuletzt angezeigte Zahl; aendert sie sich, zaehlte eine Wiederholung
    private var confirmUntil = Double.NEGATIVE_INFINITY
    private var peak: Double? = null
    private var back = false            // letzter gueltiger Stand von Bar.back
    private var last: Double? = null    // letzter gueltiger Fuellstand
    private var seen = 0.0              // wann er gemessen wurde

    fun update(bar: Bar?): BarUi {
        if (bar == null) return BarUi()
        val now = clock.nowSeconds()
        val counted = count != null && bar.count != count
        if (counted) confirmUntil = now + CONFIRM_SECONDS
        count = bar.count

        val previous = last
        val measured = bar.fill
        val fill: Double?
        val showBack: Boolean
        if (measured != null) {
            if (counted || (back && !bar.back)) peak = null     // Wiederholung fertig: neu messen
            val currentPeak = peak
            if (currentPeak != null) {
                peak = maxOf(currentPeak, measured)
            } else if (previous != null && measured > previous + 0.02) {
                peak = measured                                 // erst, wenn es wieder hochgeht
            }
            back = bar.back
            last = measured
            seen = now
            fill = measured
            showBack = bar.back
        } else if (previous != null && now - seen < HOLD_SECONDS) {
            fill = previous                                     // kurz kein Messwert: stehen lassen
            showBack = back
        } else {
            fill = null
            showBack = false
        }
        // Nur zeigen, wenn gemessen und schon wieder tiefer als der hoechste Punkt
        val shownPeak = peak?.takeIf { it >= 0.05 && fill != null && it > fill + 0.03 }
        return BarUi(fill, showBack, now < confirmUntil, shownPeak, bar.count, bar.countOnly)
    }

    companion object {
        const val CONFIRM_SECONDS = 0.6     // so lange gruen mit Haken nach einer gezaehlten Wiederholung
        // Mit den Armen oben verliert MediaPipe die Pose oft fuer 1-5 Bilder, der Balken flackerte
        const val HOLD_SECONDS = 0.4
    }
}
