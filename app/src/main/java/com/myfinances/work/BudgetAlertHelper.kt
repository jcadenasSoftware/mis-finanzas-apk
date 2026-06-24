package com.jcadenas.xpendz.work

import android.util.Log
import android.content.Context
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.dao.AccountDao
import com.jcadenas.xpendz.data.local.dao.BudgetDao
import com.jcadenas.xpendz.data.local.dao.CategoryDao
import com.jcadenas.xpendz.data.local.dao.TransactionDao
import com.jcadenas.xpendz.data.local.dao.UserSettingsDao
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class BudgetInAppAlert(
    val categoryId: String,
    val title: String,
    val message: String,
    val percentage: Int,
    val exceeded: Boolean
)

class BudgetAlertHelper(
    private val context: Context,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val userSettingsDao: UserSettingsDao
) {
    companion object {
        @Volatile
        var instance: BudgetAlertHelper? = null
            private set

        private val _alerts = MutableSharedFlow<BudgetInAppAlert>(extraBufferCapacity = 16)
        val alerts: SharedFlow<BudgetInAppAlert> = _alerts.asSharedFlow()

        fun init(context: Context, database: AppDatabase) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = BudgetAlertHelper(
                            context = context.applicationContext,
                            budgetDao = database.budgetDao(),
                            transactionDao = database.transactionDao(),
                            accountDao = database.accountDao(),
                            categoryDao = database.categoryDao(),
                            userSettingsDao = database.userSettingsDao()
                        )
                        Log.d("BudgetAlert", "BudgetAlertHelper initialized")
                    }
                }
            }
        }

        private const val WARNING_THRESHOLD = 0.80
        private const val EXCEEDED_THRESHOLD = 1.00
        private const val BASE_BUDGET_MONTH = "__BASE__"

        private const val PREFS_NAME = "budget_alert_state"
        private const val KEY_WARNING = "warning"
        private const val KEY_EXCEEDED = "exceeded"
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun wasSent(userUid: String, currency: String, monthKey: String, categoryId: String, kind: String): Boolean {
        val key = "$userUid|$currency|$monthKey|$categoryId|$kind"
        return prefs().getBoolean(key, false)
    }

    private fun markSent(userUid: String, currency: String, monthKey: String, categoryId: String, kind: String) {
        val key = "$userUid|$currency|$monthKey|$categoryId|$kind"
        prefs().edit().putBoolean(key, true).apply()
    }

    suspend fun checkAfterTransaction(userUid: String, accountId: String, categoryId: String, occurredAtEpochSec: Long) {
        Log.d("BudgetAlert", "checkAfterTransaction: uid=$userUid accountId=$accountId categoryId=$categoryId")

        // Determine currency from the transaction account to avoid mismatches with settings.baseCurrency.
        val currency = runCatching { accountDao.getById(accountId)?.currency.orEmpty() }.getOrDefault("")
        if (currency.isBlank()) {
            Log.d("BudgetAlert", "ABORT: account currency blank for accountId=$accountId")
            return
        }

        val monthKey = Instant.ofEpochSecond(occurredAtEpochSec)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("yyyy-MM"))
        Log.d("BudgetAlert", "currency=$currency month=$monthKey")

        val category = categoryDao.getById(categoryId)
        if (category == null) { Log.d("BudgetAlert", "ABORT: category not found for id=$categoryId"); return }
        Log.d("BudgetAlert", "category=${category.name} parentId=${category.parentId}")

        // Prioridad 1: budget en la propia categoría del gasto
        // Prioridad 2: budget en el padre (si es subcategoría)
        // Para compatibilidad, primero buscamos presupuesto del mes actual y luego el "base" (__BASE__)
        val budgetOwn =
            budgetDao.getByUnique(userUid, monthKey, currency, categoryId)
                ?: budgetDao.getByUnique(userUid, BASE_BUDGET_MONTH, currency, categoryId)

        val budgetParent = if (budgetOwn == null && category.parentId != null) {
            budgetDao.getByUnique(userUid, monthKey, currency, category.parentId)
                ?: budgetDao.getByUnique(userUid, BASE_BUDGET_MONTH, currency, category.parentId)
        } else {
            null
        }

        val budget = budgetOwn ?: budgetParent
        if (budget == null) { Log.d("BudgetAlert", "ABORT: no budget found for categoryId=$categoryId nor parent=${category.parentId} currency=$currency"); return }

        val budgetCategoryId = budget.categoryId
        val budgetCategoryName = categoryDao.getById(budgetCategoryId)?.name ?: category.name
        val limit = budget.limitCents
        if (limit <= 0) { Log.d("BudgetAlert", "ABORT: limit=$limit <= 0"); return }
        Log.d("BudgetAlert", "budget found: budgetCategoryId=$budgetCategoryId limitCents=$limit")

        val spentList = transactionDao.getSpentPerCategoryForMonth(userUid, currency, monthKey)
        val allCategories = categoryDao.getByUser(userUid)

        // Si el budget es de la categoría propia (subcategoría), solo contar esa
        // Si el budget es del padre (raíz), sumar raíz + todos sus hijos
        val subcategoryIds = if (budgetCategoryId == categoryId) {
            setOf(categoryId)
        } else {
            allCategories.filter { it.parentId == budgetCategoryId }.map { it.id }.toSet() + budgetCategoryId
        }

        val spent = spentList
            .filter { it.categoryId in subcategoryIds }
            .sumOf { it.totalSpentCents }

        val ratio = spent.toDouble() / limit.toDouble()
        val percentage = (ratio * 100).toInt()
        Log.d("BudgetAlert", "subcategoryIds=$subcategoryIds spentCents=$spent limitCents=$limit ratio=$ratio ($percentage%)")

        when {
            ratio >= EXCEEDED_THRESHOLD -> {
                val alreadyExceeded = wasSent(userUid, currency, monthKey, budgetCategoryId, KEY_EXCEEDED)
                if (!alreadyExceeded) {
                    _alerts.tryEmit(
                        BudgetInAppAlert(
                            categoryId = budgetCategoryId,
                            title = "🚨 Presupuesto superado",
                            message = "$budgetCategoryName: has superado tu límite mensual ($percentage%)",
                            percentage = percentage,
                            exceeded = true
                        )
                    )
                    markSent(userUid, currency, monthKey, budgetCategoryId, KEY_EXCEEDED)
                    markSent(userUid, currency, monthKey, budgetCategoryId, KEY_WARNING)
                }
            }
            ratio >= WARNING_THRESHOLD -> {
                val alreadyWarning = wasSent(userUid, currency, monthKey, budgetCategoryId, KEY_WARNING)
                if (!alreadyWarning) {
                    _alerts.tryEmit(
                        BudgetInAppAlert(
                            categoryId = budgetCategoryId,
                            title = "⚠️ Presupuesto al límite",
                            message = "$budgetCategoryName: has usado el $percentage% de tu presupuesto mensual",
                            percentage = percentage,
                            exceeded = false
                        )
                    )
                    markSent(userUid, currency, monthKey, budgetCategoryId, KEY_WARNING)
                }
            }
        }
    }
}
