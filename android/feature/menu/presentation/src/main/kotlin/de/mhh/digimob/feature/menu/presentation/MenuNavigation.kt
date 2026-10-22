package de.mhh.digimob.feature.menu.presentation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.presentation.dropUnlessResumed
import de.mhh.digimob.feature.menu.presentation.main.MainMenuScreen
import de.mhh.digimob.feature.menu.presentation.optype.OpTypeRoot
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionRoot

fun NavGraphBuilder.menuGraph(
    navController: NavController,
    showRecording: Boolean,
    onStartTraining: (List<ExerciseId>, target: Int) -> Unit,
    onRecordClick: () -> Unit,
    onQuit: () -> Unit
) {
    // dropUnlessResumed: ein Doppeltipp navigiert nur einmal (ein zweites Zurueck liesse sonst eine
    // leere Seite, ein zweites Starten zwei Uebungsseiten)
    navigation<MenuGraphRoute>(startDestination = MainMenuRoute) {
        composable<MainMenuRoute> {
            MainMenuScreen(
                showRecording = showRecording,
                onChooseExercisesClick = dropUnlessResumed { navController.navigate(OpTypeRoute) },
                onRecordClick = dropUnlessResumed(block = onRecordClick),
                onQuitClick = onQuit
            )
        }
        composable<OpTypeRoute> {
            OpTypeRoot(
                onOpTypeClick = dropUnlessResumed { opType: OpType, target: Int ->
                    navController.navigate(ExerciseSelectionRoute(opType = opType.name, target = target))
                },
                onBackClick = dropUnlessResumed { navController.popBackStack() }
            )
        }
        composable<ExerciseSelectionRoute> { backStackEntry ->
            val route: ExerciseSelectionRoute = backStackEntry.toRoute()
            ExerciseSelectionRoot(
                opType = OpType.valueOf(route.opType),
                target = route.target,
                onStartTraining = dropUnlessResumed(onStartTraining),
                onBackClick = dropUnlessResumed { navController.popBackStack() }
            )
        }
    }
}
