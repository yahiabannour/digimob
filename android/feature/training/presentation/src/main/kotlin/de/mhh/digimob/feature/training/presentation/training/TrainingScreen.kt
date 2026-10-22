package de.mhh.digimob.feature.training.presentation.training

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mhh.digimob.core.designsystem.components.ButtonKind
import de.mhh.digimob.core.designsystem.components.ButtonSize
import de.mhh.digimob.core.designsystem.components.DigiMobButton
import de.mhh.digimob.core.designsystem.components.PageScaffold
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.BarStyle
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.ObserveAsEvents
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.core.presentation.titleRes
import de.mhh.digimob.feature.training.domain.Phase
import de.mhh.digimob.feature.training.presentation.R
import de.mhh.digimob.feature.training.presentation.bar.BarUi
import de.mhh.digimob.feature.training.presentation.bar.CenterBar
import de.mhh.digimob.feature.training.presentation.bar.SpreadBar
import de.mhh.digimob.feature.training.presentation.bar.VerticalBar
import de.mhh.digimob.feature.training.presentation.components.CameraPermission
import de.mhh.digimob.feature.training.presentation.components.DemoPlayer
import de.mhh.digimob.feature.training.presentation.components.DemoSkipOverlay
import de.mhh.digimob.feature.training.presentation.components.KeepScreenOn
import de.mhh.digimob.feature.training.presentation.components.VideoFrame
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TrainingRoot(
    exerciseIds: List<ExerciseId>,
    target: Int,
    onBackToMenu: () -> Unit,
    viewModel: TrainingViewModel<Bitmap> = koinViewModel { parametersOf(exerciseIds, target) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            TrainingEvent.Finished -> onBackToMenu()
        }
    }

    CameraPermission { isGranted -> viewModel.onAction(TrainingAction.OnCameraPermissionResult(isGranted)) }
    KeepScreenOn()
    // ON_STOP, nicht ON_PAUSE: der Erlaubnis-Dialog pausiert die Seite nur
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(TrainingAction.OnScreenHidden) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAction(TrainingAction.OnScreenShown) }

    val frame = state.frame
    val image = remember(frame) { frame?.asImageBitmap() }
    TrainingScreen(
        state = state,
        image = image,
        onAction = viewModel::onAction,
        onStopClick = {
            viewModel.onAction(TrainingAction.OnStopClick)
            onBackToMenu()
        }
    )
}

@Composable
fun TrainingScreen(
    state: TrainingState<*>,
    image: ImageBitmap?,
    onAction: (TrainingAction) -> Unit,
    onStopClick: () -> Unit
) {
    // Kopfleiste: links Uebung und Stand ("Abduktion Schulter · Übung 2 von 8"), rechts die Knoepfe
    val title = state.exercise?.let {
        stringResource(it.titleRes) + "  ·  " + stringResource(R.string.exercise_progress, state.index + 1, state.count)
    }.orEmpty()
    PageScaffold(
        title = title,
        actions = {
            ControlRow(
                isPaused = state.isPaused,
                showKeypoints = state.showKeypoints,
                isZoomOn = state.isZoomOn,
                onPauseClick = { onAction(TrainingAction.OnPauseClick) },
                onKeypointsClick = { onAction(TrainingAction.OnKeypointsClick) },
                onZoomClick = { onAction(TrainingAction.OnZoomClick) },
                onStopClick = onStopClick
            )
        }
    ) {
        ExerciseArea(
            state = state,
            image = image,
            onDemoPlayed = { onAction(TrainingAction.OnDemoPlayed) },
            onDemoSkip = { onAction(TrainingAction.OnDemoSkip) },
            modifier = Modifier.fillMaxSize().padding(12.dp)
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = onStopClick) { Text(stringResource(R.string.stop)) } },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(error.asString()) }
        )
    }
}

