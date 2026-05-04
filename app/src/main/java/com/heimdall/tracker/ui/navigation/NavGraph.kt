package com.heimdall.tracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.heimdall.tracker.ui.screens.AboutScreen
import com.heimdall.tracker.ui.screens.HistoryScreen
import com.heimdall.tracker.ui.screens.RunDetailScreen
import com.heimdall.tracker.ui.screens.SettingsScreen
import com.heimdall.tracker.ui.screens.TrackingScreen
import com.heimdall.tracker.ui.viewmodel.TrackingViewModel

sealed class Screen(val route: String) {
    data object Tracking : Screen("tracking")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object RunDetail : Screen("run_detail/{runId}") {
        fun createRoute(runId: Long) = "run_detail/$runId"
    }
}

@Composable
fun HeimdallNavGraph(
    navController: NavHostController,
    viewModel: TrackingViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Tracking.route
    ) {
        composable(Screen.Tracking.route) {
            TrackingScreen(viewModel = viewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = viewModel,
                onRunClick = { runId ->
                    navController.navigate(Screen.RunDetail.createRoute(runId))
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = viewModel)
        }
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.RunDetail.route,
            arguments = listOf(navArgument("runId") { type = NavType.LongType })
        ) { backStackEntry ->
            val runId = backStackEntry.arguments?.getLong("runId") ?: return@composable
            RunDetailScreen(
                runId = runId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
