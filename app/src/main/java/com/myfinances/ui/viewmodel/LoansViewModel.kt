package com.jcadenas.xpendz.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.data.local.entity.LoanMovementEntity
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.LoanMovementRepository
import com.jcadenas.xpendz.data.repository.LoanPaymentRepository
import com.jcadenas.xpendz.data.repository.LoanRepository
import com.jcadenas.xpendz.ui.model.LoanMovementUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoansState(
    val isLoading: Boolean = false,
    val isSavingLoan: Boolean = false,
    val isSavingPayment: Boolean = false,
    val isSavingEdit: Boolean = false,
    val selectedTab: String = "LENT", // LENT or BORROWED
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalancesCents: Map<String, Long> = emptyMap(),
    val loanPaidCents: Map<String, Long> = emptyMap(),
    val lentLoans: List<LoanEntity> = emptyList(),
    val borrowedLoans: List<LoanEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val totalLentRemainingCents: Long = 0L,
    val totalBorrowedRemainingCents: Long = 0L,
    val loanMovements: Map<String, List<LoanMovementUiModel>> = emptyMap(),
    val loanMovementsError: Map<String, String?> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val loanMovementRepository: LoanMovementRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(LoansState())
    val state: StateFlow<LoansState> = _state.asStateFlow()

    private val uid: String?
        get() = authRepository.currentUser?.uid

    private var lastLoadedUid: String? = null
    private val observedLoanIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collectLatest { user ->
                val userUid = user?.uid
                if (userUid.isNullOrBlank()) {
                    lastLoadedUid = null
                    _state.value = LoansState()
                    return@collectLatest
                }
                if (userUid != lastLoadedUid) {
                    lastLoadedUid = userUid
                    refresh()

                    // Migración automática de pagos históricos (se ejecuta solo una vez)
                    val migrationKey = "loan_payment_migration_v1_$userUid"
                    val migrationCompleted = sharedPreferences.getBoolean(migrationKey, false)
                    if (!migrationCompleted) {
                        try {
                            val result = migrateHistoricalPayments()
                            sharedPreferences.edit().putBoolean(migrationKey, true).apply()
                        } catch (e: Exception) {
                            // Si falla, no marcar como completada para reintentar en el siguiente inicio
                        }
                    }
                }
            }
        }
    }

    fun setTab(tab: String) {
        _state.value = _state.value.copy(selectedTab = tab)
        refresh()
    }

    fun refresh() {
        val userUid = uid ?: return
        val selectedTab = _state.value.selectedTab

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val accountsUnsorted = accountRepository.getAccounts(userUid)
                val balances = accountsUnsorted
                    .map { acc ->
                        async { acc.id to accountRepository.computeBalance(userUid, acc.id) }
                    }
                    .map { it.await() }
                    .toMap()
                val accounts = accountsUnsorted
                    .sortedWith(compareByDescending<com.jcadenas.xpendz.data.local.entity.AccountEntity> { balances[it.id] ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() })

                val lentLoansDeferred = async { loanRepository.getFiltered(userUid, "LENT", "OPEN", null) }
                val borrowedLoansDeferred = async { loanRepository.getFiltered(userUid, "BORROWED", "OPEN", null) }
                val lentLoans = lentLoansDeferred.await()
                val borrowedLoans = borrowedLoansDeferred.await()

                val allLoans = lentLoans + borrowedLoans
                val paidByLoan = allLoans
                    .map { loan ->
                        async { loan.id to loanPaymentRepository.sumPrincipalByLoan(userUid, loan.id) }
                    }
                    .map { it.await() }
                    .toMap()

                val totalLentRemaining = lentLoans.sumOf { loan ->
                    val paid = paidByLoan[loan.id] ?: 0L
                    (loan.principalCents - paid).coerceAtLeast(0L)
                }
                val totalBorrowedRemaining = borrowedLoans.sumOf { loan ->
                    val paid = paidByLoan[loan.id] ?: 0L
                    (loan.principalCents - paid).coerceAtLeast(0L)
                }

                val loans = when (selectedTab) {
                    "BORROWED" -> borrowedLoans
                    else -> lentLoans
                }.sortedByDescending { loan ->
                    val paid = paidByLoan[loan.id] ?: 0L
                    (loan.principalCents - paid).coerceAtLeast(0L)
                }

                _state.value = _state.value.copy(
                    accounts = accounts,
                    accountBalancesCents = balances,
                    loanPaidCents = paidByLoan,
                    lentLoans = lentLoans,
                    borrowedLoans = borrowedLoans,
                    loans = loans,
                    totalLentRemainingCents = totalLentRemaining,
                    totalBorrowedRemainingCents = totalBorrowedRemaining,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    suspend fun createLoan(
        type: String,
        accountId: String,
        counterparty: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        notes: String?
    ): String? {
        // Protección contra doble clic
        if (_state.value.isSavingLoan) return null
        
        val userUid = uid ?: return "Usuario no autenticado"
        val currency = _state.value.accounts.firstOrNull { it.id == accountId }?.currency ?: ""

        _state.value = _state.value.copy(isLoading = true, isSavingLoan = true, error = null)
        return try {
            loanRepository.create(
                userUid = userUid,
                type = type,
                counterpartyName = counterparty,
                accountId = accountId,
                currency = currency,
                principalCents = principalCents,
                occurredAtEpochSec = occurredAtEpochSec,
                notes = notes
            )
            _state.value = _state.value.copy(isLoading = false, isSavingLoan = false)
            refresh()
            null
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, isSavingLoan = false)
            e.message ?: "Error al crear el préstamo"
        }
    }

    fun registerPayment(loanId: String, accountId: String, principalCents: Long, occurredAtEpochSec: Long, note: String?) {
        // Protección contra doble clic
        if (_state.value.isSavingPayment) return
        
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, isSavingPayment = true, error = null)
            try {
                loanPaymentRepository.create(
                    userUid = userUid,
                    loanId = loanId,
                    accountId = accountId,
                    principalCents = principalCents,
                    occurredAtEpochSec = occurredAtEpochSec,
                    note = note
                )

                val loan = loanRepository.getById(loanId)
                if (loan != null && loan.status == "OPEN") {
                    val paid = loanPaymentRepository.sumPrincipalByLoan(userUid, loanId)
                    if (paid >= loan.principalCents) {
                        loanRepository.close(userUid, loanId)
                    }
                }

                _state.value = _state.value.copy(isLoading = false, isSavingPayment = false)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isSavingPayment = false, error = e.message)
            }
        }
    }

    fun updateLoan(
        loanId: String,
        counterpartyName: String? = null,
        accountId: String? = null,
        principalCents: Long? = null,
        notes: String? = null
    ) {
        // Protección contra doble clic
        if (_state.value.isSavingEdit) return
        
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, isSavingEdit = true, error = null)
            try {
                loanRepository.updateLoan(
                    userUid = userUid,
                    loanId = loanId,
                    counterpartyName = counterpartyName,
                    accountId = accountId,
                    principalCents = principalCents,
                    notes = notes
                )
                _state.value = _state.value.copy(isLoading = false, isSavingEdit = false)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isSavingEdit = false, error = e.message)
            }
        }
    }

    suspend fun getLoanEditData(loanId: String): LoanEditData? {
        val userUid = uid ?: return null
        val loan = loanRepository.getById(loanId) ?: return null
        val paidCents = loanPaymentRepository.sumPrincipalByLoan(userUid, loanId)
        val pendingCents = (loan.principalCents - paidCents).coerceAtLeast(0L)
        val progressPercent = if (loan.principalCents > 0) {
            ((paidCents * 100) / loan.principalCents).toInt()
        } else {
            0
        }
        
        return LoanEditData(
            originalPrincipalCents = loan.principalCents,
            paidCents = paidCents,
            pendingCents = pendingCents,
            progressPercent = progressPercent,
            currency = loan.currency,
            status = loan.status
        )
    }

    fun calculateNewPending(newPrincipalCents: Long, paidCents: Long): Long {
        return (newPrincipalCents - paidCents).coerceAtLeast(0L)
    }

    fun calculateNewProgress(newPrincipalCents: Long, paidCents: Long): Int {
        return if (newPrincipalCents > 0) {
            ((paidCents * 100) / newPrincipalCents).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    data class LoanEditData(
        val originalPrincipalCents: Long,
        val paidCents: Long,
        val pendingCents: Long,
        val progressPercent: Int,
        val currency: String,
        val status: String
    )

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun loadLoanMovements(loanId: String) {
        val userUid = uid ?: return
        viewModelScope.launch {
            try {
                val movements = loanMovementRepository.getByLoan(userUid, loanId)
                val loan = loanRepository.getById(loanId)
                val currency = loan?.currency ?: "USD"
                val uiModels = movements.map { entity ->
                    LoanMovementUiModel.fromEntity(entity, currency)
                }
                _state.value = _state.value.copy(
                    loanMovements = _state.value.loanMovements + (loanId to uiModels),
                    loanMovementsError = _state.value.loanMovementsError + (loanId to null)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loanMovementsError = _state.value.loanMovementsError + (loanId to e.message)
                )
            }
        }
    }

    fun observeLoanMovements(loanId: String) {
        val userUid = uid ?: return
        if (loanId in observedLoanIds) return
        observedLoanIds.add(loanId)

        viewModelScope.launch {
            try {
                loanMovementRepository.observeByLoan(userUid, loanId).collectLatest { movements ->
                    val loan = loanRepository.getById(loanId)
                    val currency = loan?.currency ?: "USD"
                    val uiModels = movements.map { entity ->
                        LoanMovementUiModel.fromEntity(entity, currency)
                    }
                    _state.value = _state.value.copy(
                        loanMovements = _state.value.loanMovements + (loanId to uiModels)
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    suspend fun migrateHistoricalPayments(): LoanPaymentRepository.MigrationResult {
        val userUid = uid ?: return LoanPaymentRepository.MigrationResult(0, 0, 0, "Usuario no autenticado")
        return loanPaymentRepository.migrateHistoricalPayments(userUid)
    }
}
