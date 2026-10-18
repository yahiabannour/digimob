package de.mhh.digimob.feature.training.presentation.recording

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mhh.digimob.core.designsystem.components.ButtonKind
import de.mhh.digimob.core.designsystem.components.DigiMobButton
import de.mhh.digimob.core.designsystem.components.PageScaffold
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.Phase
import de.mhh.digimob.feature.training.presentation.R
import de.mhh.digimob.feature.training.presentation.training.ExerciseArea
import de.mhh.digimob.feature.training.presentation.training.TrainingState
import org.koin.compose.viewmodel.koinViewModel
import de.mhh.digimob.core.presentation.R as CoreR

@Composable
fun RecordingTestRoot(
    onBackClick: () -> Unit,
    viewModel: RecordingTestViewModel<Bitmap> = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val frame = state.frame
    val image = remember(frame) { frame?.asImageBitmap() }
    RecordingTestScreen(
        state = state,
        image = image,
        onStopClick = {
            viewModel.stop()
            onBackClick()
        }
    )
}

@Composable
fun RecordingTestScreen(
    state: RecordingTestState<*>,
    image: ImageBitmap?,
    onStopClick: () -> Unit
) {
    PageScaffold(title = stringResource(R.string.recording_test_title)) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(0.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        state.error != null -> state.error.asString()
                        state.isRunning && state.current != null ->
                            stringResource(R.string.recording_running, state.current, state.index + 1, state.count)
                        else -> stringResource(R.string.recording_done, state.okCount, state.count)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.problems) { problem ->
                        Text(text = problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    items(state.results, key = { it.name }) { result -> ResultRow(result) }
                }
                DigiMobButton(
                    text = stringResource(if (state.isRunning) R.string.stop else CoreR.string.back),
                    onClick = onStopClick,
                    kind = if (state.isRunning) ButtonKind.GEFAHR else ButtonKind.SEKUNDAER,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.isRunning && state.currentExercise != null) {
                ExerciseArea(
                    state = TrainingState<Nothing>(
                        phase = Phase.COUNTING,
                        exercise = state.currentExercise,
                        pose = state.pose,
                        barStyle = state.barStyle,
                        bars = state.bars,
                        showKeypoints = true
                    ),
                    image = image,
                    onDemoPlayed = {},
                    progressText = state.current,
                    modifier = Modifier.weight(0.7f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ResultRow(result: RecordingResultUi, modifier: Modifier = Modifier) {
    val verdict = when (result.verdict) {
        Verdict.OK -> stringResource(R.string.recording_ok)
        Verdict.DEVIATES -> stringResource(R.string.recording_deviates)
        Verdict.NO_TRUTH -> stringResource(R.string.recording_no_truth)
        Verdict.UNREADABLE -> stringResource(R.string.recording_unreadable)
    }
    val sides = if (Side.LEFT in result.counter) {
        " (" + stringResource(R.string.recording_side_counts, result.counter.getValue(Side.LEFT), result.counter.getValue(Side.RIGHT)) + ")"
    } else {
        ""
    }
    Column(modifier = modifier) {
        Text(
            text = "${result.name}: $verdict",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (result.verdict == Verdict.OK) DigiMobTheme.colors.on else MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.recording_counts, "${result.total}$sides", result.expectedReps?.toString() ?: "?"),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private val previewResults = listOf(
    RecordingResultUi("abduktion/rec1", 3, mapOf(Side.LEFT to 3, Side.RIGHT to 3), 3, null, Verdict.OK),
    RecordingResultUi("bizepscurls/rec5", 2, mapOf(Side.LEFT to 3, Side.RIGHT to 2), 3, null, Verdict.DEVIATES),
    RecordingResultUi("butterfly/rec1", 4, mapOf(Side.BOTH to 4), null, null, Verdict.NO_TRUTH)
)

@Preview(name = "Fertig", widthDp = 1280, heightDp = 800)
@Preview(name = "Fertig dunkel", widthDp = 1280, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RecordingTestScreenDonePreview() {
    DigiMobTheme {
        RecordingTestScreen(
            state = RecordingTestState<Nothing>(isRunning = false, count = 3, results = previewResults, problems = listOf("recordings/x/rec1/video.mp4: Datei nicht gefunden")),
            image = null,
            onStopClick = {}
        )
    }
}

@Preview(name = "Laeuft", widthDp = 1280, heightDp = 800)
@Composable
private fun RecordingTestScreenRunningPreview() {
    DigiMobTheme {
        RecordingTestScreen(
            state = RecordingTestState<Nothing>(
                current = "abduktion/rec2",
                currentExercise = de.mhh.digimob.core.domain.exercise.ExerciseId.ABDUKTION,
                index = 1,
                count = 3,
                results = previewResults.take(1)
            ),
            image = null,
            onStopClick = {}
        )
    }
}

@Preview(name = "videomodus.txt fehlt", widthDp = 1280, heightDp = 800)
@Composable
private fun RecordingTestScreenErrorPreview() {
    DigiMobTheme {
        RecordingTestScreen(
            state = RecordingTestState<Nothing>(isRunning = false, error = UiText.DynamicString("videomodus.txt fehlt")),
            image = null,
            onStopClick = {}
        )
    }
}
