package de.mhh.digimob.feature.training.presentation.recording

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import de.mhh.digimob.core.designsystem.components.DigiMobButton
import de.mhh.digimob.core.designsystem.components.DigiMobSegmentedToggle
import de.mhh.digimob.core.designsystem.components.DigiMobStepper
import de.mhh.digimob.core.designsystem.components.PageScaffold
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.core.presentation.labelRes
import de.mhh.digimob.core.presentation.titleRes
import de.mhh.digimob.feature.training.presentation.R
import de.mhh.digimob.feature.training.presentation.components.CameraPermission
import de.mhh.digimob.feature.training.presentation.components.KeepScreenOn
import de.mhh.digimob.feature.training.presentation.components.VideoFrame
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import de.mhh.digimob.core.presentation.R as CoreR

@Composable
fun RecordingRoot(
    onBackClick: () -> Unit,
    viewModel: RecordingViewModel<Bitmap> = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CameraPermission { isGranted -> viewModel.onAction(RecordingAction.OnCameraPermissionResult(isGranted)) }
    KeepScreenOn()
    // ON_STOP, nicht ON_PAUSE: der Erlaubnis-Dialog pausiert die Seite nur
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(RecordingAction.OnScreenHidden) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAction(RecordingAction.OnScreenShown) }

    val frame = state.frame
    val image = remember(frame) { frame?.asImageBitmap() }
    RecordingScreen(state = state, image = image, onAction = viewModel::onAction, onBackClick = onBackClick)
}

@Composable
fun RecordingScreen(
    state: RecordingState<*>,
    image: ImageBitmap?,
    onAction: (RecordingAction) -> Unit,
    onBackClick: () -> Unit
) {
    PageScaffold(title = stringResource(R.string.recording_title)) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.phase) {
                    RecordingPhase.READY, RecordingPhase.STARTING -> SetupControls(state, onAction)
                    RecordingPhase.RECORDING, RecordingPhase.STOPPING -> RecordingControls(state, onAction)
                    RecordingPhase.LABELING, RecordingPhase.SAVING -> GroundTruthControls(state, onAction)
                }
                Spacer(modifier = Modifier.weight(1f))
                DigiMobButton(text = stringResource(CoreR.string.back), onClick = onBackClick)
            }
            VideoFrame(
                title = stringResource(state.exercise.titleRes) + "  ·  " + stringResource(state.preset.labelRes),
                message = statusText(state),
                image = image,
                landmarks = state.pose?.landmarks,
                shownLandmarks = state.shownLandmarks,
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
            )
        }
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { onAction(RecordingAction.OnErrorDismiss) },
            confirmButton = { TextButton(onClick = { onAction(RecordingAction.OnErrorDismiss) }) { Text(stringResource(CoreR.string.ok)) } },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(error.asString()) }
        )
    }
}

/** Vor der Aufnahme: Uebung, Preset, Start, die Meldung zur letzten Aufnahme. */
@Composable
private fun SetupControls(state: RecordingState<*>, onAction: (RecordingAction) -> Unit) {
    Box {
        DigiMobButton(
            text = stringResource(state.exercise.titleRes) + "  ▾",
            onClick = { onAction(RecordingAction.OnExerciseMenuClick) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = state.isExerciseMenuExpanded,
            onDismissRequest = { onAction(RecordingAction.OnExerciseMenuDismiss) }
        ) {
            ExerciseId.entries.forEach { id ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(id.titleRes), style = MaterialTheme.typography.titleLarge) },
                    onClick = { onAction(RecordingAction.OnExerciseSelect(id)) }
                )
            }
        }
    }
    DigiMobSegmentedToggle(
        options = Preset.entries.map { stringResource(it.labelRes) },
        selected = setOf(state.preset.ordinal),
        onSelect = { onAction(RecordingAction.OnPresetSelect(Preset.entries[it])) },
        modifier = Modifier.fillMaxWidth()
    )
    Text(text = stringResource(R.string.recording_hint), style = MaterialTheme.typography.bodyLarge)
    DigiMobButton(
        text = stringResource(R.string.recording_start),
        onClick = { onAction(RecordingAction.OnStartClick) },
        kind = ButtonKind.PRIMAER,
        enabled = state.phase == RecordingPhase.READY && state.isCameraRunning,
        modifier = Modifier.fillMaxWidth()
    )
    state.saved?.let { Text(text = it.asString(), style = MaterialTheme.typography.bodyLarge) }
}

