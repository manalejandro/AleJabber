package com.manalejandro.alejabber

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manalejandro.alejabber.service.XmppForegroundService
import com.manalejandro.alejabber.ui.navigation.AleJabberNavGraph
import com.manalejandro.alejabber.ui.navigation.Screen
import com.manalejandro.alejabber.ui.theme.AleJabberTheme
import com.manalejandro.alejabber.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity entry point.
 *
 * Navigation structure:
 *   Accounts (home) ──► Contacts(accountId) ──► Chat
 *                  ──► AddEditAccount
 *   Rooms ──► Chat
 *   Settings
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startXmppService()
        setContent {
            AleJabberTheme(appTheme = AppTheme.SYSTEM) {
                MainAppContent()
            }
        }
    }

    private fun startXmppService() {
        val intent = Intent(this, XmppForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStack?.destination

    // Bottom nav items: Accounts | Rooms | Settings
    // Contacts is NOT in the bottom nav — it's a drill-down from an account.
    val bottomNavItems = listOf(
        BottomNavItem(
            route        = Screen.Accounts.route,
            labelRes     = R.string.nav_accounts,
            selectedIcon = Icons.Filled.ManageAccounts,
            unselectedIcon = Icons.Outlined.ManageAccounts
        ),
        BottomNavItem(
            route        = Screen.Rooms.route,
            labelRes     = R.string.nav_rooms,
            selectedIcon = Icons.Filled.Forum,
            unselectedIcon = Icons.Outlined.Forum
        ),
        BottomNavItem(
            route        = Screen.Settings.route,
            labelRes     = R.string.nav_settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings
        )
    )

    // Show bottom nav only on the three top-level destinations
    val topLevelRoutes = setOf(Screen.Accounts.route, Screen.Rooms.route, Screen.Settings.route)
    val showBottomBar = currentDestination?.hierarchy?.any { it.route in topLevelRoutes } == true

    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AleJabberNavGraph(
                navController    = navController,
                startDestination = Screen.Accounts.route
            )
        }
    }
}

/** Item descriptor for the bottom navigation bar. */
data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
