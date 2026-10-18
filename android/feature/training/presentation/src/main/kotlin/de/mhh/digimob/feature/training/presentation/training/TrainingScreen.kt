package de.mhh.digimob.feature.training.presentation.training

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mhh.digimob.core.designsystem.components.ButtonKind
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
import de.mhh.digimob.feature.training.presentation.bar.VerticalBar
import de.mhh.digimob.feature.training.presentation.components.DemoPlayer
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

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(isGranted))
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    // Bildschirm bleibt an, solange geuebt wird
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

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
    PageScaffold(title = stringResource(R.string.training_title)) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlColumn(
                isPaused = state.isPaused,
                showKeypoints = state.showKeypoints,
                isZoomOn = state.isZoomOn,
                onPauseClick = { onAction(TrainingAction.OnPauseClick) },
                onKeypointsClick = { onAction(TrainingAction.OnKeypointsClick) },
                onZoomClick = { onAction(TrainingAction.OnZoomClick) },
                onStopClick = onStopClick,
                modifier = Modifier.widthIn(max = 260.dp).fillMaxHeight()
            )
            ExerciseArea(
                state = state,
                image = image,
                onDemoPlayed = { onAction(TrainingAction.OnDemoPlayed) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
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

@Composable
private fun ControlColumn(
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DigiMobButton(
            text = stringResource(if (isPaused) R.string.paused else R.string.pause),
            onClick = onPauseClick,
            kind = if (isPaused) ButtonKind.ANGEHALTEN else ButtonKind.SEKUNDAER,
            modifier = Modifier.fillMaxWidth()
        )
        DigiMobButton(
            text = stringResource(if (showKeypoints) R.string.keypoints_on else R.string.keypoints_off),
            onClick = onKeypointsClick,
            kind = if (showKeypoints) ButtonKind.AN else ButtonKind.SEKUNDAER,
            modifier = Modifier.fillMaxWidth()
        )
        DigiMobButton(
            text = stringResource(if (isZoomOn) R.string.zoom_on else R.string.zoom_off),
            onClick = onZoomClick,
            kind = if (isZoomOn) ButtonKind.AN else ButtonKind.SEKUNDAER,
            enabled = isZoomEnabled,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.weight(1f))
        DigiMobButton(
            text = stringResource(R.string.stop),
            onClick = onStopClick,
            kind = ButtonKind.GEFAHR,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Video (oder Demo) mit den Balken, je nach barStyle links + rechts, darunter oder rechts daneben. */
@Composable
internal fun ExerciseArea(
    state: TrainingState<*>,
    image: ImageBitmap?,
    onDemoPlayed: () -> Unit,
    modifier: Modifier = Modifier,
    progressText: String? = state.exercise?.let { stringResource(R.string.exercise_progress, state.index + 1, state.count) }
) {
    val title = state.exercise?.let { stringResource(it.titleRes) }.orEmpty()
    val video: @Composable (Modifier) -> Unit = { videoModifier ->
        VideoFrame(
            title = listOfNotNull(title, progressText).joinToString("  ·  "),
            message = state.message?.asString(),
            image = image,
            landmarks = state.pose?.landmarks?.takeIf { state.showKeypoints },
            modifier = videoModifier,
            content = state.exercise?.takeIf { state.phase == Phase.INTRO }?.let { exercise ->
                {
                    DemoPlayer(
                        assetPath = "demos/${exercise.key}.mp4",
                        isPaused = state.isPaused,
                        onPlayedThrough = onDemoPlayed,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        )
    }
    val bar: (Side) -> BarUi = { side -> state.bars[side] ?: BarUi() }
    val barModifier = Modifier.background(DigiMobTheme.colors.barBackground)

    when (state.barStyle) {
        // Links/rechts aus Sicht des Patienten: die Anzeige ist gespiegelt, seine linke Seite ist links
        BarStyle.SPLIT -> Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VerticalBar(bar(Side.LEFT), barModifier.weight(0.12f).fillMaxHeight())
            video(Modifier.weight(0.76f, fill = false).align(Alignment.CenterVertically))
            VerticalBar(bar(Side.RIGHT), barModifier.weight(0.12f).fillMaxHeight())
        }
        BarStyle.CENTER -> Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            video(Modifier.weight(1f, fill = false))
            CenterBar(bar(Side.LEFT), bar(Side.RIGHT), barModifier.fillMaxWidth().padding(top = 8.dp))
        }
        BarStyle.SINGLE -> Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            video(Modifier.weight(0.82f, fill = false).align(Alignment.CenterVertically))
            VerticalBar(bar(Side.BOTH), barModifier.weight(0.18f).fillMaxHeight())
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
    message = UiText.DynamicString("Super, versuchen Sie 5 mal zu schaffen!"),
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

@Preview(name = "Ein Balken, gezaehlt", widthDp = 1280, heightDp = 800)
@Composable
private fun TrainingScreenSinglePreview() {
    DigiMobTheme {
        TrainingScreen(
            state = previewState(BarStyle.SINGLE, ExerciseId.BUTTERFLY, mapOf(Side.BOTH to BarUi(fill = 0.2, confirm = true, count = 1))),
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
