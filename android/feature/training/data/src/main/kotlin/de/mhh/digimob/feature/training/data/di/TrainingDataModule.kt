package de.mhh.digimob.feature.training.data.di

import android.graphics.Bitmap
import de.mhh.digimob.feature.training.data.camera.CameraXPoseSource
import de.mhh.digimob.feature.training.data.recording.FileRecordingDataSource
import de.mhh.digimob.feature.training.data.speech.AndroidSpeaker
import de.mhh.digimob.feature.training.data.time.ElapsedRealtimeClock
import de.mhh.digimob.feature.training.data.video.VideoPoseSource
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.LivePoseSource
import de.mhh.digimob.feature.training.domain.RecordingPoseSourceFactory
import de.mhh.digimob.feature.training.domain.RecordingRepository
import de.mhh.digimob.feature.training.domain.Speaker
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val trainingDataModule = module {
    singleOf(::AndroidSpeaker) { bind<Speaker>() }
    singleOf(::ElapsedRealtimeClock) { bind<Clock>() }
    single<LivePoseSource<Bitmap>> { CameraXPoseSource(androidContext()) }
    single<RecordingPoseSourceFactory<Bitmap>> {
        val context = androidContext()
        RecordingPoseSourceFactory { path -> VideoPoseSource(context, path) }
    }
    // Lambda: der Ordner kommt aus dem Context, kein Konstruktorparameter von Koin
    single<RecordingRepository> {
        FileRecordingDataSource(requireNotNull(androidContext().getExternalFilesDir(null)) { "Kein App-Speicher" })
    }
}
