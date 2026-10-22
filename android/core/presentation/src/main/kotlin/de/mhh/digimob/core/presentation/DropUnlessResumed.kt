package de.mhh.digimob.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Wie androidx' dropUnlessResumed, fuer Navigations-Callbacks mit zwei Argumenten: ein Doppeltipp
 * navigiert nur einmal. Nach dem ersten Tipp ist der Eintrag waehrend des Uebergangs nicht mehr
 * RESUMED, der zweite faellt weg (sonst zwei Uebungsseiten mit zwei Kameras uebereinander).
 */
@Composable
fun <A, B> dropUnlessResumed(block: (A, B) -> Unit): (A, B) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBlock by rememberUpdatedState(block)
    return remember(lifecycleOwner) {
        { a, b -> if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentBlock(a, b) }
    }
}
