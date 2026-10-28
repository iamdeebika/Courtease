package com.simats.courtease.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simats.courtease.data.Session
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.Role
import com.simats.courtease.model.User
import com.simats.courtease.ui.screens.AccountRequestsScreen
import com.simats.courtease.ui.screens.ComingSoonScreen
import com.simats.courtease.ui.screens.DirectoryScreen
import com.simats.courtease.ui.screens.ForgotPasswordScreen
import com.simats.courtease.ui.screens.LoginScreen
import com.simats.courtease.ui.screens.RegistryDashboardScreen
import com.simats.courtease.ui.screens.SignupScreen
import com.simats.courtease.ui.screens.SplashScreen
import com.simats.courtease.ui.screens.StatusScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val FORGOT = "forgot"
    const val STATUS = "status"
    const val REGISTRY = "registry"
    const val ACCOUNTS = "accounts"
    const val DIRECTORY = "directory"
    const val LAWYER = "lawyer"
    const val JUDGE = "judge"
}

// Where a signed-in user lands.
fun homeRoute(u: User?): String = when {
    u == null -> Routes.LOGIN
    u.status != AccountStatus.APPROVED -> Routes.STATUS
    u.role == Role.REGISTRY -> Routes.REGISTRY
    u.role == Role.JUDGE -> Routes.JUDGE
    else -> Routes.LAWYER
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val back: () -> Unit = { nav.popBackStack() }
    val signOut: () -> Unit = {
        Session.logout(context)
        nav.navigate(Routes.LOGIN) { popUpTo(0) }
    }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                nav.navigate(homeRoute(Session.user)) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { user ->
                    Session.login(context, user.id)
                    nav.navigate(homeRoute(user)) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onSignup = { nav.navigate(Routes.SIGNUP) },
                onForgot = { nav.navigate(Routes.FORGOT) }
            )
        }

        composable(Routes.SIGNUP) { SignupScreen(onBack = back, onDone = back) }

        composable(Routes.FORGOT) { ForgotPasswordScreen(onBack = back, onDone = back) }

        composable(Routes.STATUS) {
            StatusScreen(
                onContinue = {
                    nav.navigate(homeRoute(Session.user)) { popUpTo(Routes.STATUS) { inclusive = true } }
                },
                onReapply = { nav.navigate(Routes.SIGNUP) },
                onSignOut = signOut
            )
        }

        composable(Routes.REGISTRY) {
            RegistryDashboardScreen(
                onAccounts = { nav.navigate(Routes.ACCOUNTS) },
                onDirectory = { nav.navigate(Routes.DIRECTORY) },
                onSignOut = signOut
            )
        }

        composable(Routes.ACCOUNTS) { AccountRequestsScreen(onBack = back) }

        composable(Routes.DIRECTORY) { DirectoryScreen(onBack = back) }

        // Built in parts F3 and F4.
        composable(Routes.LAWYER) { ComingSoonScreen(onSignOut = signOut) }
        composable(Routes.JUDGE) { ComingSoonScreen(onSignOut = signOut) }
    }
}