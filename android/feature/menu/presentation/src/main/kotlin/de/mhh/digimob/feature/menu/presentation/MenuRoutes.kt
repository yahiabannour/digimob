package de.mhh.digimob.feature.menu.presentation

import kotlinx.serialization.Serializable

@Serializable data object MenuGraphRoute
@Serializable data object MainMenuRoute
@Serializable data object OpTypeRoute
@Serializable data class ExerciseSelectionRoute(val opType: String, val target: Int)
