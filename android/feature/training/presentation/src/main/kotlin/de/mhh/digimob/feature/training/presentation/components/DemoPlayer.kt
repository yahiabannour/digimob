package de.mhh.digimob.feature.training.presentation.components

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import co.touchlab.kermit.Logger

/**
 * Die 3D-Demo der Uebung in Endlosschleife, gespiegelt wie das Kamerabild (TextureView, eine
 * SurfaceView liesse sich nicht spiegeln). [onPlayedThrough] nach jedem ganzen Durchlauf; fehlt
 * die Demo, sofort einmal, dann zaehlt nur die Mindestzeit der Einfuehrung. Steht bei Pause still.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun DemoPlayer(
    assetPath: String,
    isPaused: Boolean,
    onPlayedThrough: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnPlayedThrough by rememberUpdatedState(onPlayedThrough)
    val player = remember(assetPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
        }
    }
    // Der Player ist eine Ressource dieses Composables: anlegen und freigeben gehoeren hierher
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) currentOnPlayedThrough()
            }

            override fun onPlayerError(error: PlaybackException) {
                Logger.withTag("Demo").w(error) { "Keine Demo $assetPath, die Einfuehrung zeigt kein Bild" }
                currentOnPlayedThrough()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, isPaused) {
        player.playWhenReady = !isPaused
    }
    ContentFrame(
        player = player,
        modifier = modifier.graphicsLayer { scaleX = -1f },
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW
    )
}
