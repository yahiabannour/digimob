package de.mhh.digimob.core.data.di

import de.mhh.digimob.core.data.DataStoreThresholdRepository
import de.mhh.digimob.core.data.createThresholdDataStore
import de.mhh.digimob.core.domain.exercise.ThresholdRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreDataModule = module {
    // Lambda statt singleOf: Fabrikfunktion, kein Konstruktor
    single { createThresholdDataStore(androidContext()) }
    singleOf(::DataStoreThresholdRepository) { bind<ThresholdRepository>() }
}
