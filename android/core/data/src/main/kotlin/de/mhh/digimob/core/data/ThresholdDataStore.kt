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
fun createThresholdDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            Logger.withTag("Schwellen").e(e) { "Schwellen-Datei kaputt, es gilt Voll" }
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile
    )

// Dateiname von frueher (nur Presets): ein gespeichertes "einfach" bleibt nach dem Update erhalten
fun createThresholdDataStore(context: Context): DataStore<Preferences> =
    createThresholdDataStore(produceFile = { context.preferencesDataStoreFile("presets") })
