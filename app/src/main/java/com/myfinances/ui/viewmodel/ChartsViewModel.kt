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

data class ChartsItem(
    val id: String,
    val name: String,
    val amountCents: Long,
    val percent: Float
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
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val rootCategories: List<CategoryEntity> = emptyList(),
    val selectedRootCategoryId: String? = null,
    val subCategories: List<CategoryEntity> = emptyList(),
    val selectedSubCategoryId: String? = null,
    val items: List<ChartsItem> = emptyList(),
    val totalAmountCents: Long = 0,
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
        _state.value = _state.value.copy(selectedYear = year)
        refreshChart()
    }

    fun updateKind(kind: ChartsKind) {
        _state.value = _state.value.copy(selectedKind = kind)
        refreshChart()
    }

    fun updateViewMode(viewMode: ChartsViewMode) {
        _state.value = _state.value.copy(
            selectedView = viewMode,
            selectedRootCategoryId = if (viewMode == ChartsViewMode.SUB) _state.value.selectedRootCategoryId else null,
            selectedSubCategoryId = if (viewMode == ChartsViewMode.SUB) _state.value.selectedSubCategoryId else null,
            subCategories = emptyList()
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
        _state.value = _state.value.copy(selectedAccountId = accountId)
        refreshChart()
    }

    fun updateRootCategory(rootCategoryId: String?) {
        val uid = userUid ?: return
        _state.value = _state.value.copy(selectedRootCategoryId = rootCategoryId, selectedSubCategoryId = null)
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
        _state.value = _state.value.copy(selectedSubCategoryId = subCategoryId)
        refreshChart()
    }

    fun updateMonthIndex(index: Int) {
        _state.value = _state.value.copy(selectedMonthIndex = index.coerceIn(0, 12))
        refreshChart()
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

                val items = if (s.selectedView == ChartsViewMode.SUB) {
                    val rows = transactionRepository.getMonthlyTotalsBySubcategory(uid, accountId, year, kind)
                    buildItemsFromSubcategoryRows(rows, monthIndex, s.selectedRootCategoryId, s.selectedSubCategoryId)
                } else {
                    val rows = transactionRepository.getMonthlyTotalsByRootCategory(uid, accountId, year, kind)
                    buildItemsFromRootRows(rows, monthIndex)
                }

                val total = items.sumOf { it.amountCents }
                val normalized = if (total > 0) {
                    items.map { it.copy(percent = (it.amountCents.toFloat() / total.toFloat()).coerceIn(0f, 1f)) }
                } else {
                    items
                }

                _state.value = _state.value.copy(
                    items = normalized,
                    totalAmountCents = total,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
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