/**
 * Pause, Keypoints, Zoom und Stop nebeneinander in der Kopfleiste. Die Umschalter sind so breit wie
 * ihre laengere Beschriftung ("Angehalten", "Keypoints aus"): beim Umschalten bleibt jeder Knopf, wo er ist.
 */
@Composable
private fun ControlRow(
    isPaused: Boolean,
    showKeypoints: Boolean,
    isZoomOn: Boolean,
    onPauseClick: () -> Unit,
    onKeypointsClick: () -> Unit,
    onZoomClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    isZoomEnabled: Boolean = true
) {
    Row(
        modifier = modifier.padding(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DigiMobButton(
            text = stringResource(if (isPaused) R.string.paused else R.string.pause),
            onClick = onPauseClick,
            kind = if (isPaused) ButtonKind.ANGEHALTEN else ButtonKind.SEKUNDAER,
            size = ButtonSize.KOMPAKT,
            modifier = Modifier.widthIn(min = TOGGLE_WIDTH)
        )
        DigiMobButton(
            text = stringResource(if (showKeypoints) R.string.keypoints_on else R.string.keypoints_off),
            onClick = onKeypointsClick,
            kind = if (showKeypoints) ButtonKind.AN else ButtonKind.SEKUNDAER,
            size = ButtonSize.KOMPAKT,
            modifier = Modifier.widthIn(min = TOGGLE_WIDTH)
        )
        DigiMobButton(
            text = stringResource(if (isZoomOn) R.string.zoom_on else R.string.zoom_off),
            onClick = onZoomClick,
            kind = if (isZoomOn) ButtonKind.AN else ButtonKind.SEKUNDAER,
            enabled = isZoomEnabled,
            size = ButtonSize.KOMPAKT
        )
        DigiMobButton(
            text = stringResource(R.string.stop),
            onClick = onStopClick,
            kind = ButtonKind.GEFAHR,
            size = ButtonSize.KOMPAKT
        )
    }
}

private val TOGGLE_WIDTH = 168.dp   // "Keypoints aus" in ButtonSize.KOMPAKT
private val SIDE_SLOT = 140.dp      // senkrechter Balken links/rechts neben dem Video
private val BOTTOM_SLOT = 100.dp    // waagerechter Balken unter dem Video, mit Zahl darueber
private val GAP = 12.dp

/**
 * Video (oder Demo) mit den Balken. Video und Balkenplaetze sind fuer jede Uebung gleich gross und
 * gleich platziert: links und rechts je ein Platz fuer die senkrechten Balken (SPLIT), darunter
 * einer fuer den waagerechten (CENTER, SINGLE). Das Video ist das groesste 4:3, das daneben passt;
 * ungenutzte Plaetze bleiben leer.
 */
@Composable
internal fun ExerciseArea(
    state: TrainingState<*>,
    image: ImageBitmap?,
    onDemoPlayed: () -> Unit,
    onDemoSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bar: (Side) -> BarUi = { side -> state.bars[side] ?: BarUi() }
    val barModifier = Modifier.background(DigiMobTheme.colors.barBackground)

    BoxWithConstraints(modifier = modifier) {
        val videoWidth = minOf(maxWidth - (SIDE_SLOT + GAP) * 2, (maxHeight - BOTTOM_SLOT - GAP) * 4f / 3f)
        val videoHeight = videoWidth * 3f / 4f
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GAP)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                // Links/rechts aus Sicht des Patienten: die Anzeige ist gespiegelt, seine linke Seite ist links
                Box(modifier = Modifier.size(SIDE_SLOT, videoHeight)) {
                    if (state.barStyle == BarStyle.SPLIT) VerticalBar(bar(Side.LEFT), barModifier.fillMaxSize())
                }
                VideoFrame(
                    title = null,
                    message = state.message?.asString(),
                    image = image,
                    landmarks = state.pose?.landmarks?.takeIf { state.showKeypoints },
                    shownLandmarks = state.shownLandmarks,
                    modifier = Modifier.size(videoWidth, videoHeight),
                    content = state.exercise?.takeIf { state.phase == Phase.INTRO }?.let { exercise ->
                        {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DemoPlayer(
                                    assetPath = "demos/${exercise.key}.mp4",
                                    isPaused = state.isPaused,
                                    onPlayedThrough = onDemoPlayed,
                                    modifier = Modifier.fillMaxSize()
                                )
                                DemoSkipOverlay(onSkip = onDemoSkip, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                )
                Box(modifier = Modifier.size(SIDE_SLOT, videoHeight)) {
                    if (state.barStyle == BarStyle.SPLIT) VerticalBar(bar(Side.RIGHT), barModifier.fillMaxSize())
                }
            }
            Box(modifier = Modifier.size(videoWidth, BOTTOM_SLOT), contentAlignment = Alignment.BottomCenter) {
                when (state.barStyle) {
                    BarStyle.SPLIT -> Unit
                    BarStyle.CENTER -> CenterBar(bar(Side.LEFT), bar(Side.RIGHT), barModifier.fillMaxWidth().padding(top = 8.dp))
                    BarStyle.SINGLE -> SpreadBar(bar(Side.BOTH), barModifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}

private fun previewState(
    barStyle: BarStyle,
    exercise: ExerciseId,
    bars: Map<Side, BarUi>,
    phase: Phase = Phase.COUNTING,
    isPaused: Boolean = false,
    error: UiText? = null
) = TrainingState<Nothing>(
    phase = phase,
    exercise = exercise,
    index = 1,
    count = 8,
    barStyle = barStyle,
    bars = bars,
    message = UiText.DynamicString("Okay, versuchen Sie 5 mal zu schaffen!"),
    isPaused = isPaused,
    error = error
)

@Preview(name = "Arme getrennt", widthDp = 1280, heightDp = 800)
@Preview(name = "Arme getrennt dunkel", widthDp = 1280, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TrainingScreenSplitPreview() {
    DigiMobTheme {
        TrainingScreen(
            state = previewState(
                BarStyle.SPLIT, ExerciseId.ABDUKTION,
                mapOf(Side.LEFT to BarUi(fill = 1.1, back = true, count = 3), Side.RIGHT to BarUi(fill = 0.4, count = 4))
            ),
            image = null,
            onAction = {},
            onStopClick = {}
        )
    }
}

@Preview(name = "Kopfdrehung, angehalten", widthDp = 1280, heightDp = 800)
@Composable
private fun TrainingScreenCenterPreview() {
    DigiMobTheme {
        TrainingScreen(
            state = previewState(
                BarStyle.CENTER, ExerciseId.HALSWIRBELSAEULE,
                mapOf(Side.LEFT to BarUi(fill = 0.6, count = 2), Side.RIGHT to BarUi(fill = 0.0, count = 3)),
                isPaused = true
            ),
            image = null,
            onAction = {},
            onStopClick = {}
        )
    }
}

@Preview(name = "Ein Balken unter dem Video, Pfeile zur Mitte", widthDp = 1280, heightDp = 800)
@Composable
private fun TrainingScreenSinglePreview() {
    DigiMobTheme {
        TrainingScreen(
            state = previewState(BarStyle.SINGLE, ExerciseId.BUTTERFLY, mapOf(Side.BOTH to BarUi(fill = 1.1, back = true, count = 4))),
            image = null,
            onAction = {},
            onStopClick = {}
        )
    }
}

@Preview(name = "Kamerafehler", widthDp = 1280, heightDp = 800)
@Composable
private fun TrainingScreenErrorPreview() {
    DigiMobTheme {
        TrainingScreen(
            state = previewState(BarStyle.SPLIT, ExerciseId.ABDUKTION, emptyMap(), error = UiText.DynamicString("Die Kamera liefert keine Bilder mehr.")),
            image = null,
            onAction = {},
            onStopClick = {}
        )
    }
}
