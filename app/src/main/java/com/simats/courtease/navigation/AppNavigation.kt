package com.simats.courtease.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simats.courtease.data.Session
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.Role
import com.simats.courtease.model.User
import com.simats.courtease.ui.screens.AccountRequestsScreen
import com.simats.courtease.ui.screens.AllocationScreen
import com.simats.courtease.ui.screens.AuditLogScreen
import com.simats.courtease.ui.screens.CaseFormScreen
import com.simats.courtease.ui.screens.ComingSoonScreen
import com.simats.courtease.ui.screens.DirectoryScreen
import com.simats.courtease.ui.screens.DocumentsScreen
import com.simats.courtease.ui.screens.FilingQueueScreen
import com.simats.courtease.ui.screens.ForgotPasswordScreen
import com.simats.courtease.ui.screens.JoinCaseScreen
import com.simats.courtease.ui.screens.LawyerCaseScreen
import com.simats.courtease.ui.screens.LawyerDashboardScreen
import com.simats.courtease.ui.screens.LoginScreen
import com.simats.courtease.ui.screens.NoticesScreen
import com.simats.courtease.ui.screens.RegistryDashboardScreen
import com.simats.courtease.ui.screens.ScrutinyScreen
import com.simats.courtease.ui.screens.SignupScreen
import com.simats.courtease.ui.screens.SplashScreen
import com.simats.courtease.ui.screens.StatusScreen
import com.simats.courtease.ui.screens.TeamScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val FORGOT = "forgot"
    const val STATUS = "status"

    const val REGISTRY = "registry"
    const val ACCOUNTS = "accounts"
    const val DIRECTORY = "directory"
    const val QUEUE = "queue"
    const val SCRUTINY = "scrutiny"
    const val ALLOCATION = "allocation"
    const val NOTICES = "notices"
    const val AUDIT = "audit"

    const val LAWYER = "lawyer"
    const val LCASE = "lcase"
    const val LFORM = "lform"
    const val LDOCS = "ldocs"
    const val LTEAM = "lteam"
    const val LJOIN = "ljoin"

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

private val idArg = listOf(navArgument("id") { type = NavType.IntType })

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

        // ---------- registry ----------
        composable(Routes.REGISTRY) {
            RegistryDashboardScreen(
                onAccounts = { nav.navigate(Routes.ACCOUNTS) },
                onDirectory = { nav.navigate(Routes.DIRECTORY) },
                onSignOut = signOut,
                onQueue = { nav.navigate(Routes.QUEUE) },
                onAllocation = { nav.navigate(Routes.ALLOCATION) },
                onNotices = { nav.navigate(Routes.NOTICES) },
                onAudit = { nav.navigate(Routes.AUDIT) }
            )
        }
        composable(Routes.ACCOUNTS) { AccountRequestsScreen(onBack = back) }
        composable(Routes.DIRECTORY) { DirectoryScreen(onBack = back) }
        composable(Routes.QUEUE) {
            FilingQueueScreen(onBack = back, onOpen = { id -> nav.navigate("${Routes.SCRUTINY}/$id") })
        }
        composable("${Routes.SCRUTINY}/{id}", arguments = idArg) { entry ->
            ScrutinyScreen(caseId = entry.arguments?.getInt("id") ?: 0, onBack = back)
        }
        composable(Routes.ALLOCATION) { AllocationScreen(onBack = back) }
        composable(Routes.NOTICES) { NoticesScreen(onBack = back) }
        composable(Routes.AUDIT) { AuditLogScreen(onBack = back) }

        // ---------- lawyer ----------
        composable(Routes.LAWYER) {
            LawyerDashboardScreen(
                onOpenCase = { id -> nav.navigate("${Routes.LCASE}/$id") },
                onFileCase = { nav.navigate("${Routes.LFORM}/0") },
                onJoinCase = { nav.navigate(Routes.LJOIN) },
                onSignOut = signOut
            )
        }

        composable("${Routes.LCASE}/{id}", arguments = idArg) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            LawyerCaseScreen(
                caseId = id,
                onBack = back,
                onEdit = { nav.navigate("${Routes.LFORM}/$id") },
                onDocs = { nav.navigate("${Routes.LDOCS}/$id") },
                onTeam = { nav.navigate("${Routes.LTEAM}/$id") },
                onDeleted = back
            )
        }

        composable("${Routes.LFORM}/{id}", arguments = idArg) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            CaseFormScreen(
                caseId = id,
                onBack = back,
                onSaved = { savedId ->
                    if (id == 0) {
                        // New case: open its workspace and drop the form from the back stack.
                        nav.navigate("${Routes.LCASE}/$savedId") {
                            popUpTo("${Routes.LFORM}/0") { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                }
            )
        }

        composable("${Routes.LDOCS}/{id}", arguments = idArg) { entry ->
            DocumentsScreen(caseId = entry.arguments?.getInt("id") ?: 0, onBack = back)
        }

        composable("${Routes.LTEAM}/{id}", arguments = idArg) { entry ->
            TeamScreen(caseId = entry.arguments?.getInt("id") ?: 0, onBack = back)
        }

        composable(Routes.LJOIN) {
            JoinCaseScreen(
                onBack = back,
                onJoined = { id ->
                    nav.navigate("${Routes.LCASE}/$id") {
                        popUpTo(Routes.LJOIN) { inclusive = true }
                    }
                }
            )
        }

        // Built in part F4.
        composable(Routes.JUDGE) { ComingSoonScreen(onSignOut = signOut) }
    }
}