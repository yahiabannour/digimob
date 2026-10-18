package de.mhh.digimob.core.data.di

import de.mhh.digimob.core.data.DataStorePresetRepository
import de.mhh.digimob.core.data.createPresetDataStore
import de.mhh.digimob.core.domain.exercise.PresetRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreDataModule = module {
    // Lambda statt singleOf: Fabrikfunktion, kein Konstruktor
    single { createPresetDataStore(androidContext()) }
    singleOf(::DataStorePresetRepository) { bind<PresetRepository>() }
}
