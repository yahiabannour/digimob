package de.mhh.digimob.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import de.mhh.digimob.core.designsystem.R

/** Die Icons der App, an einer Stelle. */
object AppIcons {
    val Check: Painter @Composable get() = painterResource(R.drawable.ic_check)
    val Add: Painter @Composable get() = painterResource(R.drawable.ic_add)
    val Remove: Painter @Composable get() = painterResource(R.drawable.ic_remove)
}
