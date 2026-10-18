package de.mhh.digimob.feature.training.presentation.di

import android.graphics.Bitmap
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.feature.training.presentation.recording.RecordingTestViewModel
import de.mhh.digimob.feature.training.presentation.training.TrainingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Lambda statt viewModelOf: die ViewModels sind generisch (Bitmap), viewModelOf kennt keine Typparameter
val trainingPresentationModule = module {
    viewModel { (exerciseIds: List<ExerciseId>, target: Int) ->
        TrainingViewModel<Bitmap>(exerciseIds, target, get(), get(), get(), get())
    }
    viewModel { RecordingTestViewModel<Bitmap>(get(), get(), get()) }
}
