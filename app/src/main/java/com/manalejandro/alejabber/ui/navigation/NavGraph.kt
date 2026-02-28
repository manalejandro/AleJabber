package com.manalejandro.alejabber.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.manalejandro.alejabber.ui.accounts.AccountsScreen
import com.manalejandro.alejabber.ui.accounts.AddEditAccountScreen
import com.manalejandro.alejabber.ui.chat.ChatScreen
import com.manalejandro.alejabber.ui.contacts.ContactsScreen
import com.manalejandro.alejabber.ui.rooms.RoomsScreen
import com.manalejandro.alejabber.ui.settings.SettingsScreen

/** All navigable destinations in the app. */
sealed class Screen(val route: String) {
    /** Account list — the app home screen. */
    object Accounts : Screen("accounts")

    /** Add a new XMPP account. */
    object AddAccount : Screen("add_account")

    /** Edit an existing account by its database id. */
    object EditAccount : Screen("edit_account/{accountId}") {
        fun createRoute(accountId: Long) = "edit_account/$accountId"
    }

    /**
     * Contact list for a specific account.
     * Navigated to after the user taps a connected account.
     */
    object Contacts : Screen("contacts/{accountId}") {
        fun createRoute(accountId: Long) = "contacts/$accountId"
    }

    /** MUC room list — accessible via bottom nav. */
    object Rooms : Screen("rooms")

    /**
     * Chat screen for a 1-to-1 conversation or a MUC room.
     *
     * @param accountId  The local account used to send messages.
     * @param jid        The bare JID of the contact or room.
     * @param isRoom     True when [jid] represents a MUC room.
     */
    object Chat : Screen("chat/{accountId}/{jid}/{isRoom}") {
        fun createRoute(accountId: Long, jid: String, isRoom: Boolean = false) =
            "chat/$accountId/${jid.replace("/", "%2F")}/$isRoom"
    }

    /** Application settings. */
    object Settings : Screen("settings")
}

@Composable
fun AleJabberNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Accounts.route
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(280))
        },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(280))
        }
    ) {
        // ── Accounts ─────────────────────────────────────────────────────────
        composable(Screen.Accounts.route) {
            AccountsScreen(
                onAddAccount    = { navController.navigate(Screen.AddAccount.route) },
                onEditAccount   = { id -> navController.navigate(Screen.EditAccount.createRoute(id)) },
                onOpenContacts  = { accountId ->
                    navController.navigate(Screen.Contacts.createRoute(accountId))
                }
            )
        }

        // ── Add account ───────────────────────────────────────────────────────
        composable(Screen.AddAccount.route) {
            AddEditAccountScreen(
                accountId      = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Edit account ──────────────────────────────────────────────────────
        composable(
            route     = Screen.EditAccount.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { back ->
            AddEditAccountScreen(
                accountId      = back.arguments?.getLong("accountId"),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Contacts for one account ──────────────────────────────────────────
        composable(
            route     = Screen.Contacts.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { back ->
            val accountId = back.arguments!!.getLong("accountId")
            ContactsScreen(
                accountId       = accountId,
                onNavigateToChat = { accId, jid ->
                    navController.navigate(Screen.Chat.createRoute(accId, jid))
                },
                onNavigateBack  = { navController.popBackStack() }
            )
        }

        // ── Rooms ─────────────────────────────────────────────────────────────
        composable(Screen.Rooms.route) {
            RoomsScreen(
                onNavigateToRoom = { accountId, jid ->
                    navController.navigate(Screen.Chat.createRoute(accountId, jid, isRoom = true))
                }
            )
        }

        // ── Chat ──────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Chat.route,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType },
                navArgument("jid")       { type = NavType.StringType },
                navArgument("isRoom")    { type = NavType.BoolType }
            )
        ) { back ->
            ChatScreen(
                accountId       = back.arguments!!.getLong("accountId"),
                conversationJid = back.arguments!!.getString("jid")!!,
                isRoom          = back.arguments!!.getBoolean("isRoom"),
                onNavigateBack  = { navController.popBackStack() }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

