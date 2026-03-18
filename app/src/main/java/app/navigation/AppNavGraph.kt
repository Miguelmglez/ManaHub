package app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import feature.carddetail.CardDetailScreen
import feature.collection.CollectionScreen
import feature.scanner.ScannerScreen
import feature.stats.StatsScreen
import feature.synergy.SynergyScreen

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    // Screens that show the bottom bar
    val bottomBarScreens = listOf(
        Screen.Collection.route,
        Screen.Stats.route,
        Screen.Synergy.route,
    )
    val showBottomBar = currentRoute in bottomBarScreens

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Collection.route,
                        onClick  = { navController.navigateSingleTop(Screen.Collection.route) },
                        icon     = { Icon(Icons.Default.CollectionsBookmark, null) },
                        label    = { Text("Collection") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Stats.route,
                        onClick  = { navController.navigateSingleTop(Screen.Stats.route) },
                        icon     = { Icon(Icons.Default.BarChart, null) },
                        label    = { Text("Stats") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Synergy.route,
                        onClick  = { navController.navigateSingleTop(Screen.Synergy.route) },
                        icon     = { Icon(Icons.Default.AutoAwesome, null) },
                        label    = { Text("Synergies") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Collection.route,
            modifier         = modifier,
        ) {
            composable(Screen.Collection.route) {
                CollectionScreen(
                    onCardClick    = { id -> navController.navigate(Screen.CardDetail.createRoute(id)) },
                    onAddCardClick = { navController.navigate(Screen.Scanner.route) },
                )
            }
            composable(
                route     = Screen.CardDetail.route,
                arguments = listOf(navArgument("scryfallId") { type = NavType.StringType }),
            ) {
                CardDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Stats.route) {
                StatsScreen(onCardClick = { id -> navController.navigate(Screen.CardDetail.createRoute(id)) })
            }
            composable(Screen.Synergy.route) {
                SynergyScreen(onCardClick = { id -> navController.navigate(Screen.CardDetail.createRoute(id)) })
            }
        }
    }
}

private fun NavController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}