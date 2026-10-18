package de.mhh.digimob.feature.menu.presentation.di

import de.mhh.digimob.feature.menu.presentation.optype.OpTypeViewModel
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val menuPresentationModule = module {
    viewModelOf(::OpTypeViewModel)
    viewModelOf(::ExerciseSelectionViewModel)   // opType und target per parametersOf()
}
