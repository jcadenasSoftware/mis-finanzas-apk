package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.local.entity.GoalEntity
import com.myfinances.data.repository.BudgetRepository
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.GoalRepository
import com.myfinances.data.repository.TransactionRepository
import com.myfinances.data.repository.TransferRepository
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
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalancesCents: Map<String, Long> = emptyMap(),
    val goalAccountBalancesCents: Map<String, Long> = emptyMap(),
    val monthlyMonth: String = "",
    val monthlyCurrency: String = "",
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
    private val transactionRepository: TransactionRepository
) : ViewModel() {

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
                val goals = goalsDeferred.await().filter { it.status != "DELETED" }

                val accountBalances = mutableMapOf<String, Long>()
                for (a in accounts) {
                    accountBalances[a.id] = runCatching { accountRepository.computeBalance(uid, a.id) }.getOrDefault(0L)
                }

                val balances = mutableMapOf<String, Long>()
                for (g in goals) {
                    balances[g.id] = runCatching { accountRepository.computeBalance(uid, g.accountId) }.getOrDefault(0L)
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
                    accounts = accounts,
                    accountBalancesCents = accountBalances,
                    goalAccountBalancesCents = balances,
                    monthlyMonth = monthKey,
                    monthlyCurrency = currency,
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
        val budgets = budgetRepository.getByMonth(userUid, monthKey, currency)
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
        val limitMap = budgets.associate { it.categoryId to it.limitCents }

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
            var sum = limitMap[r.id] ?: 0L
            val children = childrenMap[r.id].orEmpty()
            for (c in children) {
                sum += (limitMap[c.id] ?: 0L)
            }
            summedLimitByRoot[r.id] = sum
        }

        val subcategoryItemsByRootId = linkedMapOf<String, List<MonthlyBudgetItem>>()
        for (r in roots) {
            val children = childrenMap[r.id].orEmpty()
            if (children.isEmpty()) continue

            val childItems = children.mapNotNull { child ->
                val limit = limitMap[child.id] ?: 0L
                val spent = spentByCategoryMap[child.id] ?: 0L
                if (limit <= 0L && spent <= 0L) return@mapNotNull null
                MonthlyBudgetItem(
                    categoryId = child.id,
                    categoryName = child.name,
                    limitCents = limit,
                    spentCents = spent
                )
            }.sortedBy { it.categoryName }

            if (childItems.isNotEmpty()) {
                subcategoryItemsByRootId[r.id] = childItems
            }
        }

        val rootsWithLimits = summedLimitByRoot.filterValues { it > 0 }.keys
        val usedCategoryIds = (spentMap.keys + rootsWithLimits)

        val items = usedCategoryIds.mapNotNull { id ->
            val cat = rootMap[id] ?: return@mapNotNull null
            MonthlyBudgetItem(
                categoryId = id,
                categoryName = cat.name,
                limitCents = summedLimitByRoot[id] ?: 0L,
                spentCents = spentMap[id] ?: 0L
            )
        }.sortedBy { it.categoryName }

        val totalLimit = items.sumOf { it.limitCents }
        val totalSpent = items.sumOf { it.spentCents }
        return MonthlyLoadResult(roots, childrenMap, limitMap, subcategoryItemsByRootId, items, totalLimit, totalSpent)
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
        val monthKey = _state.value.monthlyMonth.ifBlank { currentMonthKey() }
        val currency = _state.value.monthlyCurrency.ifBlank { _state.value.accounts.firstOrNull()?.currency ?: "COP" }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                budgetRepository.upsert(
                    userUid = uid,
                    month = monthKey,
                    categoryId = categoryId,
                    currency = currency,
                    limitCents = limitCents
                )
                val monthly = loadMonthly(uid, monthKey, currency)
                _state.value = _state.value.copy(
                    isLoading = false,
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
                        month = monthKey,
                        categoryId = b.categoryId,
                        currency = currency,
                        limitCents = b.limitCents
                    )
                }
                val monthly = loadMonthly(uid, monthKey, currency)
                _state.value = _state.value.copy(
                    isLoading = false,
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
                val goalAccount = accountRepository.create(
                    userUid = uid,
                    name = name,
                    type = "SAVINGS",
                    currency = currency
                )

                goalRepository.create(
                    userUid = uid,
                    name = name,
                    currency = currency,
                    targetCents = targetCents,
                    targetDateEpochSec = targetDateEpochSec,
                    accountId = goalAccount.id
                )

                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
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
                val goal = _state.value.goals.firstOrNull { it.id == goalId } ?: goalRepository.getById(goalId)
                if (goal == null) {
                    _state.value = _state.value.copy(isLoading = false, error = "Meta no encontrada")
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
                val goal = _state.value.goals.firstOrNull { it.id == goalId } ?: goalRepository.getById(goalId)
                if (goal == null) {
                    _state.value = _state.value.copy(isLoading = false, error = "Meta no encontrada")
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
