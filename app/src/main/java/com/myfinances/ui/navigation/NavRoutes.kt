package com.myfinances.ui.navigation

sealed class NavRoutes(val route: String) {
    object Login : NavRoutes("login")
    object Dashboard : NavRoutes("dashboard")
    object Transactions : NavRoutes("transactions?accountId={accountId}&categoryId={categoryId}&from={from}&to={to}") {
        fun createRoute(
            accountId: String? = null,
            categoryId: String? = null,
            fromEpochSec: Long? = null,
            toEpochSec: Long? = null
        ): String {
            val a = accountId ?: ""
            val c = categoryId ?: ""
            val f = fromEpochSec?.toString() ?: ""
            val t = toEpochSec?.toString() ?: ""
            return "transactions?accountId=$a&categoryId=$c&from=$f&to=$t"
        }
    }
    object Transfers : NavRoutes("transfers")
    object Categories : NavRoutes("categories")
    object Charts : NavRoutes("charts")
    object Loans : NavRoutes("loans")
    object Budget : NavRoutes("budget")
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
    object Settings : NavRoutes("settings")
    object PrivacyAndData : NavRoutes("privacy_and_data")
    object Reports : NavRoutes("reports")
}
