package de.mhh.digimob.feature.menu.presentation.optype

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mhh.digimob.core.designsystem.components.ButtonKind
import de.mhh.digimob.core.designsystem.components.CenteredColumn
import de.mhh.digimob.core.designsystem.components.DigiMobButton
import de.mhh.digimob.core.designsystem.components.PageScaffold
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.PostOpDay
import de.mhh.digimob.core.presentation.labelRes
import de.mhh.digimob.feature.menu.presentation.R
import org.koin.compose.viewmodel.koinViewModel
import de.mhh.digimob.core.presentation.R as CoreR

@Composable
fun OpTypeRoot(
    onOpTypeClick: (OpType, target: Int) -> Unit,
    onBackClick: () -> Unit,
    viewModel: OpTypeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OpTypeScreen(state = state, onAction = viewModel::onAction, onOpTypeClick = onOpTypeClick, onBackClick = onBackClick)
}

@Composable
fun OpTypeScreen(
    state: OpTypeState,
    onAction: (OpTypeAction) -> Unit,
    onOpTypeClick: (OpType, target: Int) -> Unit,
    onBackClick: () -> Unit
) {
    PageScaffold(title = stringResource(R.string.op_type_title)) {
        CenteredColumn {
            OpType.entries.forEach { opType ->
                DigiMobButton(
                    text = stringResource(opType.labelRes),
                    onClick = { onOpTypeClick(opType, state.day.repetitions) },
                    kind = ButtonKind.PRIMAER
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = stringResource(R.string.post_op_day), style = MaterialTheme.typography.bodyLarge)
            Box {
                DigiMobButton(
                    text = stringResource(state.day.labelRes) + " · " +
                        pluralStringResource(R.plurals.repetitions, state.day.repetitions, state.day.repetitions),
                    onClick = { onAction(OpTypeAction.OnDayMenuClick) }
                )
                DropdownMenu(
                    expanded = state.isDayMenuExpanded,
                    onDismissRequest = { onAction(OpTypeAction.OnDayMenuDismiss) }
                ) {
                    PostOpDay.entries.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(day.labelRes), style = MaterialTheme.typography.titleLarge) },
                            onClick = { onAction(OpTypeAction.OnDaySelect(day)) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            DigiMobButton(text = stringResource(CoreR.string.back), onClick = onBackClick)
        }
    }
}

@Preview(name = "Tag 1", widthDp = 1280, heightDp = 800)
@Preview(name = "Tag 1 dunkel", widthDp = 1280, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OpTypeScreenPreview() {
    DigiMobTheme {
        OpTypeScreen(state = OpTypeState(), onAction = {}, onOpTypeClick = { _, _ -> }, onBackClick = {})
    }
}

@Preview(name = "Menue offen", widthDp = 1280, heightDp = 800)
@Composable
private fun OpTypeScreenMenuPreview() {
    DigiMobTheme {
        OpTypeScreen(
            state = OpTypeState(day = PostOpDay.AB_TAG_5, isDayMenuExpanded = true),
            onAction = {},
            onOpTypeClick = { _, _ -> },
            onBackClick = {}
        )
    }
}
