package de.mhh.digimob.feature.training.presentation

import kotlinx.serialization.Serializable

/** exerciseIds: ExerciseId.key in der Reihenfolge, in der geuebt wird. */
@Serializable data class TrainingRoute(val exerciseIds: List<String>, val target: Int)

/** Aufnehmen mit Ground Truth, nur im Debug-Build. */
@Serializable data object RecordingRoute
