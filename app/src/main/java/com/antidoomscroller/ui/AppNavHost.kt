package com.antidoomscroller.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antidoomscroller.ui.screens.AboutScreen
import com.antidoomscroller.ui.screens.AdultFilterScreen
import com.antidoomscroller.ui.screens.AppDetailScreen
import com.antidoomscroller.ui.screens.AppsScreen
import com.antidoomscroller.ui.screens.DashboardScreen
import com.antidoomscroller.ui.screens.DisableFilterScreen
import com.antidoomscroller.ui.screens.MessagesScreen
import com.antidoomscroller.ui.screens.ScheduleScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val APPS = "apps"
    const val APP_DETAIL = "apps/{packageName}"
    const val MESSAGES = "messages"
    const val ADULT_FILTER = "adult-filter"
    const val DISABLE_FILTER = "adult-filter/disable"
    const val SCHEDULE = "schedule"
    const val ABOUT = "about"

    fun appDetail(packageName: String): String = "apps/$packageName"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenApps = { navController.navigate(Routes.APPS) },
                onOpenMessages = { navController.navigate(Routes.MESSAGES) },
                onOpenAdultFilter = { navController.navigate(Routes.ADULT_FILTER) },
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.APPS) {
            AppsScreen(
                onBack = { navController.popBackStack() },
                onOpenApp = { navController.navigate(Routes.appDetail(it)) },
            )
        }
        composable(Routes.APP_DETAIL) { entry ->
            AppDetailScreen(
                packageName = entry.arguments?.getString("packageName").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MESSAGES) {
            MessagesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADULT_FILTER) {
            AdultFilterScreen(
                onBack = { navController.popBackStack() },
                onOpenDisableFlow = { navController.navigate(Routes.DISABLE_FILTER) },
            )
        }
        composable(Routes.DISABLE_FILTER) {
            DisableFilterScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SCHEDULE) {
            ScheduleScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
