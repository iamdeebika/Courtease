package com.simats.courtease.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.courtease.ui.screens.CaseProfileScreen
import com.example.courtease.ui.screens.DashboardScreen
import com.example.courtease.ui.screens.LoginScreen
import com.example.courtease.ui.screens.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard/{role}"
    const val CASE = "case/{caseNo}"

    fun dashboard(role: String) = "dashboard/$role"
    fun case(caseNo: String) = "case/$caseNo"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(onLogin = { role ->
                nav.navigate(Routes.dashboard(role.name)) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(
            Routes.DASHBOARD,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { entry ->
            DashboardScreen(
                role = entry.arguments?.getString("role") ?: "LAWYER",
                onOpenCase = { nav.navigate(Routes.case(it)) },
                onLogout = {
                    nav.navigate(Routes.LOGIN) { popUpTo(0) }
                }
            )
        }

        composable(
            Routes.CASE,
            arguments = listOf(navArgument("caseNo") { type = NavType.StringType })
        ) { entry ->
            CaseProfileScreen(
                caseNo = entry.arguments?.getString("caseNo") ?: "",
                onBack = { nav.popBackStack() }
            )
        }
    }
}