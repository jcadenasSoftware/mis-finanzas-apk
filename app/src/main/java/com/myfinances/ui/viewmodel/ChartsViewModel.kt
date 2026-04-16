package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.dao.MonthlyCategoryDetailTotal
import com.myfinances.data.local.dao.MonthlyCategoryTotal
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ChartsKind(val label: String, val kind: String) {
    EXPENSE("Gastos", "EXPENSE"),
    INCOME("Ingresos", "INCOME")
}

enum class ChartsViewMode(val label: String) {
    ROOT("Categorías"),
    SUB("Subcategorías")
}

enum class ChartsType(val label: String) {
    BARS("Barras")
}

enum class ChartsDashboardTab(val label: String) {
    CATEGORIES("Categorías"),
    TREND("Tendencia")
}

data class ChartsItem(
    val id: String,
    val name: String,
    val amountCents: Long,
    val percent: Float
)

data class ChartsComparison(
    val label: String,
    val deltaPercent: Float?
)

enum class ChartsInsightTone {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}

data class ChartsInsight(
    val title: String,
    val subtitle: String,
    val tone: ChartsInsightTone
)

data class ChartsState(
    val years: List<Int> = emptyList(),
    val selectedYear: Int = LocalDate.now().year,
    val kinds: List<ChartsKind> = ChartsKind.entries,
    val selectedKind: ChartsKind = ChartsKind.EXPENSE,
    val views: List<ChartsViewMode> = ChartsViewMode.entries,
    val selectedView: ChartsViewMode = ChartsViewMode.ROOT,
    val months: List<String> = emptyList(),
    val selectedMonthIndex: Int = 0,
    val chartTypes: List<ChartsType> = ChartsType.entries,
    val selectedChartType: ChartsType = ChartsType.BARS,
    val dashboardTabs: List<ChartsDashboardTab> = ChartsDashboardTab.entries,
    val selectedDashboardTab: ChartsDashboardTab = ChartsDashboardTab.CATEGORIES,
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val rootCategories: List<CategoryEntity> = emptyList(),
    val selectedRootCategoryId: String? = null,
    val subCategories: List<CategoryEntity> = emptyList(),
    val selectedSubCategoryId: String? = null,
    val selectedItemId: String? = null,
    val expandedRootItemId: String? = null,
    val subItemsByRootId: Map<String, List<ChartsItem>> = emptyMap(),
    val items: List<ChartsItem> = emptyList(),
    val totalAmountCents: Long = 0,
    val summaryIncomeCents: Long = 0,
    val summaryExpenseCents: Long = 0,
    val summaryBalanceCents: Long = 0,
    val trendByMonthCents: List<Long> = emptyList(),
    val top3: List<ChartsItem> = emptyList(),
    val comparison: ChartsComparison? = null,
    val insights: List<ChartsInsight> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChartsState(
            years = buildList {
                val current = LocalDate.now().year
                for (y in current downTo (current - 5)) add(y)
            },
            months = listOf(
                "TOTAL",
                "ENERO",
                "FEBRERO",
                "MARZO",
                "ABRIL",
                "MAYO",
                "JUNIO",
                "JULIO",
                "AGOSTO",
                "SEPTIEMBRE",
                "OCTUBRE",
                "NOVIEMBRE",
                "DICIEMBRE"
            )
        )
    )
    val state: StateFlow<ChartsState> = _state.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    init {
        load()
    }

    fun load() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val accounts = accountRepository.getAccounts(uid)
                val roots = categoryRepository.getRoots(uid)

                val selectedRoot = _state.value.selectedRootCategoryId
                    ?: roots.firstOrNull()?.id
                val selectedView = _state.value.selectedView

                val subcats = if (selectedView == ChartsViewMode.SUB && selectedRoot != null) {
                    categoryRepository.getChildren(uid, selectedRoot)
                } else {
                    emptyList()
                }

                _state.value = _state.value.copy(
                    accounts = accounts,
                    rootCategories = roots,
                    selectedRootCategoryId = if (selectedView == ChartsViewMode.SUB) selectedRoot else null,
                    subCategories = subcats,
                    selectedSubCategoryId = if (selectedView == ChartsViewMode.SUB) _state.value.selectedSubCategoryId else null,
                    isLoading = false
                )

                refreshChart()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateYear(year: Int) {
        _state.value = _state.value.copy(
            selectedYear = year,
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )
        refreshChart()
    }

    fun updateKind(kind: ChartsKind) {
        _state.value = _state.value.copy(
            selectedKind = kind,
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )
        refreshChart()
    }

    fun updateViewMode(viewMode: ChartsViewMode) {
        _state.value = _state.value.copy(
            selectedView = viewMode,
            selectedRootCategoryId = if (viewMode == ChartsViewMode.SUB) _state.value.selectedRootCategoryId else null,
            selectedSubCategoryId = if (viewMode == ChartsViewMode.SUB) _state.value.selectedSubCategoryId else null,
            subCategories = emptyList(),
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )

        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                val roots = _state.value.rootCategories.ifEmpty { categoryRepository.getRoots(uid) }
                val selectedRoot = _state.value.selectedRootCategoryId ?: roots.firstOrNull()?.id
                val subcats = if (viewMode == ChartsViewMode.SUB && selectedRoot != null) {
                    categoryRepository.getChildren(uid, selectedRoot)
                } else {
                    emptyList()
                }
                _state.value = _state.value.copy(
                    selectedRootCategoryId = if (viewMode == ChartsViewMode.SUB) selectedRoot else null,
                    subCategories = subcats,
                    selectedSubCategoryId = null
                )
            } catch (_: Exception) {
            }
            refreshChart()
        }
    }

    fun updateAccount(accountId: String?) {
        _state.value = _state.value.copy(
            selectedAccountId = accountId,
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )
        refreshChart()
    }

    fun updateRootCategory(rootCategoryId: String?) {
        val uid = userUid ?: return
        _state.value = _state.value.copy(
            selectedRootCategoryId = rootCategoryId,
            selectedSubCategoryId = null,
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )
        viewModelScope.launch {
            try {
                val subcats = if (_state.value.selectedView == ChartsViewMode.SUB && !rootCategoryId.isNullOrBlank()) {
                    categoryRepository.getChildren(uid, rootCategoryId)
                } else {
                    emptyList()
                }
                _state.value = _state.value.copy(subCategories = subcats)
            } catch (_: Exception) {
                _state.value = _state.value.copy(subCategories = emptyList())
            }
            refreshChart()
        }
    }

    fun updateSubCategory(subCategoryId: String?) {
        _state.value = _state.value.copy(
            selectedSubCategoryId = subCategoryId,
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )
        refreshChart()
    }

    fun updateMonthIndex(index: Int) {
        _state.value = _state.value.copy(
            selectedMonthIndex = index.coerceIn(0, 12),
            expandedRootItemId = null,
            subItemsByRootId = emptyMap()
        )
        refreshChart()
    }

    fun updateDashboardTab(tab: ChartsDashboardTab) {
        _state.value = _state.value.copy(selectedDashboardTab = tab)
    }

    fun toggleSelectedItem(itemId: String) {
        val next = if (_state.value.selectedItemId == itemId) null else itemId
        _state.value = _state.value.copy(selectedItemId = next)
    }

    fun toggleExpandedRootItem(rootItemId: String) {
        val uid = userUid ?: return
        val current = _state.value
        val next = if (current.expandedRootItemId == rootItemId) null else rootItemId

        _state.value = current.copy(expandedRootItemId = next)

        if (next == null) return
        if (_state.value.subItemsByRootId.containsKey(rootItemId)) return

        viewModelScope.launch {
            try {
                val s = _state.value
                val rows = transactionRepository.getMonthlyTotalsBySubcategory(
                    uid,
                    s.selectedAccountId,
                    s.selectedYear,
                    s.selectedKind.kind
                )
                val subItems = buildItemsFromSubcategoryRows(
                    rows = rows,
                    monthIndex = s.selectedMonthIndex,
                    rootFilterId = rootItemId,
                    subFilterId = null
                )
                _state.value = _state.value.copy(
                    subItemsByRootId = _state.value.subItemsByRootId + (rootItemId to subItems)
                )
            } catch (_: Exception) {
            }
        }
    }

    fun clearSelectedItem() {
        _state.value = _state.value.copy(selectedItemId = null)
    }

    private fun refreshChart() {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                val s = _state.value
                val year = s.selectedYear
                val kind = s.selectedKind.kind
                val accountId = s.selectedAccountId
                val monthIndex = s.selectedMonthIndex

                var effectiveRootId = s.selectedRootCategoryId
                var effectiveSubId = s.selectedSubCategoryId

                if (s.selectedView == ChartsViewMode.SUB) {
                    if (effectiveRootId.isNullOrBlank()) {
                        effectiveRootId = s.rootCategories.firstOrNull()?.id
                    }

                    val subcats = if (!effectiveRootId.isNullOrBlank()) {
                        categoryRepository.getChildren(uid, effectiveRootId!!)
                    } else {
                        emptyList()
                    }

                    if (!effectiveSubId.isNullOrBlank() && subcats.none { it.id == effectiveSubId }) {
                        effectiveSubId = null
                    }

                    if (effectiveRootId != s.selectedRootCategoryId ||
                        effectiveSubId != s.selectedSubCategoryId ||
                        subcats != s.subCategories
                    ) {
                        _state.value = _state.value.copy(
                            selectedRootCategoryId = effectiveRootId,
                            selectedSubCategoryId = effectiveSubId,
                            subCategories = subcats
                        )
                    }
                }

                val trendRows = transactionRepository.getMonthlyTotalsByRootCategory(uid, accountId, year, kind)
                val trend = buildTrendFromRootRows(trendRows)

                val incomeRows = transactionRepository.getMonthlyTotalsByRootCategory(uid, accountId, year, ChartsKind.INCOME.kind)
                val expenseRows = transactionRepository.getMonthlyTotalsByRootCategory(uid, accountId, year, ChartsKind.EXPENSE.kind)
                val incomeTotal = sumForMonthIndexFromRootRows(incomeRows, monthIndex)
                val expenseTotal = sumForMonthIndexFromRootRows(expenseRows, monthIndex)
                val balanceTotal = incomeTotal - expenseTotal

                val items = if (s.selectedView == ChartsViewMode.SUB) {
                    val rows = transactionRepository.getMonthlyTotalsBySubcategory(uid, accountId, year, kind)
                    buildItemsFromSubcategoryRows(rows, monthIndex, effectiveRootId, effectiveSubId)
                } else {
                    buildItemsFromRootRows(trendRows, monthIndex)
                }

                val total = items.sumOf { it.amountCents }
                val normalized = if (total > 0) {
                    items.map { it.copy(percent = (it.amountCents.toFloat() / total.toFloat()).coerceIn(0f, 1f)) }
                } else {
                    items
                }

                val top3 = normalized.sortedByDescending { it.amountCents }.take(3)
                val comparison = buildComparisonLabel(year = year, monthIndex = monthIndex, trendByMonth = trend)
                val insights = buildInsights(
                    year = year,
                    monthIndex = monthIndex,
                    kind = s.selectedKind,
                    viewMode = s.selectedView,
                    rootRows = trendRows,
                    accountId = accountId,
                    selectedRootCategoryId = s.selectedRootCategoryId,
                    selectedSubCategoryId = s.selectedSubCategoryId,
                    top3 = top3
                )

                _state.value = _state.value.copy(
                    items = normalized,
                    totalAmountCents = total,
                    summaryIncomeCents = incomeTotal,
                    summaryExpenseCents = expenseTotal,
                    summaryBalanceCents = balanceTotal,
                    trendByMonthCents = trend,
                    top3 = top3,
                    comparison = comparison,
                    insights = insights,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun buildComparisonLabel(year: Int, monthIndex: Int, trendByMonth: List<Long>): ChartsComparison? {
        if (trendByMonth.size < 12) return null

        return if (monthIndex in 1..12) {
            val cur = trendByMonth.getOrNull(monthIndex - 1) ?: return null
            val prev = trendByMonth.getOrNull(monthIndex - 2)
            if (prev == null) {
                ChartsComparison(label = "Vs mes anterior", deltaPercent = null)
            } else {
                val delta = if (prev == 0L) null else ((cur - prev).toFloat() / prev.toFloat())
                ChartsComparison(label = "Vs mes anterior", deltaPercent = delta)
            }
        } else if (monthIndex == 0) {
            ChartsComparison(label = "Total ${year}", deltaPercent = null)
        } else {
            null
        }
    }

    private suspend fun buildInsights(
        year: Int,
        monthIndex: Int,
        kind: ChartsKind,
        viewMode: ChartsViewMode,
        rootRows: List<MonthlyCategoryTotal>,
        accountId: String?,
        selectedRootCategoryId: String?,
        selectedSubCategoryId: String?,
        top3: List<ChartsItem>
    ): List<ChartsInsight> {
        val uid = userUid ?: return emptyList()

        val insights = mutableListOf<ChartsInsight>()
        val maxItem = top3.firstOrNull()

        if (maxItem != null) {
            val pct = (maxItem.percent * 100f).coerceIn(0f, 100f)
            insights.add(
                ChartsInsight(
                    title = "${maxItem.name}",
                    subtitle = "Representa ${String.format(java.util.Locale("es"), "%.0f", pct)}% del total.",
                    tone = ChartsInsightTone.NEUTRAL
                )
            )
        }

        if (monthIndex in 2..12) {
            val curTotal = when (viewMode) {
                ChartsViewMode.ROOT -> amountForRootRowsTotal(rootRows, monthIndex)
                ChartsViewMode.SUB -> {
                    val rows = transactionRepository.getMonthlyTotalsBySubcategory(uid, accountId, year, kind.kind)
                    amountForSubRowsTotal(rows, monthIndex, selectedRootCategoryId, selectedSubCategoryId)
                }
            }
            val prevTotal = when (viewMode) {
                ChartsViewMode.ROOT -> amountForRootRowsTotal(rootRows, monthIndex - 1)
                ChartsViewMode.SUB -> {
                    val rows = transactionRepository.getMonthlyTotalsBySubcategory(uid, accountId, year, kind.kind)
                    amountForSubRowsTotal(rows, monthIndex - 1, selectedRootCategoryId, selectedSubCategoryId)
                }
            }

            if (prevTotal > 0L) {
                val delta = ((curTotal - prevTotal).toFloat() / prevTotal.toFloat())
                val absPct = kotlin.math.abs(delta) * 100f
                val signWord = if (delta >= 0f) "más" else "menos"
                val tone = when {
                    delta < 0f -> ChartsInsightTone.POSITIVE
                    delta > 0f && kind == ChartsKind.EXPENSE -> ChartsInsightTone.NEGATIVE
                    delta > 0f -> ChartsInsightTone.POSITIVE
                    else -> ChartsInsightTone.NEUTRAL
                }
                insights.add(
                    ChartsInsight(
                        title = "${String.format(java.util.Locale("es"), "%.0f", absPct)}% ${signWord} en ${kind.label.lowercase()}",
                        subtitle = "Respecto al mes anterior.",
                        tone = tone
                    )
                )
            }
        }

        return insights.take(2)
    }

    private fun amountForRootRowsTotal(rows: List<MonthlyCategoryTotal>, month: Int): Long {
        var sum = 0L
        for (r in rows) {
            if (r.month == month) sum += r.totalAmountCents
        }
        return sum
    }

    private fun amountForSubRowsTotal(
        rows: List<MonthlyCategoryDetailTotal>,
        month: Int,
        selectedRootCategoryId: String?,
        selectedSubCategoryId: String?
    ): Long {
        var sum = 0L
        for (r in rows) {
            if (r.month != month) continue
            if (!selectedRootCategoryId.isNullOrBlank() && r.rootCategoryId != selectedRootCategoryId) continue
            if (!selectedSubCategoryId.isNullOrBlank() && r.categoryId != selectedSubCategoryId) continue
            sum += r.totalAmountCents
        }
        return sum
    }

    private fun amountForRootIdAndMonth(rows: List<MonthlyCategoryTotal>, rootId: String, month: Int): Long? {
        var sum = 0L
        var found = false
        for (r in rows) {
            if (r.rootCategoryId == rootId && r.month == month) {
                sum += r.totalAmountCents
                found = true
            }
        }
        return if (found) sum else null
    }

    private fun amountForSubIdAndMonth(
        rows: List<MonthlyCategoryDetailTotal>,
        categoryId: String,
        month: Int,
        selectedRootCategoryId: String?,
        selectedSubCategoryId: String?
    ): Long? {
        var sum = 0L
        var found = false
        for (r in rows) {
            if (r.month != month) continue
            if (!selectedRootCategoryId.isNullOrBlank() && r.rootCategoryId != selectedRootCategoryId) continue
            if (!selectedSubCategoryId.isNullOrBlank() && r.categoryId != selectedSubCategoryId) continue
            if (r.categoryId == categoryId) {
                sum += r.totalAmountCents
                found = true
            }
        }
        return if (found) sum else null
    }

    private fun buildTrendFromRootRows(rows: List<MonthlyCategoryTotal>): List<Long> {
        val totals = LongArray(13)
        for (r in rows) {
            if (r.month in 1..12) {
                totals[r.month] += r.totalAmountCents
            }
        }
        return (1..12).map { totals[it] }
    }

    private fun sumForMonthIndexFromRootRows(rows: List<MonthlyCategoryTotal>, monthIndex: Int): Long {
        val totals = LongArray(13)
        for (r in rows) {
            if (r.month in 1..12) {
                totals[r.month] += r.totalAmountCents
            }
        }
        return if (monthIndex == 0) {
            totals.drop(1).sum()
        } else {
            totals.getOrElse(monthIndex) { 0L }
        }
    }

    private fun buildItemsFromRootRows(
        rows: List<MonthlyCategoryTotal>,
        monthIndex: Int
    ): List<ChartsItem> {
        val centsById = mutableMapOf<String, LongArray>()
        val nameById = mutableMapOf<String, String>()

        for (r in rows) {
            val months = centsById.getOrPut(r.rootCategoryId) { LongArray(13) }
            if (r.month in 1..12) {
                months[r.month] = r.totalAmountCents
            }
            nameById.putIfAbsent(r.rootCategoryId, r.rootCategoryName)
        }

        return centsById.entries
            .mapNotNull { (id, months) ->
                val value = if (monthIndex == 0) months.drop(1).sum() else months.getOrElse(monthIndex) { 0L }
                if (value == 0L) null
                else ChartsItem(id = id, name = nameById[id] ?: id, amountCents = value, percent = 0f)
            }
            .sortedByDescending { it.amountCents }
    }

    private fun buildItemsFromSubcategoryRows(
        rows: List<MonthlyCategoryDetailTotal>,
        monthIndex: Int,
        rootFilterId: String?,
        subFilterId: String?
    ): List<ChartsItem> {
        val centsById = mutableMapOf<String, LongArray>()
        val nameById = mutableMapOf<String, String>()
        val rootById = mutableMapOf<String, String>()

        for (r in rows) {
            val months = centsById.getOrPut(r.categoryId) { LongArray(13) }
            if (r.month in 1..12) {
                months[r.month] = r.totalAmountCents
            }
            nameById.putIfAbsent(r.categoryId, r.categoryName)
            rootById.putIfAbsent(r.categoryId, r.rootCategoryId)
        }

        return centsById.entries
            .asSequence()
            .filter { (id, _) ->
                val rootId = rootById[id]
                (rootFilterId.isNullOrBlank() || rootFilterId == rootId) &&
                    (subFilterId.isNullOrBlank() || subFilterId == id)
            }
            .mapNotNull { (id, months) ->
                val value = if (monthIndex == 0) months.drop(1).sum() else months.getOrElse(monthIndex) { 0L }
                if (value == 0L) null
                else ChartsItem(id = id, name = nameById[id] ?: id, amountCents = value, percent = 0f)
            }
            .sortedByDescending { it.amountCents }
            .toList()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
