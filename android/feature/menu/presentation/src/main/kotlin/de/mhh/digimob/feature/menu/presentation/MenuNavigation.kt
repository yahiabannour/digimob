package de.mhh.digimob.feature.menu.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.feature.menu.presentation.main.MainMenuScreen
import de.mhh.digimob.feature.menu.presentation.optype.OpTypeRoot
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionRoot

fun NavGraphBuilder.menuGraph(
    navController: NavController,
    showRecordingTest: Boolean,
    onStartTraining: (List<ExerciseId>, target: Int) -> Unit,
    onRecordingTest: () -> Unit,
    onQuit: () -> Unit
) {
    navigation<MenuGraphRoute>(startDestination = MainMenuRoute) {
        composable<MainMenuRoute> {
            MainMenuScreen(
                showRecordingTest = showRecordingTest,
                onChooseExercisesClick = { navController.navigate(OpTypeRoute) },
                onRecordingTestClick = onRecordingTest,
                onQuitClick = onQuit
            )
        }
        composable<OpTypeRoute> {
            OpTypeRoot(
                onOpTypeClick = { opType, target ->
                    navController.navigate(ExerciseSelectionRoute(opType = opType.name, target = target))
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable<ExerciseSelectionRoute> { backStackEntry ->
            val route: ExerciseSelectionRoute = backStackEntry.toRoute()
            ExerciseSelectionRoot(
                opType = OpType.valueOf(route.opType),
                target = route.target,
                onStartTraining = onStartTraining,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
