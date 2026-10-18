package de.mhh.digimob.feature.menu.presentation.main

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.components.ButtonKind
import de.mhh.digimob.core.designsystem.components.CenteredColumn
import de.mhh.digimob.core.designsystem.components.DigiMobButton
import de.mhh.digimob.core.designsystem.components.PageScaffold
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.feature.menu.presentation.R

/**
 * Hauptmenue. Kein ViewModel: jeder Knopf navigiert nur. "Aufnahmen testen" nur in der
 * Entwickler-Version (Debug-Build), im Krankenhaus gibt es die Aufnahmen-Videos nicht.
 */
@Composable
fun MainMenuScreen(
    showRecordingTest: Boolean,
    onChooseExercisesClick: () -> Unit,
    onRecordingTestClick: () -> Unit,
    onQuitClick: () -> Unit
) {
    PageScaffold(title = stringResource(R.string.main_menu_title)) {
        CenteredColumn {
            DigiMobButton(
                text = stringResource(R.string.choose_exercises),
                onClick = onChooseExercisesClick,
                kind = ButtonKind.PRIMAER
            )
            if (showRecordingTest) {
                DigiMobButton(text = stringResource(R.string.recording_test), onClick = onRecordingTestClick)
            }
            Spacer(modifier = Modifier.height(32.dp))
            DigiMobButton(text = stringResource(R.string.quit), onClick = onQuitClick, kind = ButtonKind.GEFAHR)
        }
    }
}

@Preview(name = "Klinik", widthDp = 1280, heightDp = 800)
@Preview(name = "Klinik dunkel", widthDp = 1280, heightDp = 800, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MainMenuScreenPreview() {
    DigiMobTheme {
        MainMenuScreen(showRecordingTest = false, onChooseExercisesClick = {}, onRecordingTestClick = {}, onQuitClick = {})
    }
}

@Preview(name = "Entwickler", widthDp = 1280, heightDp = 800)
@Composable
private fun MainMenuScreenDeveloperPreview() {
    DigiMobTheme {
        MainMenuScreen(showRecordingTest = true, onChooseExercisesClick = {}, onRecordingTestClick = {}, onQuitClick = {})
    }
}
