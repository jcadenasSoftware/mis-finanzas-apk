package com.myfinances.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.dao.UserDao
import com.myfinances.data.local.entity.UserEntity
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.BudgetRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.ExchangeRateRepository
import com.myfinances.data.repository.GoalRepository
import com.myfinances.data.repository.LoanPaymentRepository
import com.myfinances.data.repository.LoanRepository
import com.myfinances.data.repository.TransactionRepository
import com.myfinances.data.repository.TransferRepository
import com.myfinances.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject

data class AccountWithBalance(
    val account: AccountEntity,
    val balanceCents: Long
)

data class DashboardBalancePoint(
    val dayOfMonth: Int,
    val balanceCents: Long
)

data class DashboardMonthlyHistoryItem(
    val label: String,
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val balanceCents: Long = 0
)

data class DashboardMonthlySummary(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val balanceCents: Long = 0,
    val trendDeltaCents: Long = 0,
    val points: List<DashboardBalancePoint> = emptyList(),
    val previousMonths: List<DashboardMonthlyHistoryItem> = emptyList(),
    val periodTotalIncomeCents: Long = 0,
    val periodTotalExpenseCents: Long = 0,
    val periodTotalBalanceCents: Long = 0
)

data class DashboardState(
    val userDisplayName: String = "",
    val userEmail: String = "",
    val accounts: List<AccountWithBalance> = emptyList(),
    val totalBalanceCents: Long = 0,
    val monthlySummary: DashboardMonthlySummary = DashboardMonthlySummary(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddAccountDialog: Boolean = false,
    val hasAccounts: Boolean = false,
    val hasTwoAccounts: Boolean = false,
    val hasRootCategories: Boolean = false,
    val hasSubCategories: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDao: UserDao,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    private var lastLoadedUid: String? = null

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                val uid = user?.uid
                if (uid.isNullOrBlank()) {
                    lastLoadedUid = null
                    _state.value = DashboardState()
                    return@collect
                }

                if (uid != lastLoadedUid) {
                    lastLoadedUid = uid
                    loadData()
                }
            }
        }
    }

    fun loadData() {
        val uid = userUid ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                userDisplayName = authRepository.currentUser?.displayName.orEmpty(),
                userEmail = authRepository.currentUser?.email ?: ""
            )

            try {
                ensureUserExists(uid)
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error during load", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: e.toString()
                )
            }
        }
    }

    private suspend fun ensureUserExists(uid: String) {
        val now = System.currentTimeMillis() / 1000
        val email = authRepository.currentUser?.email ?: ""
        userDao.upsert(
            UserEntity(
                uid = uid,
                email = email,
                createdAtEpochSec = now,
                updatedAtEpochSec = now
            )
        )
    }

    private suspend fun loadAccountsWithBalances(uid: String) {
        Log.d("DashboardViewModel", "Loading accounts with balances for user: $uid")
        val accounts = accountRepository.getAccounts(uid)
        Log.d("DashboardViewModel", "Found ${accounts.size} accounts in local database")
        
        val accountsWithBalance = accounts.map { account ->
            val balance = accountRepository.computeBalance(uid, account.id)
            Log.d("DashboardViewModel", "Account: ${account.name}, Balance: $balance")
            AccountWithBalance(account, balance)
        }
        val total = accountsWithBalance.sumOf { it.balanceCents }
        val monthlySummary = buildMonthlySummary(uid)
        Log.d("DashboardViewModel", "Total balance: $total")

        val rootCategories = runCatching { categoryRepository.getRoots(uid) }.getOrNull().orEmpty()
        val allCategories = runCatching { categoryRepository.getCategories(uid) }.getOrNull().orEmpty()
        val hasRoots = rootCategories.isNotEmpty()
        val hasSubs = allCategories.any { it.parentId != null }

        _state.value = _state.value.copy(
            accounts = accountsWithBalance,
            totalBalanceCents = total,
            monthlySummary = monthlySummary,
            userDisplayName = authRepository.currentUser?.displayName.orEmpty(),
            userEmail = authRepository.currentUser?.email ?: "",
            isLoading = false,
            hasAccounts = accountsWithBalance.isNotEmpty(),
            hasTwoAccounts = accountsWithBalance.size >= 2,
            hasRootCategories = hasRoots,
            hasSubCategories = hasSubs
        )
        Log.d("DashboardViewModel", "State updated with ${accountsWithBalance.size} accounts")
    }

    private suspend fun buildMonthlySummary(uid: String): DashboardMonthlySummary {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthStartEpochSec = calendar.timeInMillis / 1000

        val endCalendar = calendar.clone() as Calendar
        endCalendar.add(Calendar.MONTH, 1)
        endCalendar.add(Calendar.SECOND, -1)
        val monthEndEpochSec = endCalendar.timeInMillis / 1000

        val previousStartCalendar = calendar.clone() as Calendar
        previousStartCalendar.add(Calendar.MONTH, -1)
        val previousMonthStartEpochSec = previousStartCalendar.timeInMillis / 1000

        val previousEndCalendar = calendar.clone() as Calendar
        previousEndCalendar.add(Calendar.SECOND, -1)
        val previousMonthEndEpochSec = previousEndCalendar.timeInMillis / 1000

        val currentMonthTransactions = transactionRepository.getFiltered(
            userUid = uid,
            fromEpochSec = monthStartEpochSec,
            toEpochSec = monthEndEpochSec,
            limit = 1000
        )
        val previousMonthTransactions = transactionRepository.getFiltered(
            userUid = uid,
            fromEpochSec = previousMonthStartEpochSec,
            toEpochSec = previousMonthEndEpochSec,
            limit = 1000
        )
        val currentYear = calendar.get(Calendar.YEAR)

        val incomeKinds = setOf("INCOME", "LOAN_BORROWED_IN", "LOAN_REPAYMENT_PRINCIPAL_IN")
        val expenseKinds = setOf("EXPENSE", "LOAN_LENT_OUT", "LOAN_REPAYMENT_PRINCIPAL_OUT")

        val previousMonths = buildList {
            var monthOffset = 1
            while (true) {
                val item = buildMonthlyHistoryItem(
                    uid = uid,
                    monthOffset = monthOffset,
                    currentYear = currentYear,
                    incomeKinds = incomeKinds,
                    expenseKinds = expenseKinds
                ) ?: break
                add(item)
                monthOffset += 1
            }
        }

        val incomeCents = currentMonthTransactions
            .filter { it.kind in incomeKinds }
            .sumOf { kotlin.math.abs(it.amountCents) }
        val expenseCents = currentMonthTransactions
            .filter { it.kind in expenseKinds }
            .sumOf { kotlin.math.abs(it.amountCents) }
        val balanceCents = incomeCents - expenseCents

        val previousIncomeCents = previousMonthTransactions
            .filter { it.kind in incomeKinds }
            .sumOf { kotlin.math.abs(it.amountCents) }
        val previousExpenseCents = previousMonthTransactions
            .filter { it.kind in expenseKinds }
            .sumOf { kotlin.math.abs(it.amountCents) }
        val previousBalanceCents = previousIncomeCents - previousExpenseCents

        val dayNetMap = linkedMapOf<Int, Long>()
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        for (day in 1..currentDay) {
            dayNetMap[day] = 0L
        }

        for (transaction in currentMonthTransactions) {
            val txCalendar = Calendar.getInstance().apply {
                timeInMillis = transaction.occurredAtEpochSec * 1000
            }
            val day = txCalendar.get(Calendar.DAY_OF_MONTH)
            val delta = when (transaction.kind) {
                in incomeKinds -> kotlin.math.abs(transaction.amountCents)
                in expenseKinds -> -kotlin.math.abs(transaction.amountCents)
                else -> 0L
            }
            dayNetMap[day] = (dayNetMap[day] ?: 0L) + delta
        }

        var runningBalance = 0L
        val points = dayNetMap.map { (day, delta) ->
            runningBalance += delta
            DashboardBalancePoint(dayOfMonth = day, balanceCents = runningBalance)
        }

        val periodTotalIncomeCents = incomeCents + previousMonths.sumOf { it.incomeCents }
        val periodTotalExpenseCents = expenseCents + previousMonths.sumOf { it.expenseCents }
        val periodTotalBalanceCents = balanceCents + previousMonths.sumOf { it.balanceCents }

        return DashboardMonthlySummary(
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            balanceCents = balanceCents,
            trendDeltaCents = balanceCents - previousBalanceCents,
            points = points,
            previousMonths = previousMonths,
            periodTotalIncomeCents = periodTotalIncomeCents,
            periodTotalExpenseCents = periodTotalExpenseCents,
            periodTotalBalanceCents = periodTotalBalanceCents
        )
    }

    private suspend fun buildMonthlyHistoryItem(
        uid: String,
        monthOffset: Int,
        currentYear: Int,
        incomeKinds: Set<String>,
        expenseKinds: Set<String>
    ): DashboardMonthlyHistoryItem? {
        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -monthOffset)
        }
        if (startCalendar.get(Calendar.YEAR) != currentYear) {
            return null
        }
        val endCalendar = (startCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.SECOND, -1)
        }

        val transactions = transactionRepository.getFiltered(
            userUid = uid,
            fromEpochSec = startCalendar.timeInMillis / 1000,
            toEpochSec = endCalendar.timeInMillis / 1000,
            limit = 1000
        )

        val incomeCents = transactions
            .filter { it.kind in incomeKinds }
            .sumOf { kotlin.math.abs(it.amountCents) }
        val expenseCents = transactions
            .filter { it.kind in expenseKinds }
            .sumOf { kotlin.math.abs(it.amountCents) }

        val label = startCalendar.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale("es", "CO"))
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("es", "CO")) else it.toString() }
            ?: "Mes"

        return DashboardMonthlyHistoryItem(
            label = label,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            balanceCents = incomeCents - expenseCents
        )
    }

    fun refreshBalances() {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error during refreshBalances", e)
                _state.value = _state.value.copy(error = e.message ?: e.toString(), isLoading = false)
            }
        }
    }

    fun showAddAccountDialog() {
        _state.value = _state.value.copy(showAddAccountDialog = true)
    }

    fun hideAddAccountDialog() {
        _state.value = _state.value.copy(showAddAccountDialog = false)
    }

    fun createAccount(name: String, type: String, currency: String, iconKey: String?, colorHex: String?) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                accountRepository.create(uid, name, type, currency, iconKey, colorHex)
                hideAddAccountDialog()
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateAccountName(accountId: String, newName: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                accountRepository.updateName(uid, accountId, newName)
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateAccountDetails(accountId: String, name: String, type: String, iconKey: String?, colorHex: String?) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                accountRepository.updateDetails(uid, accountId, name, type, iconKey, colorHex)
                loadAccountsWithBalances(uid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteAccount(accountId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                val deleted = accountRepository.delete(uid, accountId)
                if (!deleted) {
                    _state.value = _state.value.copy(
                        error = "No se puede eliminar la cuenta porque tiene movimientos o saldo asociado. Deja el saldo en cero y elimina/mueve sus movimientos para poder eliminarla."
                    )
                } else {
                    loadAccountsWithBalances(uid)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
