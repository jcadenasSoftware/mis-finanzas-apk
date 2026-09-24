package com.jcadenas.xpendz.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcadenas.xpendz.data.local.GoalAchievementTracker
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.GoalEntity
import com.jcadenas.xpendz.data.repository.BudgetRepository
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.CategoryRepository
import com.jcadenas.xpendz.data.repository.GoalDeletionInfo
import com.jcadenas.xpendz.data.repository.GoalDeletionOutcome
import com.jcadenas.xpendz.data.repository.GoalRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.data.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class MonthlyBudgetItem(
    val categoryId: String,
    val categoryName: String,
    val limitCents: Long,
    val spentCents: Long
)

data class BudgetState(
    val isLoading: Boolean = false,
    val goals: List<GoalEntity> = emptyList(),
    val archivedGoals: List<GoalEntity> = emptyList(),
    val achievedGoal: GoalEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalancesCents: Map<String, Long> = emptyMap(),
    val goalAccountBalancesCents: Map<String, Long> = emptyMap(),
    val monthlyMonth: String = "",
    val monthlyCurrency: String = "",
    val monthlyExpenseMonths: List<String> = emptyList(),
    val monthlyRootCategories: List<CategoryEntity> = emptyList(),
    val monthlyChildrenMap: Map<String, List<CategoryEntity>> = emptyMap(),
    val monthlyLimitsByCategoryId: Map<String, Long> = emptyMap(),
    val monthlySubcategoryItemsByRootId: Map<String, List<MonthlyBudgetItem>> = emptyMap(),
    val monthlyItems: List<MonthlyBudgetItem> = emptyList(),
    val monthlyTotalLimitCents: Long = 0,
    val monthlyTotalSpentCents: Long = 0,
    val error: String? = null
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val goalAchievementTracker: GoalAchievementTracker
) : ViewModel() {

    private companion object {
        const val BASE_BUDGET_MONTH = "__BASE__"
    }

    private val _state = MutableStateFlow(BudgetState())
    val state: StateFlow<BudgetState> = _state.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    fun refresh() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val accountsDeferred = async { accountRepository.getAccounts(uid) }
                val goalsDeferred = async { goalRepository.getByUser(uid) }

                val accounts = accountsDeferred.await()
                val allGoals = goalsDeferred.await().map {
                    it.copy(status = GoalEntity.normalizeStatus(it.status, it.id))
                }
                val goals = allGoals.filter { it.status != GoalEntity.STATUS_CLOSED }
                val archivedGoals = allGoals.filter { it.status == GoalEntity.STATUS_CLOSED }

                val accountBalances = mutableMapOf<String, Long>()
                for (a in accounts) {
                    accountBalances[a.id] = runCatching { accountRepository.computeBalance(uid, a.id) }.getOrDefault(0L)
                }

                val balances = mutableMapOf<String, Long>()
                var achievedGoal: GoalEntity? = null
                for (g in allGoals) {
                    val balance = runCatching { accountRepository.computeBalance(uid, g.accountId) }.getOrDefault(0L)
                    balances[g.id] = balance
                    val achieved = g.targetCents > 0 && balance >= g.targetCents
                    if (goalAchievementTracker.onProgressEvaluated(g.id, achieved) && achievedGoal == null) {
                        achievedGoal = g
                    }
                }

                val existingMonth = _state.value.monthlyMonth
                val existingCurrency = _state.value.monthlyCurrency
                val nowMonth = currentMonthKey()
                val monthKey = if (existingMonth.isBlank()) nowMonth else existingMonth
                val currency = if (existingCurrency.isBlank()) {
                    accounts.firstOrNull()?.currency ?: "COP"
                } else {
                    existingCurrency
                }

                val monthly = loadMonthly(uid, monthKey, currency)

                _state.value = _state.value.copy(
                    isLoading = false,
                    goals = goals,
                    archivedGoals = archivedGoals,
                    achievedGoal = achievedGoal ?: _state.value.achievedGoal,
                    accounts = accounts,
                    accountBalancesCents = accountBalances,
                    goalAccountBalancesCents = balances,
                    monthlyMonth = monthKey,
                    monthlyCurrency = currency,
                    monthlyExpenseMonths = monthly.expenseMonths,
                    monthlyRootCategories = monthly.rootCategories,
                    monthlyChildrenMap = monthly.childrenMap,
                    monthlyLimitsByCategoryId = monthly.limitsByCategoryId,
                    monthlySubcategoryItemsByRootId = monthly.subcategoryItemsByRootId,
                    monthlyItems = monthly.items,
                    monthlyTotalLimitCents = monthly.totalLimitCents,
                    monthlyTotalSpentCents = monthly.totalSpentCents
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private data class MonthlyLoadResult(
        val expenseMonths: List<String>,
        val rootCategories: List<CategoryEntity>,
        val childrenMap: Map<String, List<CategoryEntity>>,
        val limitsByCategoryId: Map<String, Long>,
        val subcategoryItemsByRootId: Map<String, List<MonthlyBudgetItem>>,
        val items: List<MonthlyBudgetItem>,
        val totalLimitCents: Long,
        val totalSpentCents: Long
    )

    private suspend fun loadMonthly(userUid: String, monthKey: String, currency: String): MonthlyLoadResult {
        val (fromEpochSec, toEpochSec) = monthRangeEpochSec(monthKey)
        val baseBudgets = budgetRepository.getByMonth(userUid, BASE_BUDGET_MONTH, currency)
        budgetRepository.getByMonth(userUid, monthKey, currency)

        val expenseMonths = runCatching { transactionRepository.getExpenseMonths(userUid, currency, limit = 24) }
            .getOrDefault(emptyList())
        val expenseMonthsWithCurrent = (listOf(monthKey) + expenseMonths)
            .distinct()
            .sortedDescending()

        val spentTotals = transactionRepository.getExpenseTotalsByRootCategoryInRange(
            userUid = userUid,
            currency = currency,
            fromEpochSec = fromEpochSec,
            toEpochSec = toEpochSec
        )

        val spentByCategoryTotals = transactionRepository.getExpenseTotalsByCategoryInRange(
            userUid = userUid,
            currency = currency,
            fromEpochSec = fromEpochSec,
            toEpochSec = toEpochSec
        )

        val spentMap = spentTotals.associate { it.rootCategoryId to it.totalSpentCents }
        val spentByCategoryMap = spentByCategoryTotals.associate { it.categoryId to it.totalSpentCents }
        val baseLimitMap = baseBudgets.associate { it.categoryId to it.limitCents }
        val limitMap = baseLimitMap

        val roots = categoryRepository.getRoots(userUid)
        val rootMap = roots.associateBy { it.id }

        val childrenMap = linkedMapOf<String, List<CategoryEntity>>()
        for (r in roots) {
            val children = runCatching { categoryRepository.getChildren(userUid, r.id) }.getOrDefault(emptyList())
                .sortedBy { it.name }
            if (children.isNotEmpty()) {
                childrenMap[r.id] = children
            }
        }

        val summedLimitByRoot = mutableMapOf<String, Long>()
        for (r in roots) {
            val children = childrenMap[r.id].orEmpty()
            if (children.isEmpty()) {
                summedLimitByRoot[r.id] = limitMap[r.id] ?: 0L
            } else {
                var sum = 0L
                for (c in children) {
                    sum += (limitMap[c.id] ?: 0L)
                }
                summedLimitByRoot[r.id] = sum
            }
        }

        val subcategoryItemsByRootId = linkedMapOf<String, List<MonthlyBudgetItem>>()
        for (r in roots) {
            val children = childrenMap[r.id].orEmpty()
            if (children.isEmpty()) continue

            val childItems = children.map { child ->
                val limit = limitMap[child.id] ?: 0L
                val spent = spentByCategoryMap[child.id] ?: 0L
                MonthlyBudgetItem(
                    categoryId = child.id,
                    categoryName = child.name,
                    limitCents = limit,
                    spentCents = spent
                )
            }.sortedBy { it.categoryName }

            subcategoryItemsByRootId[r.id] = childItems
        }

        val items = roots
            .map { cat ->
                MonthlyBudgetItem(
                    categoryId = cat.id,
                    categoryName = cat.name,
                    limitCents = summedLimitByRoot[cat.id] ?: 0L,
                    spentCents = spentMap[cat.id] ?: 0L
                )
            }
            .sortedBy { it.categoryName }

        val totalLimit = items.sumOf { it.limitCents }
        val totalSpent = items.sumOf { it.spentCents }
        return MonthlyLoadResult(expenseMonthsWithCurrent, roots, childrenMap, limitMap, subcategoryItemsByRootId, items, totalLimit, totalSpent)
    }

    fun setMonthlyCurrency(currency: String) {
        val uid = userUid ?: return
        val monthKey = _state.value.monthlyMonth.ifBlank { currentMonthKey() }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val monthly = loadMonthly(uid, monthKey, currency)
                _state.value = _state.value.copy(
                    isLoading = false,
                    monthlyCurrency = currency,
                    monthlyExpenseMonths = monthly.expenseMonths,
                    monthlyRootCategories = monthly.rootCategories,
                    monthlyChildrenMap = monthly.childrenMap,
                    monthlyLimitsByCategoryId = monthly.limitsByCategoryId,
                    monthlySubcategoryItemsByRootId = monthly.subcategoryItemsByRootId,
                    monthlyItems = monthly.items,
                    monthlyTotalLimitCents = monthly.totalLimitCents,
                    monthlyTotalSpentCents = monthly.totalSpentCents
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun shiftMonthlyMonth(deltaMonths: Int) {
        val uid = userUid ?: return
        val current = _state.value.monthlyMonth.ifBlank { currentMonthKey() }
        val currency = _state.value.monthlyCurrency.ifBlank { _state.value.accounts.firstOrNull()?.currency ?: "COP" }
        val next = shiftMonthKey(current, deltaMonths)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val monthly = loadMonthly(uid, next, currency)
                _state.value = _state.value.copy(
                    isLoading = false,
                    monthlyMonth = next,
                    monthlyExpenseMonths = monthly.expenseMonths,
                    monthlyRootCategories = monthly.rootCategories,
                    monthlyChildrenMap = monthly.childrenMap,
                    monthlyLimitsByCategoryId = monthly.limitsByCategoryId,
                    monthlySubcategoryItemsByRootId = monthly.subcategoryItemsByRootId,
                    monthlyItems = monthly.items,
                    monthlyTotalLimitCents = monthly.totalLimitCents,
                    monthlyTotalSpentCents = monthly.totalSpentCents
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun upsertMonthlyLimit(categoryId: String, limitCents: Long) {
        val uid = userUid ?: return
        val currency = _state.value.monthlyCurrency.ifBlank { _state.value.accounts.firstOrNull()?.currency ?: "COP" }
        val monthKey = _state.value.monthlyMonth.ifBlank { currentMonthKey() }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                budgetRepository.upsert(
                    userUid = uid,
                    month = BASE_BUDGET_MONTH,
                    categoryId = categoryId,
                    currency = currency,
                    limitCents = limitCents
                )
                val monthly = loadMonthly(uid, monthKey, currency)
                _state.value = _state.value.copy(
                    isLoading = false,
                    monthlyExpenseMonths = monthly.expenseMonths,
                    monthlyRootCategories = monthly.rootCategories,
                    monthlyChildrenMap = monthly.childrenMap,
                    monthlyLimitsByCategoryId = monthly.limitsByCategoryId,
                    monthlySubcategoryItemsByRootId = monthly.subcategoryItemsByRootId,
                    monthlyItems = monthly.items,
                    monthlyTotalLimitCents = monthly.totalLimitCents,
                    monthlyTotalSpentCents = monthly.totalSpentCents
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun copyPreviousMonthBudgets() {
        val uid = userUid ?: return
        val monthKey = _state.value.monthlyMonth.ifBlank { currentMonthKey() }
        val prev = shiftMonthKey(monthKey, -1)
        val currency = _state.value.monthlyCurrency.ifBlank { _state.value.accounts.firstOrNull()?.currency ?: "COP" }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val prevBudgets = budgetRepository.getByMonth(uid, prev, currency)
                for (b in prevBudgets) {
                    budgetRepository.upsert(
                        userUid = uid,
                        month = BASE_BUDGET_MONTH,
                        categoryId = b.categoryId,
                        currency = currency,
                        limitCents = b.limitCents
                    )
                }
                val monthly = loadMonthly(uid, monthKey, currency)
                _state.value = _state.value.copy(
                    isLoading = false,
                    monthlyExpenseMonths = monthly.expenseMonths,
                    monthlyRootCategories = monthly.rootCategories,
                    monthlyChildrenMap = monthly.childrenMap,
                    monthlyLimitsByCategoryId = monthly.limitsByCategoryId,
                    monthlySubcategoryItemsByRootId = monthly.subcategoryItemsByRootId,
                    monthlyItems = monthly.items,
                    monthlyTotalLimitCents = monthly.totalLimitCents,
                    monthlyTotalSpentCents = monthly.totalSpentCents
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun currentMonthKey(nowEpochSec: Long = System.currentTimeMillis() / 1000): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowEpochSec * 1000 }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(y, m)
    }

    private fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = parts.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, (month - 1))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, deltaMonths)
        }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(y, m)
    }

    private fun monthRangeEpochSec(monthKey: String): Pair<Long, Long> {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = parts.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)

        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            add(Calendar.MONTH, 1)
            add(Calendar.SECOND, -1)
        }
        return (start.timeInMillis / 1000) to (end.timeInMillis / 1000)
    }

    fun createGoal(
        name: String,
        targetCents: Long,
        targetDateEpochSec: Long,
        currency: String
    ) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                goalRepository.createWithAccount(
                    userUid = uid,
                    name = name,
                    currency = currency,
                    targetCents = targetCents,
                    targetDateEpochSec = targetDateEpochSec
                )

                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateGoal(
        goalId: String,
        name: String,
        targetCents: Long,
        targetDateEpochSec: Long,
        currency: String
    ) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                goalRepository.update(
                    userUid = uid,
                    goalId = goalId,
                    name = name,
                    currency = currency,
                    targetCents = targetCents,
                    targetDateEpochSec = targetDateEpochSec
                )

                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = goalErrorMessage(e))
            }
        }
    }

    fun closeGoal(goalId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                goalRepository.close(uid, goalId)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = goalErrorMessage(e))
            }
        }
    }

    fun reopenGoal(goalId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                goalRepository.reopen(uid, goalId)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = goalErrorMessage(e))
            }
        }
    }

    suspend fun goalDeletionInfo(goalId: String): GoalDeletionInfo? {
        val uid = userUid ?: return null
        return goalRepository.getDeletionInfo(uid, goalId)
    }

    fun deleteGoal(
        goalId: String,
        onOutcome: (GoalDeletionOutcome) -> Unit = {}
    ) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val outcome = goalRepository.deleteGoal(uid, goalId)
                refresh()
                onOutcome(outcome)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = goalErrorMessage(e))
            }
        }
    }

    fun dismissGoalAchievement() {
        _state.value = _state.value.copy(achievedGoal = null)
    }

    private fun goalErrorMessage(e: Exception): String {
        return when (e.message) {
            "goal_not_open" -> "La meta está archivada. Reábrela para poder operarla."
            "goal_not_archived" -> "La meta no está archivada."
            "goal_has_balance" -> "No se puede eliminar una meta que aún tiene dinero. Retira el saldo primero."
            "Meta no encontrada" -> "Meta no encontrada"
            else -> e.message ?: e.toString()
        }
    }

    fun withdrawFromGoal(
        goalId: String,
        toAccountId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val goal = try {
                    goalRepository.requireOpen(goalId)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(isLoading = false, error = goalErrorMessage(e))
                    return@launch
                }

                transferRepository.create(
                    userUid = uid,
                    fromAccountId = goal.accountId,
                    toAccountId = toAccountId,
                    amountCents = amountCents,
                    occurredAtEpochSec = occurredAtEpochSec,
                    note = note
                )

                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun depositToGoal(
        goalId: String,
        fromAccountId: String,
        amountCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val goal = try {
                    goalRepository.requireOpen(goalId)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(isLoading = false, error = goalErrorMessage(e))
                    return@launch
                }

                transferRepository.create(
                    userUid = uid,
                    fromAccountId = fromAccountId,
                    toAccountId = goal.accountId,
                    amountCents = amountCents,
                    occurredAtEpochSec = occurredAtEpochSec,
                    note = note
                )

                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun monthsUntil(targetDateEpochSec: Long, nowEpochSec: Long): Int {
        val start = Calendar.getInstance().apply { timeInMillis = nowEpochSec * 1000 }
        val end = Calendar.getInstance().apply { timeInMillis = targetDateEpochSec * 1000 }

        val startY = start.get(Calendar.YEAR)
        val startM = start.get(Calendar.MONTH)
        val endY = end.get(Calendar.YEAR)
        val endM = end.get(Calendar.MONTH)

        val diff = (endY - startY) * 12 + (endM - startM)
        return diff.coerceAtLeast(1)
    }
}
