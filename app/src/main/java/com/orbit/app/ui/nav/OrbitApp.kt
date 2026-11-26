package com.orbit.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orbit.app.ui.assistants.AssistantEditScreen
import com.orbit.app.ui.assistants.AssistantsScreen
import com.orbit.app.ui.chat.ChatScreen
import com.orbit.app.ui.chats.ChatListScreen
import com.orbit.app.ui.editor.EditorScreen
import com.orbit.app.ui.providers.ProviderEditScreen
import com.orbit.app.ui.providers.ProvidersScreen
import com.orbit.app.ui.settings.SettingsScreen
import com.orbit.app.ui.workspace.WorkspaceScreen

private object Routes {
    const val CHATS = "chats"
    const val CHAT = "chat/{conversationId}"
    const val WORKSPACE = "workspace"
    const val EDITOR = "editor?path={path}"
    const val ASSISTANTS = "assistants"
    const val ASSISTANT_EDIT = "assistant/edit?assistantId={assistantId}"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDIT = "provider/edit?providerId={providerId}"
    const val SETTINGS = "settings"

    fun chat(id: String) = "chat/$id"
    fun editor(path: String) = "editor?path=${Uri.encode(path)}"
    fun assistantEdit(id: String?) =
        if (id == null) "assistant/edit" else "assistant/edit?assistantId=$id"
    fun providerEdit(id: String?) =
        if (id == null) "provider/edit" else "provider/edit?providerId=$id"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.CHATS, "Chats", Icons.Outlined.ChatBubbleOutline),
    TopLevelDestination(Routes.WORKSPACE, "Workspace", Icons.Outlined.FolderOpen),
    TopLevelDestination(Routes.ASSISTANTS, "Assistants", Icons.Outlined.AutoAwesome),
    TopLevelDestination(Routes.PROVIDERS, "Providers", Icons.Outlined.Api),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun OrbitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHATS,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.CHATS) {
                ChatListScreen(
                    onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                )
            }

            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
            ) { entry ->
                val conversationId = entry.arguments?.getString("conversationId").orEmpty()
                ChatScreen(
                    conversationId = conversationId,
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                    onOpenWorkspace = { navController.navigate(Routes.WORKSPACE) },
                )
            }

            composable(Routes.WORKSPACE) {
                WorkspaceScreen(
                    onOpenFile = { path -> navController.navigate(Routes.editor(path)) },
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("path") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                EditorScreen(
                    path = entry.arguments?.getString("path").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ASSISTANTS) {
                AssistantsScreen(
                    onAdd = { navController.navigate(Routes.assistantEdit(null)) },
                    onEdit = { id -> navController.navigate(Routes.assistantEdit(id)) },
                    onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                )
            }

            composable(
                route = Routes.ASSISTANT_EDIT,
                arguments = listOf(
                    navArgument("assistantId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                AssistantEditScreen(
                    assistantId = entry.arguments?.getString("assistantId"),
                    onBack = { navController.popBackStack() },
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                )
            }

            composable(Routes.PROVIDERS) {
                ProvidersScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.providerEdit(null)) },
                    onEdit = { id -> navController.navigate(Routes.providerEdit(id)) },
                )
            }

            composable(
                route = Routes.PROVIDER_EDIT,
                arguments = listOf(
                    navArgument("providerId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ProviderEditScreen(
                    providerId = entry.arguments?.getString("providerId"),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                )
            }
        }
    }
}
