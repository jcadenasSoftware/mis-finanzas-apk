package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.local.entity.LoanEntity
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.LoanPaymentRepository
import com.myfinances.data.repository.LoanRepository
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
    val selectedTab: String = "LENT", // LENT or BORROWED
    val accounts: List<AccountEntity> = emptyList(),
    val accountBalancesCents: Map<String, Long> = emptyMap(),
    val loanPaidCents: Map<String, Long> = emptyMap(),
    val loans: List<LoanEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoansState())
    val state: StateFlow<LoansState> = _state.asStateFlow()

    private val uid: String?
        get() = authRepository.currentUser?.uid

    private var lastLoadedUid: String? = null

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
        val tab = _state.value.selectedTab

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
                    .sortedWith(compareByDescending<com.myfinances.data.local.entity.AccountEntity> { balances[it.id] ?: Long.MIN_VALUE }
                        .thenBy { it.name.lowercase() })
                val loans = loanRepository.getFiltered(userUid, tab, "OPEN", null)

                val paidByLoan = loans
                    .map { loan ->
                        async { loan.id to loanPaymentRepository.sumPrincipalByLoan(userUid, loan.id) }
                    }
                    .map { it.await() }
                    .toMap()
                _state.value = _state.value.copy(
                    accounts = accounts,
                    accountBalancesCents = balances,
                    loanPaidCents = paidByLoan,
                    loans = loans,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createLoan(
        type: String,
        accountId: String,
        counterparty: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        notes: String?
    ) {
        val userUid = uid ?: return
        val currency = _state.value.accounts.firstOrNull { it.id == accountId }?.currency ?: ""

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
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
                _state.value = _state.value.copy(isLoading = false)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun registerPayment(loanId: String, accountId: String, principalCents: Long, occurredAtEpochSec: Long, note: String?) {
        val userUid = uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
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

                _state.value = _state.value.copy(isLoading = false)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
