package com.simats.courtease.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simats.courtease.data.Session
import com.simats.courtease.ui.screens.CaseWorkspaceScreen
import com.simats.courtease.ui.screens.CasesScreen
import com.simats.courtease.ui.screens.CreateCaseScreen
import com.simats.courtease.ui.screens.DashboardScreen
import com.simats.courtease.ui.screens.ForgotPasswordScreen
import com.simats.courtease.ui.screens.LoginScreen
import com.simats.courtease.ui.screens.NotificationsScreen
import com.simats.courtease.ui.screens.SignupScreen
import com.simats.courtease.ui.screens.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val FORGOT = "forgot"
    const val DASHBOARD = "dashboard"
    const val CASES = "cases"
    const val CREATE = "create"
    const val NOTIFICATIONS = "notifications"
    const val CASE = "case"
    const val EDIT = "edit"
    const val DOCS = "docs"
    const val ANALYSIS = "analysis"
    const val ISSUES = "issues"
    const val PRECEDENT = "precedent"
    const val HEARING = "hearing"
    const val VERIFY = "verify"
}

private fun NavHostController.go(prefix: String, caseNo: String) =
    navigate("$prefix/${Uri.encode(caseNo)}")

private fun NavGraphBuilder.caseRoute(prefix: String, content: @Composable (String) -> Unit) {
    composable(
        "$prefix/{caseNo}",
        arguments = listOf(navArgument("caseNo") { type = NavType.StringType })
    ) { entry -> content(entry.arguments?.getString("caseNo") ?: "") }
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                val next = if (Session.isLoggedIn) Routes.DASHBOARD else Routes.LOGIN
                nav.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onSignup = { nav.navigate(Routes.SIGNUP) },
                onForgot = { nav.navigate(Routes.FORGOT) }
            )
        }

        composable(Routes.SIGNUP) { SignupScreen(onBack = back, onDone = back) }

        composable(Routes.FORGOT) { ForgotPasswordScreen(onBack = back, onDone = back) }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenCase = { nav.go(Routes.CASE, it) },
                onViewAll = { nav.navigate(Routes.CASES) },
                onCreate = { nav.navigate(Routes.CREATE) },
                onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                onLogout = {
                    Session.clear(context)
                    nav.navigate(Routes.LOGIN) { popUpTo(0) }
                }
            )
        }

        composable(Routes.CASES) {
            CasesScreen(
                onBack = back,
                onOpenCase = { nav.go(Routes.CASE, it) },
                onCreate = { nav.navigate(Routes.CREATE) }
            )
        }

        composable(Routes.CREATE) {
            CreateCaseScreen(caseNo = null, onBack = back, onDone = back)
        }

        composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = back) }

        caseRoute(Routes.EDIT) { no -> CreateCaseScreen(caseNo = no, onBack = back, onDone = back) }

        caseRoute(Routes.CASE) { no ->
            CaseWorkspaceScreen(
                caseNo = no,
                onBack = back,
                // The tool screens are added in later sprints.
                onDocs = { },
                onAnalysis = { },
                onIssues = { },
                onPrecedent = { },
                onHearing = { },
                onVerify = { },
                onEdit = { nav.go(Routes.EDIT, no) },
                onDeleted = back
            )
        }
    }
}