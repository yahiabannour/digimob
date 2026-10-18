package de.mhh.digimob

import android.app.Application
import de.mhh.digimob.core.data.di.coreDataModule
import de.mhh.digimob.feature.menu.presentation.di.menuPresentationModule
import de.mhh.digimob.feature.training.data.di.trainingDataModule
import de.mhh.digimob.feature.training.presentation.di.trainingPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DigiMobApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DigiMobApp)
            modules(
                // core
                coreDataModule,
                // features
                menuPresentationModule,
                trainingDataModule,
                trainingPresentationModule
            )
        }
    }
}
