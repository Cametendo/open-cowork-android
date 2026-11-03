package io.github.cametendo.opencowork.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.cametendo.opencowork.AppContainer
import io.github.cametendo.opencowork.data.AuthState
import io.github.cametendo.opencowork.data.User
import io.github.cametendo.opencowork.ui.admin.AdminScreen
import io.github.cametendo.opencowork.ui.chat.ChatListScreen
import io.github.cametendo.opencowork.ui.chat.ChatScreen
import io.github.cametendo.opencowork.ui.components.LocalToaster
import io.github.cametendo.opencowork.ui.components.Toaster
import io.github.cametendo.opencowork.ui.components.rememberActions
import io.github.cametendo.opencowork.ui.cowork.CoworkListScreen
import io.github.cametendo.opencowork.ui.cowork.CoworkSessionScreen
import io.github.cametendo.opencowork.ui.cowork.NewTaskScreen
import io.github.cametendo.opencowork.ui.cowork.WorkspaceScreen
import io.github.cametendo.opencowork.ui.login.LoginScreen
import io.github.cametendo.opencowork.ui.repos.ReposScreen
import io.github.cametendo.opencowork.ui.settings.AboutScreen
import io.github.cametendo.opencowork.ui.settings.AccountScreen
import io.github.cametendo.opencowork.ui.settings.AppearanceScreen
import io.github.cametendo.opencowork.ui.settings.ChatDefaultsScreen
import io.github.cametendo.opencowork.ui.settings.CoworkDefaultsScreen
import io.github.cametendo.opencowork.ui.settings.GitHubScreen
import io.github.cametendo.opencowork.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

sealed interface NavRequest {
    data class Chat(val id: String) : NavRequest
    data class Cowork(val id: String) : NavRequest
}

object Routes {
    const val CHATS = "chats"
    const val COWORK = "cowork"
    const val REPOS = "repos"
    const val SETTINGS = "settings"
    fun chat(id: String) = "chat/$id"
    fun task(id: String) = "task/$id"
    fun newTask(repoId: String? = null) = if (repoId != null) "newtask?repo=$repoId" else "newtask"
    fun workspace(id: String) = "task/$id/workspace"
}

/** Which optional features this user can use (mirrors the web UI's checks). */
data class Features(val cowork: Boolean, val github: Boolean, val admin: Boolean)

val LocalFeatures = staticCompositionLocalOf { Features(cowork = true, github = true, admin = false) }

@Composable
fun AppRoot(container: AppContainer, navRequests: MutableStateFlow<NavRequest?>) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(snack, scope) }
    CompositionLocalProvider(LocalContainer provides container, LocalToaster provides toaster) {
        val auth by container.auth.state.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val st = auth) {
                AuthState.Loading -> Splash()
                is AuthState.SignedOut -> LoginScreen(st.message)
                is AuthState.SignedIn -> if (st.user.isPending) PendingScreen(st.user) else {
                    val features = Features(
                        cowork = st.user.isAdmin || st.config?.features?.cowork != false,
                        github = st.user.isAdmin || st.config?.features?.github != false,
                        admin = st.user.isAdmin,
                    )
                    CompositionLocalProvider(LocalFeatures provides features) { MainNav(navRequests) }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 12.dp, end = 12.dp))
        }
    }
}

@Composable
fun AppLogo(size: Int = 64) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8), Color(0xFF0B2A6B)))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.ChatBubble, null, tint = Color.White, modifier = Modifier.size((size * 0.48f).dp)) }
}

@Composable
private fun Splash() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(72)
        Box(Modifier.padding(top = 24.dp)) { CircularProgressIndicator() }
    }
}

@Composable
private fun PendingScreen(user: User) {
    val container = LocalContainer.current
    val actions = rememberActions()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.HourglassTop, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Account pending activation", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Hi ${user.name}! An administrator has to activate your account before you can use Open Cowork. Check back once they have.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { actions.launch { container.auth.signOut() } }) { Text("Sign out") }
            Button(onClick = { actions.launch { container.auth.refresh() } }) { Text("Check again") }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun MainNav(navRequests: MutableStateFlow<NavRequest?>) {
    val nav = rememberNavController()
    val features = LocalFeatures.current
    val context = LocalContext.current

    // Deep links from notifications
    LaunchedEffect(Unit) {
        navRequests.collect { req ->
            when (req) {
                is NavRequest.Chat -> nav.navigate(Routes.chat(req.id)) { launchSingleTop = true }
                is NavRequest.Cowork -> nav.navigate(Routes.task(req.id)) { launchSingleTop = true }
                null -> {}
            }
            if (req != null) navRequests.value = null
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val tabs = buildList {
        add(Tab(Routes.CHATS, "Chats", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble))
        if (features.cowork) add(Tab(Routes.COWORK, "Cowork", Icons.Outlined.SmartToy, Icons.Filled.SmartToy))
        if (features.github) add(Tab(Routes.REPOS, "Repos", Icons.Outlined.FolderCopy, Icons.Filled.FolderCopy))
        add(Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings))
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(showBar, enter = expandVertically(), exit = shrinkVertically()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.CHATS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavGraph(nav, Modifier.fillMaxWidth().padding(pad).consumeWindowInsets(pad))
    }
}

@Composable
private fun NavGraph(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        nav, startDestination = Routes.CHATS, modifier = modifier,
        enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
    ) {
        composable(Routes.CHATS) {
            ChatListScreen(onOpen = { nav.navigate(Routes.chat(it)) }, onNew = { nav.navigate(Routes.chat("new")) })
        }
        composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("id") ?: "new"
            ChatScreen(
                chatId = id.takeIf { it != "new" },
                onBack = back,
                onCreated = { newId -> nav.navigate(Routes.chat(newId)) { popUpTo("chat/{id}") { inclusive = true } } },
            )
        }
        composable(Routes.COWORK) {
            CoworkListScreen(onOpen = { nav.navigate(Routes.task(it)) }, onNew = { nav.navigate(Routes.newTask()) })
        }
        composable("newtask?repo={repo}", arguments = listOf(navArgument("repo") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            NewTaskScreen(
                initialRepo = e.arguments?.getString("repo"),
                onBack = back,
                onStarted = { id -> nav.navigate(Routes.task(id)) { popUpTo("newtask?repo={repo}") { inclusive = true } } },
                onManageRepos = { nav.navigate(Routes.REPOS) },
            )
        }
        composable("task/{id}") { e ->
            val id = e.arguments?.getString("id")!!
            CoworkSessionScreen(id, onBack = back, onWorkspace = { nav.navigate(Routes.workspace(id)) })
        }
        composable("task/{id}/workspace") { e ->
            WorkspaceScreen(e.arguments?.getString("id")!!, onBack = back)
        }
        composable(Routes.REPOS) {
            ReposScreen(onStartTask = { repoId -> nav.navigate(Routes.newTask(repoId)) }, onGitHubSettings = { nav.navigate("settings/github") })
        }
        composable(Routes.SETTINGS) { SettingsScreen(onNavigate = { nav.navigate(it) }) }
        composable("settings/appearance") { AppearanceScreen(onBack = back) }
        composable("settings/chat") { ChatDefaultsScreen(onBack = back) }
        composable("settings/cowork") { CoworkDefaultsScreen(onBack = back) }
        composable("settings/github") { GitHubScreen(onBack = back) }
        composable("settings/account") { AccountScreen(onBack = back) }
        composable("settings/about") { AboutScreen(onBack = back) }
        composable("admin") { AdminScreen(onBack = back) }
    }
}