/** Waehrend der Aufnahme: was die App zaehlt, Stop. */
@Composable
private fun RecordingControls(state: RecordingState<*>, onAction: (RecordingAction) -> Unit) {
    CountedText(state)
    if (!state.hasVideo) {
        Text(
            text = stringResource(R.string.recording_no_video),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    DigiMobButton(
        text = stringResource(R.string.recording_stop),
        onClick = { onAction(RecordingAction.OnStopClick) },
        kind = ButtonKind.GEFAHR,
        enabled = state.phase == RecordingPhase.RECORDING,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Nach der Aufnahme: Ground Truth eintragen, speichern oder verwerfen. */
@Composable
private fun GroundTruthControls(state: RecordingState<*>, onAction: (RecordingAction) -> Unit) {
    val enabled = state.phase == RecordingPhase.LABELING
    Text(text = stringResource(R.string.recording_ground_truth_hint), style = MaterialTheme.typography.bodyLarge)
    if (state.isTwoSided) {
        DigiMobStepper(
            label = stringResource(R.string.recording_left),
            value = state.left,
            onValueChange = { onAction(RecordingAction.OnRepsChange(Side.LEFT, it)) }
        )
        DigiMobStepper(
            label = stringResource(R.string.recording_right),
            value = state.right,
            onValueChange = { onAction(RecordingAction.OnRepsChange(Side.RIGHT, it)) }
        )
    } else {
        DigiMobStepper(
            label = stringResource(R.string.recording_reps),
            value = state.left,
            onValueChange = { onAction(RecordingAction.OnRepsChange(Side.BOTH, it)) }
        )
    }
    CountedText(state)
    DigiMobButton(
        text = stringResource(R.string.recording_save),
        onClick = { onAction(RecordingAction.OnSaveClick) },
        kind = ButtonKind.PRIMAER,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    DigiMobButton(
        text = stringResource(R.string.recording_discard),
        onClick = { onAction(RecordingAction.OnDiscardClick) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CountedText(state: RecordingState<*>) {
    val left = state.counted[Side.LEFT]
    val right = state.counted[Side.RIGHT]
    val text = if (left != null && right != null) {
        stringResource(R.string.recording_counted_sides, left, right)
    } else {
        stringResource(R.string.recording_counted, state.counted[Side.BOTH] ?: 0)
    }
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Die Zeile im Videobild, wie die Statuszeile des Vorschaufensters von record_exercise.py. */
@Composable
private fun statusText(state: RecordingState<*>): String = when (state.phase) {
    RecordingPhase.READY, RecordingPhase.STARTING -> stringResource(R.string.recording_status_ready)
    RecordingPhase.RECORDING, RecordingPhase.STOPPING -> stringResource(
        R.string.recording_status_running,
        String.format(Locale.GERMANY, "%.1f", state.seconds),
        state.frameCount,
        stringResource(if (state.hasPose) R.string.recording_pose_yes else R.string.recording_pose_no)
    )
    RecordingPhase.LABELING, RecordingPhase.SAVING -> stringResource(R.string.recording_status_labeling)
}

@Preview(name = "Bereit", widthDp = 1280, heightDp = 800)
@Preview(name = "Bereit dunkel", widthDp = 1280, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RecordingScreenReadyPreview() {
    DigiMobTheme {
        RecordingScreen(
            state = RecordingState<Nothing>(
                isCameraRunning = true,
                saved = UiText.DynamicString("abduktion/rec2 gespeichert: Ground Truth 5, Frames: 412, 9,1 fps, mit Video")
            ),
            image = null,
            onAction = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "Laeuft, ohne Video", widthDp = 1280, heightDp = 800)
@Composable
private fun RecordingScreenRunningPreview() {
    DigiMobTheme {
        RecordingScreen(
            state = RecordingState<Nothing>(
                phase = RecordingPhase.RECORDING,
                isCameraRunning = true,
                seconds = 12.3,
                frameCount = 112,
                hasPose = true,
                counted = mapOf(Side.LEFT to 2, Side.RIGHT to 3),
                hasVideo = false
            ),
            image = null,
            onAction = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "Ground Truth, zwei Seiten", widthDp = 1280, heightDp = 800)
@Composable
private fun RecordingScreenLabelingPreview() {
    DigiMobTheme {
        RecordingScreen(
            state = RecordingState<Nothing>(
                phase = RecordingPhase.LABELING,
                left = 5,
                right = 4,
                counted = mapOf(Side.LEFT to 5, Side.RIGHT to 3),
                hasVideo = true
            ),
            image = null,
            onAction = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "Ground Truth, eine Seite (Butterfly)", widthDp = 1280, heightDp = 800)
@Composable
private fun RecordingScreenSingleSidePreview() {
    DigiMobTheme {
        RecordingScreen(
            state = RecordingState<Nothing>(
                exercise = ExerciseId.BUTTERFLY,
                isTwoSided = false,
                phase = RecordingPhase.LABELING,
                left = 5,
                counted = mapOf(Side.BOTH to 5)
            ),
            image = null,
            onAction = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "Speicherfehler", widthDp = 1280, heightDp = 800)
@Composable
private fun RecordingScreenErrorPreview() {
    DigiMobTheme {
        RecordingScreen(
            state = RecordingState<Nothing>(
                phase = RecordingPhase.LABELING,
                error = UiText.DynamicString("Der Speicher ist voll, die Aufnahme wurde nicht gespeichert.")
            ),
            image = null,
            onAction = {},
            onBackClick = {}
        )
    }
}
