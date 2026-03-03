package com.myfinances.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.myfinances.ui.screens.categories.CategoriesScreen
import com.myfinances.ui.screens.charts.ChartsScreen
import com.myfinances.ui.screens.dashboard.DashboardScreen
import com.myfinances.ui.screens.budget.BudgetScreen
import com.myfinances.ui.screens.loans.LoansScreen
import com.myfinances.ui.screens.login.LoginScreen
import com.myfinances.ui.screens.settings.SettingsScreen
import com.myfinances.ui.screens.transactions.AddTransactionScreen
import com.myfinances.ui.screens.transactions.TransactionsScreen
import com.myfinances.ui.screens.transfers.AddTransferScreen
import com.myfinances.ui.screens.transfers.TransfersScreen
import com.myfinances.ui.viewmodel.AuthViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    
    val startDestination = if (authState.isLoggedIn) {
        NavRoutes.Dashboard.route
    } else {
        NavRoutes.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.Dashboard.route) {
                        popUpTo(NavRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                onNavigateToTransactions = {
                    navController.navigate(NavRoutes.Transactions.route)
                },
                onNavigateToTransfers = {
                    navController.navigate(NavRoutes.Transfers.route)
                },
                onNavigateToCategories = {
                    navController.navigate(NavRoutes.Categories.route)
                },
                onNavigateToCharts = {
                    navController.navigate(NavRoutes.Charts.route)
                },
                onNavigateToLoans = {
                    navController.navigate(NavRoutes.Loans.route)
                },
                onNavigateToBudget = {
                    navController.navigate(NavRoutes.Budget.route)
                },
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Budget.route) {
            BudgetScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Loans.route) {
            LoansScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Transactions.route) {
            TransactionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddTransaction = { navController.navigate(NavRoutes.AddTransaction.route) },
                onEditTransaction = { id ->
                    navController.navigate(NavRoutes.EditTransaction.createRoute(id))
                }
            )
        }

        composable(NavRoutes.AddTransaction.route) {
            AddTransactionScreen(
                onNavigateBack = { navController.popBackStack() },
                onTransactionSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.EditTransaction.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            AddTransactionScreen(
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() },
                onTransactionSaved = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Transfers.route) {
            TransfersScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddTransfer = { navController.navigate(NavRoutes.AddTransfer.route) },
                onEditTransfer = { id ->
                    navController.navigate(NavRoutes.EditTransfer.createRoute(id))
                }
            )
        }

        composable(NavRoutes.AddTransfer.route) {
            AddTransferScreen(
                onNavigateBack = { navController.popBackStack() },
                onTransferSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.EditTransfer.route,
            arguments = listOf(navArgument("transferId") { type = NavType.StringType })
        ) { backStackEntry ->
            val transferId = backStackEntry.arguments?.getString("transferId") ?: ""
            AddTransferScreen(
                transferId = transferId,
                onNavigateBack = { navController.popBackStack() },
                onTransferSaved = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Categories.route) {
            CategoriesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Charts.route) {
            ChartsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
