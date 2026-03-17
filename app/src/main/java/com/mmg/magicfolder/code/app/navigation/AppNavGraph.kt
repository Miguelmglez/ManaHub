package com.mmg.magicfolder.code.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Collection.route,
        modifier         = modifier,
    ) {
        composable(Screen.Collection.route) {
            // CollectionScreen(onCardClick = { id -> navController.navigate(Screen.CardDetail.createRoute(id)) })
        }
        composable(
            route     = Screen.CardDetail.route,
            arguments = listOf(navArgument("scryfallId") { type = NavType.StringType }),
        ) {
            // CardDetailScreen()
        }
        composable(Screen.Scanner.route)     { /* ScannerScreen() */ }
        composable(Screen.Stats.route)       { /* StatsScreen() */ }
        composable(Screen.Synergy.route)     { /* SynergyScreen() */ }
        composable(Screen.DeckBuilder.route) { /* DeckBuilderScreen() */ }
    }
}