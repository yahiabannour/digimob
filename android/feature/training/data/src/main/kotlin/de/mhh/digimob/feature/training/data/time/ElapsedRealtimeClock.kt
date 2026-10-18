package de.mhh.digimob.feature.training.data.time

import android.os.SystemClock
import de.mhh.digimob.feature.training.domain.Clock

/** Monotone Uhr seit dem Geraetestart, laeuft auch im Standby weiter. */
class ElapsedRealtimeClock : Clock {
    override fun nowSeconds(): Double = SystemClock.elapsedRealtimeNanos() / 1e9
}
