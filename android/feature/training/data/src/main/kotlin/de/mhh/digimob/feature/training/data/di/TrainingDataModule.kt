package de.mhh.digimob.feature.training.data.di

import android.graphics.Bitmap
import android.os.Build
import de.mhh.digimob.feature.training.data.camera.CameraXPoseSource
import de.mhh.digimob.feature.training.data.camera.CameraXRecordingCamera
import de.mhh.digimob.feature.training.data.recording.FileRecordingRepository
import de.mhh.digimob.feature.training.data.speech.AndroidSpeaker
import de.mhh.digimob.feature.training.data.time.ElapsedRealtimeClock
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.LivePoseSource
import de.mhh.digimob.feature.training.domain.RecordingCamera
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
    single<RecordingCamera<Bitmap>> { CameraXRecordingCamera(androidContext()) }
    // Lambda: Ordner und Geraetename kommen aus dem System, keine Konstruktorparameter von Koin
    single<RecordingRepository> {
        FileRecordingRepository(
            root = requireNotNull(androidContext().getExternalFilesDir("recordings")) { "Kein App-Speicher" },
            cameraName = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
}
