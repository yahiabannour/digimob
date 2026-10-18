package de.mhh.digimob.core.designsystem.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.R
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/** Eine Seite der App: Kopfleiste mit Titel und "DigiMob · Reha-Übungen", darunter der Inhalt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DigiMobTheme.colors.onHeaderVariant,
                        modifier = Modifier.padding(end = 24.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DigiMobTheme.colors.header,
                    titleContentColor = DigiMobTheme.colors.onHeader
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

@Preview(name = "Seite", widthDp = 1280, heightDp = 400)
@Preview(name = "Seite dunkel", widthDp = 1280, heightDp = 400, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PageScaffoldPreview() {
    DigiMobTheme {
        PageScaffold(title = "Hauptmenü") {
            Text("Inhalt", modifier = Modifier.padding(24.dp))
        }
    }
}

@Preview(name = "Langer Titel, schmal", widthDp = 600, heightDp = 200)
@Composable
private fun PageScaffoldLongTitlePreview() {
    DigiMobTheme {
        PageScaffold(title = "Nach Bauch-OP: alle Übungen nacheinander") {}
    }
}
