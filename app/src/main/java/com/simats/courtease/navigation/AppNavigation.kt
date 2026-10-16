package com.simats.courtease.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simats.courtease.ui.screens.CaseProfileScreen
import com.simats.courtease.ui.screens.CreateCaseScreen
import com.simats.courtease.ui.screens.DashboardScreen
import com.simats.courtease.ui.screens.LoginScreen
import com.simats.courtease.ui.screens.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard/{role}"
    const val CASE = "case/{caseNo}"
    const val CREATE = "create"

    fun dashboard(role: String) = "dashboard/$role"
    fun case(caseNo: String) = "case/$caseNo"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen {
                nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = { role ->
                    nav.navigate(Routes.dashboard(role.name)) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Routes.DASHBOARD,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { entry ->
            DashboardScreen(
                role = entry.arguments?.getString("role") ?: "LAWYER",
                onOpenCase = { nav.navigate(Routes.case(it)) },
                onCreateCase = { nav.navigate(Routes.CREATE) },
                onLogout = { nav.navigate(Routes.LOGIN) { popUpTo(0) } },
            )
        }

        composable(
            Routes.CASE,
            arguments = listOf(navArgument("caseNo") { type = NavType.StringType })
        ) { entry ->
            CaseProfileScreen(
                caseNo = entry.arguments?.getString("caseNo") ?: "",
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack() },
            )
        }

        composable(Routes.CREATE) {
            CreateCaseScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
    }
}
