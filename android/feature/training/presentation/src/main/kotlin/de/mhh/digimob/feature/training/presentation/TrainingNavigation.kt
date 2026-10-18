package de.mhh.digimob.feature.training.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.feature.training.presentation.recording.RecordingTestRoot
import de.mhh.digimob.feature.training.presentation.training.TrainingRoot

fun NavGraphBuilder.trainingGraph(onBackToMenu: () -> Unit) {
    composable<TrainingRoute> { backStackEntry ->
        val route: TrainingRoute = backStackEntry.toRoute()
        TrainingRoot(
            exerciseIds = route.exerciseIds.mapNotNull(ExerciseId::fromKey),
            target = route.target,
            onBackToMenu = onBackToMenu
        )
    }
    composable<RecordingTestRoute> {
        RecordingTestRoot(onBackClick = onBackToMenu)
    }
}
