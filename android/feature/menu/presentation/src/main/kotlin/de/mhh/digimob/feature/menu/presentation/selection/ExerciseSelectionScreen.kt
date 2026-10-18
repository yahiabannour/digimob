package de.mhh.digimob.feature.menu.presentation.selection

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mhh.digimob.core.designsystem.components.ButtonKind
import de.mhh.digimob.core.designsystem.components.DigiMobButton
import de.mhh.digimob.core.designsystem.components.DigiMobSegmentedToggle
import de.mhh.digimob.core.designsystem.components.DigiMobSelectButton
import de.mhh.digimob.core.designsystem.components.PageScaffold
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.core.presentation.labelRes
import de.mhh.digimob.core.presentation.titleRes
import de.mhh.digimob.feature.menu.presentation.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import de.mhh.digimob.core.presentation.R as CoreR

@Composable
fun ExerciseSelectionRoot(
    opType: OpType,
    target: Int,
    onStartTraining: (List<ExerciseId>, target: Int) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ExerciseSelectionViewModel = koinViewModel { parametersOf(opType, target) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExerciseSelectionScreen(
        state = state,
        onAction = viewModel::onAction,
        onStartTraining = onStartTraining,
        onBackClick = onBackClick
    )
}

@Composable
fun ExerciseSelectionScreen(
    state: ExerciseSelectionState,
    onAction: (ExerciseSelectionAction) -> Unit,
    onStartTraining: (List<ExerciseId>, target: Int) -> Unit,
    onBackClick: () -> Unit
) {
    PageScaffold(title = stringResource(R.string.selection_title, stringResource(state.opType.labelRes))) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(text = stringResource(R.string.selection_hint), style = MaterialTheme.typography.bodyLarge)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.rows, key = { it.id }) { row ->
                    ExerciseRow(
                        row = row,
                        onExerciseClick = { onAction(ExerciseSelectionAction.OnExerciseClick(row.id)) },
                        onPresetSelect = { onAction(ExerciseSelectionAction.OnPresetSelect(row.id, it)) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DigiMobButton(
                    text = stringResource(R.string.start_selected),
                    onClick = { onStartTraining(state.selectedIds, state.target) },
                    kind = ButtonKind.PRIMAER,
                    enabled = state.selectedIds.isNotEmpty()
                )
                DigiMobButton(
                    text = stringResource(R.string.start_all),
                    onClick = { onStartTraining(state.allIds, state.target) },
                    kind = ButtonKind.PRIMAER
                )
                DigiMobButton(text = stringResource(CoreR.string.back), onClick = onBackClick)
            }
        }
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { onAction(ExerciseSelectionAction.OnErrorDismiss) },
            confirmButton = {
                TextButton(onClick = { onAction(ExerciseSelectionAction.OnErrorDismiss) }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(error.asString()) }
        )
    }
}

/** Eine Uebung: Auswahlflaeche, daneben immer ihr Preset (gilt auch, wenn nicht angekreuzt). */
@Composable
private fun ExerciseRow(
    row: ExerciseRowUi,
    onExerciseClick: () -> Unit,
    onPresetSelect: (Preset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DigiMobSelectButton(text = stringResource(row.titleRes), selected = row.isChecked, onClick = onExerciseClick)
        DigiMobSegmentedToggle(
            options = Preset.entries.map { stringResource(it.labelRes) },
            selected = if (row.presetsEqual) Preset.entries.indices.toSet() else setOf(row.preset.ordinal),
            onSelect = { onPresetSelect(Preset.entries[it]) }
        )
        if (row.presetsEqual) {
            Text(
                text = stringResource(CoreR.string.preset_equal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun previewState(opType: OpType, checked: Set<ExerciseId> = emptySet(), error: UiText? = null) =
    ExerciseSelectionState(
        opType = opType,
        target = 5,
        rows = opType.exercises.map {
            ExerciseRowUi(
                id = it,
                titleRes = it.titleRes,
                isChecked = it in checked,
                preset = if (it == ExerciseId.BIZEPSCURLS) Preset.EINFACH else Preset.VOLL,
                presetsEqual = ExerciseCatalog.presetsEqual(it)
            )
        },
        error = error
    )

@Preview(name = "Bauch-OP, nichts gewaehlt", widthDp = 1280, heightDp = 800)
@Preview(name = "Bauch-OP dunkel", widthDp = 1280, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExerciseSelectionScreenEmptyPreview() {
    DigiMobTheme {
        ExerciseSelectionScreen(previewState(OpType.BAUCH_OP), onAction = {}, onStartTraining = { _, _ -> }, onBackClick = {})
    }
}

@Preview(name = "Brust-OP, zwei gewaehlt", widthDp = 1280, heightDp = 800)
@Composable
private fun ExerciseSelectionScreenCheckedPreview() {
    DigiMobTheme {
        ExerciseSelectionScreen(
            previewState(OpType.BRUST_OP, checked = setOf(ExerciseId.BUTTERFLY, ExerciseId.ROTATION)),
            onAction = {},
            onStartTraining = { _, _ -> },
            onBackClick = {}
        )
    }
}

@Preview(name = "Fehler beim Speichern", widthDp = 1280, heightDp = 800)
@Composable
private fun ExerciseSelectionScreenErrorPreview() {
    DigiMobTheme {
        ExerciseSelectionScreen(
            previewState(OpType.BRUST_OP, error = UiText.DynamicString("Der Speicher ist voll.")),
            onAction = {},
            onStartTraining = { _, _ -> },
            onBackClick = {}
        )
    }
}
