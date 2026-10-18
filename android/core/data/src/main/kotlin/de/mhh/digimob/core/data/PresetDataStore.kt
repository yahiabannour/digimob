package de.mhh.digimob.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Kaputte Datei: neu anfangen, es gilt fuer alle Uebungen Voll (wie eine kaputte schwellen.json). */
fun createPresetDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            Logger.withTag("Presets").e(e) { "Preset-Datei kaputt, es gilt Voll" }
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile
    )

fun createPresetDataStore(context: Context): DataStore<Preferences> =
    createPresetDataStore(produceFile = { context.preferencesDataStoreFile("presets") })
