package com.myfinances.ui.navigation

sealed class NavRoutes(val route: String) {
    object Login : NavRoutes("login")
    object Dashboard : NavRoutes("dashboard")
    object Transactions : NavRoutes("transactions")
    object Transfers : NavRoutes("transfers")
    object Categories : NavRoutes("categories")
    object Charts : NavRoutes("charts")
    object Loans : NavRoutes("loans")
    object Budget : NavRoutes("budget")
    object Settings : NavRoutes("settings")
    object AddTransaction : NavRoutes("add_transaction")
    object EditTransaction : NavRoutes("edit_transaction/{transactionId}") {
        fun createRoute(transactionId: String) = "edit_transaction/$transactionId"
    }
    object AddTransfer : NavRoutes("add_transfer")
    object EditTransfer : NavRoutes("edit_transfer/{transferId}") {
        fun createRoute(transferId: String) = "edit_transfer/$transferId"
    }
    object AddAccount : NavRoutes("add_account")
    object AddCategory : NavRoutes("add_category")
}
