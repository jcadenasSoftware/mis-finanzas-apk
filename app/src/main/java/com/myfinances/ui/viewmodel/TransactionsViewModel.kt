package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.dao.TransactionWithDetails
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
import javax.inject.Inject

enum class TransactionsPeriodPreset {
    TODAY,
    WEEK,
    MONTH,
    CUSTOM
}

data class TransactionsState(
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val fromEpochSec: Long? = null,
    val toEpochSec: Long? = null,
    val selectedPeriodPreset: TransactionsPeriodPreset = TransactionsPeriodPreset.MONTH,
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val selectedMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
    val availableMonthsYearMonth: List<Pair<Int, Int>> = emptyList(),
    val searchQuery: String = "",
    val totalIncomeCents: Long = 0L,
    val totalExpenseCents: Long = 0L,
    val balanceCents: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class TransactionFormState(
    val id: String? = null,
    val accountId: String = "",
    val accountBalanceCents: Long? = null,
    val categoryId: String = "",
    val kind: String = "EXPENSE",
    val amountText: String = "",
    val note: String = "",
    val occurredAtEpochSec: Long = System.currentTimeMillis() / 1000,
    val accounts: List<AccountEntity> = emptyList(),
    val rootCategories: List<CategoryEntity> = emptyList(),
    val subCategories: List<CategoryEntity> = emptyList(),
    val selectedRootCategoryId: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private suspend fun resolveCategoryFilterIds(
        userUid: String,
        categoryId: String?
    ): Set<String>? {
        val id = categoryId?.trim().orEmpty()
        if (id.isBlank()) return null

        val category = try {
            categoryRepository.getById(id)
        } catch (_: Exception) {
            null
        }

        if (category?.parentId != null) {
            return setOf(id)
        }

        val children = try {
            categoryRepository.getChildren(userUid, id)
        } catch (_: Exception) {
            emptyList()
        }

        return if (children.isNotEmpty()) {
            children.map { it.id }.toSet()
        } else {
            setOf(id)
        }
    }

    private fun CategoryEntity.isCompatibleWith(kind: String): Boolean {
        val k = this.kind.trim().uppercase()
        return k == "BOTH" || k == kind.trim().uppercase()
    }

    private fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, month - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val start = startOfDayEpochSec(cal.timeInMillis)
        cal.add(java.util.Calendar.MONTH, 1)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val end = endOfDayEpochSec(cal.timeInMillis)
        return start to end
    }

    private var initialApplied = false

    private val _state = MutableStateFlow(TransactionsState())
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    init {
        loadTransactions()
    }

    private fun startOfDayEpochSec(nowMillis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000
    }

    private fun endOfDayEpochSec(nowMillis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        return cal.timeInMillis / 1000
    }

    private fun currentPeriodRange(preset: TransactionsPeriodPreset, nowMillis: Long): Pair<Long?, Long?> {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        return when (preset) {
            TransactionsPeriodPreset.TODAY -> {
                startOfDayEpochSec(nowMillis) to endOfDayEpochSec(nowMillis)
            }

            TransactionsPeriodPreset.WEEK -> {
                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = startOfDayEpochSec(cal.timeInMillis)
                cal.add(java.util.Calendar.DAY_OF_YEAR, 6)
                val end = endOfDayEpochSec(cal.timeInMillis)
                start to end
            }

            TransactionsPeriodPreset.MONTH -> {
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val start = startOfDayEpochSec(cal.timeInMillis)
                cal.add(java.util.Calendar.MONTH, 1)
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val end = endOfDayEpochSec(cal.timeInMillis)
                start to end
            }

            TransactionsPeriodPreset.CUSTOM -> {
                null to null
            }
        }
    }

    private fun applyLocalSearch(transactions: List<TransactionWithDetails>, query: String): List<TransactionWithDetails> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return transactions
        return transactions.filter { t ->
            t.categoryName.lowercase().contains(q)
                || t.accountName.lowercase().contains(q)
                || (t.note?.lowercase()?.contains(q) == true)
        }
    }

    private fun computeTotals(transactions: List<TransactionWithDetails>): Triple<Long, Long, Long> {
        var income = 0L
        var expense = 0L
        transactions.forEach { t ->
            if (t.kind.trim().uppercase() == "INCOME") income += t.amountCents else expense += t.amountCents
        }
        return Triple(income, expense, income - expense)
    }

    private fun computeAvailableMonths(transactions: List<TransactionWithDetails>): List<Pair<Int, Int>> {
        val cal = java.util.Calendar.getInstance()
        val set = LinkedHashSet<Pair<Int, Int>>()
        for (t in transactions) {
            cal.timeInMillis = t.occurredAtEpochSec * 1000
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH) + 1
            set.add(year to month)
        }
        return set.toList().sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }

    fun loadTransactions() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val accounts = accountRepository.getAccounts(uid)
                val categories = categoryRepository.getCategories(uid)

                val s = _state.value
                val hasFilters = !s.selectedAccountId.isNullOrBlank()
                    || !s.selectedCategoryId.isNullOrBlank()
                    || s.fromEpochSec != null
                    || s.toEpochSec != null

                val categoryFilterIds = resolveCategoryFilterIds(uid, s.selectedCategoryId)
                val shouldFilterLocallyByCategory = categoryFilterIds != null && categoryFilterIds.size > 1

                val transactions = if (!hasFilters) {
                    transactionRepository.getRecent(uid, 100)
                } else {
                    val base = transactionRepository.getFiltered(
                        userUid = uid,
                        accountId = s.selectedAccountId,
                        categoryId = if (shouldFilterLocallyByCategory) null else s.selectedCategoryId,
                        fromEpochSec = s.fromEpochSec,
                        toEpochSec = s.toEpochSec,
                        limit = 100
                    )
                    if (shouldFilterLocallyByCategory) {
                        base.filter { t -> categoryFilterIds!!.contains(t.categoryId) }
                    } else {
                        base
                    }
                }

                val monthsSource = transactionRepository.getRecent(uid, 1000)
                val months = computeAvailableMonths(monthsSource)

                val filtered = applyLocalSearch(transactions, s.searchQuery)
                val (inc, exp, bal) = computeTotals(filtered)

                _state.value = _state.value.copy(
                    transactions = filtered,
                    accounts = accounts,
                    categories = categories,
                    availableMonthsYearMonth = months,
                    totalIncomeCents = inc,
                    totalExpenseCents = exp,
                    balanceCents = bal,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun applyInitialFilters(
        accountId: String?,
        categoryId: String?,
        fromEpochSec: Long?,
        toEpochSec: Long?
    ) {
        if (initialApplied) return
        initialApplied = true

        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedAccountId = accountId,
                selectedCategoryId = categoryId,
                fromEpochSec = fromEpochSec,
                toEpochSec = toEpochSec,
                isLoading = true
            )
            try {
                val accounts = accountRepository.getAccounts(uid)
                val categories = categoryRepository.getCategories(uid)

                val categoryFilterIds = resolveCategoryFilterIds(uid, categoryId)
                val shouldFilterLocallyByCategory = categoryFilterIds != null && categoryFilterIds.size > 1

                val transactions = if (accountId == null && categoryId == null && fromEpochSec == null && toEpochSec == null) {
                    transactionRepository.getRecent(uid, 100)
                } else {
                    val base = transactionRepository.getFiltered(
                        userUid = uid,
                        accountId = accountId,
                        categoryId = if (shouldFilterLocallyByCategory) null else categoryId,
                        fromEpochSec = fromEpochSec,
                        toEpochSec = toEpochSec,
                        limit = 100
                    )
                    if (shouldFilterLocallyByCategory) {
                        base.filter { t -> categoryFilterIds!!.contains(t.categoryId) }
                    } else {
                        base
                    }
                }
                _state.value = _state.value.copy(
                    accounts = accounts,
                    categories = categories,
                    transactions = transactions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun filterByAccount(accountId: String?) {
        _state.value = _state.value.copy(selectedAccountId = accountId)
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilters()
    }

    fun setPeriodPreset(preset: TransactionsPeriodPreset) {
        val nowMillis = System.currentTimeMillis()
        val (from, to) = currentPeriodRange(preset, nowMillis)
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        _state.value = _state.value.copy(
            selectedPeriodPreset = preset,
            selectedYear = year,
            selectedMonth = month,
            fromEpochSec = from,
            toEpochSec = to
        )
        applyFilters()
    }

    fun setMonth(year: Int, month: Int) {
        val (from, to) = monthRange(year, month)
        _state.value = _state.value.copy(
            selectedPeriodPreset = TransactionsPeriodPreset.MONTH,
            selectedYear = year,
            selectedMonth = month,
            fromEpochSec = from,
            toEpochSec = to
        )
        applyFilters()
    }

    fun setCustomPeriod(fromEpochSec: Long, toEpochSec: Long) {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = fromEpochSec * 1000L
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1

        _state.value = _state.value.copy(
            selectedPeriodPreset = TransactionsPeriodPreset.CUSTOM,
            selectedYear = year,
            selectedMonth = month,
            fromEpochSec = minOf(fromEpochSec, toEpochSec),
            toEpochSec = maxOf(fromEpochSec, toEpochSec)
        )
        applyFilters()
    }

    fun filterByCategory(categoryId: String?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
        applyFilters()
    }

    private fun applyFilters() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val s = _state.value
                val categoryFilterIds = resolveCategoryFilterIds(uid, s.selectedCategoryId)
                val shouldFilterLocallyByCategory = categoryFilterIds != null && categoryFilterIds.size > 1

                val base = transactionRepository.getFiltered(
                    userUid = uid,
                    accountId = s.selectedAccountId,
                    categoryId = if (shouldFilterLocallyByCategory) null else s.selectedCategoryId,
                    fromEpochSec = s.fromEpochSec,
                    toEpochSec = s.toEpochSec,
                    limit = 100
                )

                val transactions = if (shouldFilterLocallyByCategory) {
                    base.filter { t -> categoryFilterIds!!.contains(t.categoryId) }
                } else {
                    base
                }
                val monthsSource = transactionRepository.getRecent(uid, 1000)
                val months = computeAvailableMonths(monthsSource)
                val filtered = applyLocalSearch(transactions, _state.value.searchQuery)
                val (inc, exp, bal) = computeTotals(filtered)
                _state.value = _state.value.copy(
                    transactions = filtered,
                    availableMonthsYearMonth = months,
                    totalIncomeCents = inc,
                    totalExpenseCents = exp,
                    balanceCents = bal,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun initForm(transactionId: String? = null) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _formState.value = TransactionFormState(isLoading = true)
            try {
                val accounts = accountRepository.getAccounts(uid)
                val rootCategories = categoryRepository.getRoots(uid)

                if (transactionId != null) {
                    val transaction = transactionRepository.getById(transactionId)
                    if (transaction != null) {
                        val category = categoryRepository.getById(transaction.categoryId)
                        val rootId = category?.parentId ?: category?.id
                        val subCategories = if (rootId != null) {
                            categoryRepository.getChildren(uid, rootId)
                        } else emptyList()

                        _formState.value = TransactionFormState(
                            id = transaction.id,
                            accountId = transaction.accountId,
                            accountBalanceCents = accountRepository.computeBalance(uid, transaction.accountId),
                            categoryId = transaction.categoryId,
                            kind = transaction.kind,
                            amountText = (transaction.amountCents / 100.0).toString(),
                            note = transaction.note ?: "",
                            occurredAtEpochSec = transaction.occurredAtEpochSec,
                            accounts = accounts,
                            rootCategories = rootCategories,
                            subCategories = subCategories,
                            selectedRootCategoryId = rootId,
                            isLoading = false
                        )
                        return@launch
                    }
                }

                _formState.value = TransactionFormState(
                    accounts = accounts,
                    rootCategories = rootCategories,
                    accountId = accounts.firstOrNull()?.id ?: "",
                    accountBalanceCents = accounts.firstOrNull()?.id?.let { accountRepository.computeBalance(uid, it) },
                    isLoading = false
                )
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateFormAccount(accountId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            val balance = try {
                accountRepository.computeBalance(uid, accountId)
            } catch (e: Exception) {
                null
            }
            _formState.value = _formState.value.copy(
                accountId = accountId,
                accountBalanceCents = balance
            )
        }
    }

    fun updateFormRootCategory(categoryId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            val subCategories = categoryRepository
                .getChildren(uid, categoryId)
                .filter { it.isCompatibleWith(_formState.value.kind) }
            _formState.value = _formState.value.copy(
                selectedRootCategoryId = categoryId,
                subCategories = subCategories,
                categoryId = if (subCategories.isEmpty()) categoryId else ""
            )
        }
    }

    fun updateFormCategory(categoryId: String) {
        _formState.value = _formState.value.copy(categoryId = categoryId)
    }

    fun updateFormKind(kind: String) {
        val uid = userUid ?: return
        val current = _formState.value
        _formState.value = current.copy(kind = kind)

        viewModelScope.launch {
            val next = _formState.value
            val selectedRootId = next.selectedRootCategoryId
            val selectedCategoryId = next.categoryId

            // If selected root is not compatible with new kind, clear selections.
            val root = next.rootCategories.firstOrNull { it.id == selectedRootId }
            if (root != null && !root.isCompatibleWith(kind)) {
                _formState.value = next.copy(
                    selectedRootCategoryId = null,
                    subCategories = emptyList(),
                    categoryId = ""
                )
                return@launch
            }

            if (!selectedRootId.isNullOrBlank()) {
                val filteredSubs = categoryRepository
                    .getChildren(uid, selectedRootId)
                    .filter { it.isCompatibleWith(kind) }

                val effectiveCategoryId = if (filteredSubs.isNotEmpty()) {
                    selectedCategoryId.takeIf { id -> filteredSubs.any { it.id == id } }.orEmpty()
                } else {
                    // No children: root itself is selected.
                    selectedRootId
                }

                _formState.value = _formState.value.copy(
                    subCategories = filteredSubs,
                    categoryId = effectiveCategoryId
                )
            }
        }
    }

    fun updateFormAmount(amount: String) {
        _formState.value = _formState.value.copy(amountText = amount)
    }

    fun updateFormNote(note: String) {
        _formState.value = _formState.value.copy(note = note)
    }

    fun updateFormDate(epochSec: Long) {
        _formState.value = _formState.value.copy(occurredAtEpochSec = epochSec)
    }

    fun saveTransaction() {
        val uid = userUid ?: return
        val form = _formState.value

        if (form.accountId.isBlank() || form.categoryId.isBlank() || form.amountText.isBlank()) {
            _formState.value = form.copy(error = "Completa todos los campos")
            return
        }

        val amountCents = try {
            (form.amountText.toDouble() * 100).toLong()
        } catch (e: Exception) {
            _formState.value = form.copy(error = "Monto inválido")
            return
        }

        viewModelScope.launch {
            _formState.value = form.copy(isLoading = true)
            try {
                if (form.id != null) {
                    transactionRepository.update(
                        userUid = uid,
                        transactionId = form.id,
                        accountId = form.accountId,
                        categoryId = form.categoryId,
                        kind = form.kind,
                        amountCents = amountCents,
                        occurredAtEpochSec = form.occurredAtEpochSec,
                        note = form.note.ifBlank { null }
                    )
                } else {
                    transactionRepository.create(
                        userUid = uid,
                        accountId = form.accountId,
                        categoryId = form.categoryId,
                        kind = form.kind,
                        amountCents = amountCents,
                        occurredAtEpochSec = form.occurredAtEpochSec,
                        note = form.note.ifBlank { null }
                    )
                }
                _formState.value = _formState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                transactionRepository.delete(uid, transactionId)
                loadTransactions()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun prepareNewForm() {
        _formState.value = TransactionFormState(isLoading = true)
    }

    fun consumeSaved() {
        val f = _formState.value
        if (f.isSaved) {
            _formState.value = f.copy(isSaved = false)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
        _formState.value = _formState.value.copy(error = null)
    }
}
