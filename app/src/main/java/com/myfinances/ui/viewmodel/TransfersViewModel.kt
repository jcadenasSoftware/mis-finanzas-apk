package com.myfinances.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.dao.TransferWithDetails
import com.myfinances.data.local.entity.AccountEntity
import com.myfinances.data.repository.AccountRepository
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TransfersState(
    val transfers: List<TransferWithDetails> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedYear: Int? = null,
    val selectedMonth: Int? = null,
    val availableMonthsYearMonth: List<Pair<Int, Int>> = emptyList(),
    val searchQuery: String = "",
    val totalTransferredCents: Long = 0L,
    val outgoingCents: Long = 0L,
    val incomingCents: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class TransferFormState(
    val id: String? = null,
    val originalFromAccountId: String? = null,
    val originalToAccountId: String? = null,
    val originalAmountCents: Long? = null,
    val fromAccountId: String = "",
    val fromAccountBalanceCents: Long? = null,
    val toAccountId: String = "",
    val toAccountBalanceCents: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtEpochSec: Long = System.currentTimeMillis() / 1000,
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransfersState())
    val state: StateFlow<TransfersState> = _state.asStateFlow()

    private val _formState = MutableStateFlow(TransferFormState())
    val formState: StateFlow<TransferFormState> = _formState.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    private fun parseAmountToCents(amountText: String): Long? {
        val normalized = amountText.trim().replace(',', '.')
        if (normalized.isBlank()) return null
        return try {
            (normalized.toDouble() * 100).toLong()
        } catch (_: Exception) {
            null
        }
    }

    init {
        loadTransfers()
    }

    fun loadTransfers() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val transfers = transferRepository.getRecent(uid, 200)
                val accounts = accountRepository.getAccounts(uid)

                val availableMonths = computeAvailableMonths(transfers)
                val effectiveYear = _state.value.selectedYear ?: availableMonths.firstOrNull()?.first
                val effectiveMonth = _state.value.selectedMonth ?: availableMonths.firstOrNull()?.second

                _state.value = _state.value.copy(
                    accounts = accounts,
                    availableMonthsYearMonth = availableMonths,
                    selectedYear = effectiveYear,
                    selectedMonth = effectiveMonth
                )

                applyFiltersInternal(
                    uid = uid,
                    selectedAccountId = _state.value.selectedAccountId,
                    selectedYear = effectiveYear,
                    selectedMonth = effectiveMonth,
                    searchQuery = _state.value.searchQuery
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

    fun setMonth(year: Int, month: Int) {
        _state.value = _state.value.copy(selectedYear = year, selectedMonth = month)
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilters()
    }

    private fun applyFilters() {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                applyFiltersInternal(
                    uid = uid,
                    selectedAccountId = _state.value.selectedAccountId,
                    selectedYear = _state.value.selectedYear,
                    selectedMonth = _state.value.selectedMonth,
                    searchQuery = _state.value.searchQuery
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun applyFiltersInternal(
        uid: String,
        selectedAccountId: String?,
        selectedYear: Int?,
        selectedMonth: Int?,
        searchQuery: String
    ) {
        _state.value = _state.value.copy(isLoading = true)

        val (fromEpochSec, toEpochSec) = monthBounds(selectedYear, selectedMonth)
        var transfers = transferRepository.getFiltered(
            userUid = uid,
            accountId = selectedAccountId,
            fromEpochSec = fromEpochSec,
            toEpochSec = toEpochSec,
            limit = 200
        )

        val q = searchQuery.trim().lowercase()
        if (q.isNotBlank()) {
            transfers = transfers.filter {
                it.fromAccountName.lowercase().contains(q) ||
                    it.toAccountName.lowercase().contains(q) ||
                    (it.note ?: "").lowercase().contains(q)
            }
        }

        val total = transfers.sumOf { it.amountCents }
        val outgoing = if (selectedAccountId.isNullOrBlank()) {
            transfers.sumOf { it.amountCents }
        } else {
            transfers.filter { it.fromAccountId == selectedAccountId }.sumOf { it.amountCents }
        }
        val incoming = if (selectedAccountId.isNullOrBlank()) {
            0L
        } else {
            transfers.filter { it.toAccountId == selectedAccountId }.sumOf { it.amountCents }
        }

        _state.value = _state.value.copy(
            transfers = transfers,
            totalTransferredCents = total,
            outgoingCents = outgoing,
            incomingCents = incoming,
            isLoading = false,
            error = null
        )
    }

    private fun computeAvailableMonths(transfers: List<TransferWithDetails>): List<Pair<Int, Int>> {
        val set = linkedSetOf<Pair<Int, Int>>()
        val cal = Calendar.getInstance()
        transfers.forEach { tr ->
            cal.timeInMillis = tr.occurredAtEpochSec * 1000L
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            set.add(y to m)
        }
        return set.toList().sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }

    private fun monthBounds(year: Int?, month: Int?): Pair<Long?, Long?> {
        if (year == null || month == null) return null to null
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis / 1000
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.SECOND, -1)
        val to = cal.timeInMillis / 1000
        return from to to
    }

    fun initForm(transferId: String? = null) {
        val uid = userUid ?: return
        viewModelScope.launch {
            _formState.value = TransferFormState(isLoading = true)
            try {
                val accounts = accountRepository.getAccounts(uid)

                if (transferId != null) {
                    val transfer = transferRepository.getById(transferId)
                    if (transfer != null) {
                        val originalAmount = transfer.amountCents
                        val originalFromId = transfer.fromAccountId
                        val originalToId = transfer.toAccountId

                        val fromBalanceNow = accountRepository.computeBalance(uid, originalFromId)
                        val toBalanceNow = accountRepository.computeBalance(uid, originalToId)

                        val fromBase = fromBalanceNow + originalAmount
                        val toBase = toBalanceNow - originalAmount

                        _formState.value = TransferFormState(
                            id = transfer.id,
                            originalFromAccountId = originalFromId,
                            originalToAccountId = originalToId,
                            originalAmountCents = originalAmount,
                            fromAccountId = transfer.fromAccountId,
                            fromAccountBalanceCents = fromBase,
                            toAccountId = transfer.toAccountId,
                            toAccountBalanceCents = toBase,
                            amountText = (transfer.amountCents / 100.0).toString(),
                            note = transfer.note ?: "",
                            occurredAtEpochSec = transfer.occurredAtEpochSec,
                            accounts = accounts,
                            isLoading = false
                        )
                        return@launch
                    }
                }

                val defaultFromId = accounts.getOrNull(0)?.id ?: ""
                val defaultToId = accounts.getOrNull(1)?.id ?: ""

                _formState.value = TransferFormState(
                    accounts = accounts,
                    fromAccountId = defaultFromId,
                    fromAccountBalanceCents = defaultFromId.ifBlank { null }?.let { accountRepository.computeBalance(uid, it) },
                    toAccountId = defaultToId,
                    toAccountBalanceCents = defaultToId.ifBlank { null }?.let { accountRepository.computeBalance(uid, it) },
                    isLoading = false
                )
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateFormFromAccount(accountId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            val current = _formState.value

            val balance = try {
                val now = accountRepository.computeBalance(uid, accountId)
                val originalAmount = current.originalAmountCents
                when {
                    originalAmount != null && accountId == current.originalFromAccountId -> now + originalAmount
                    originalAmount != null && accountId == current.originalToAccountId -> now - originalAmount
                    else -> now
                }
            } catch (_: Exception) {
                null
            }

            val newToId = if (current.toAccountId == accountId) "" else current.toAccountId
            val newToBalance = if (newToId.isBlank()) null else {
                try {
                    val now = accountRepository.computeBalance(uid, newToId)
                    val originalAmount = current.originalAmountCents
                    when {
                        originalAmount != null && newToId == current.originalFromAccountId -> now + originalAmount
                        originalAmount != null && newToId == current.originalToAccountId -> now - originalAmount
                        else -> now
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _formState.value = current.copy(
                fromAccountId = accountId,
                fromAccountBalanceCents = balance,
                toAccountId = newToId,
                toAccountBalanceCents = newToBalance,
                error = null
            )
        }
    }

    fun updateFormToAccount(accountId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            val current = _formState.value

            val balance = try {
                val now = accountRepository.computeBalance(uid, accountId)
                val originalAmount = current.originalAmountCents
                when {
                    originalAmount != null && accountId == current.originalFromAccountId -> now + originalAmount
                    originalAmount != null && accountId == current.originalToAccountId -> now - originalAmount
                    else -> now
                }
            } catch (_: Exception) {
                null
            }

            val newFromId = if (current.fromAccountId == accountId) "" else current.fromAccountId
            val newFromBalance = if (newFromId.isBlank()) null else {
                try {
                    val now = accountRepository.computeBalance(uid, newFromId)
                    val originalAmount = current.originalAmountCents
                    when {
                        originalAmount != null && newFromId == current.originalFromAccountId -> now + originalAmount
                        originalAmount != null && newFromId == current.originalToAccountId -> now - originalAmount
                        else -> now
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _formState.value = current.copy(
                toAccountId = accountId,
                toAccountBalanceCents = balance,
                fromAccountId = newFromId,
                fromAccountBalanceCents = newFromBalance,
                error = null
            )
        }
    }

    fun swapAccounts() {
        val current = _formState.value
        if (current.fromAccountId.isBlank() && current.toAccountId.isBlank()) return
        _formState.value = current.copy(
            fromAccountId = current.toAccountId,
            fromAccountBalanceCents = current.toAccountBalanceCents,
            toAccountId = current.fromAccountId,
            toAccountBalanceCents = current.fromAccountBalanceCents,
            error = null
        )
    }

    fun updateFormAmount(amount: String) {
        _formState.value = _formState.value.copy(amountText = amount, error = null)
    }

    fun updateFormNote(note: String) {
        _formState.value = _formState.value.copy(note = note)
    }

    fun updateFormDate(epochSec: Long) {
        _formState.value = _formState.value.copy(occurredAtEpochSec = epochSec)
    }

    fun saveTransfer() {
        val uid = userUid ?: return
        val form = _formState.value

        if (form.fromAccountId.isBlank() || form.toAccountId.isBlank() || form.amountText.isBlank()) {
            _formState.value = form.copy(error = "Completa todos los campos")
            return
        }

        if (form.fromAccountId == form.toAccountId) {
            _formState.value = form.copy(error = "Las cuentas deben ser diferentes")
            return
        }

        val amountCents = parseAmountToCents(form.amountText)
        if (amountCents == null || amountCents <= 0) {
            _formState.value = form.copy(error = "Monto inválido")
            return
        }

        val available = form.fromAccountBalanceCents
        if (available != null && amountCents > available) {
            _formState.value = form.copy(error = "Saldo insuficiente")
            return
        }

        viewModelScope.launch {
            _formState.value = form.copy(isLoading = true)
            try {
                if (form.id != null) {
                    transferRepository.update(
                        userUid = uid,
                        transferId = form.id,
                        fromAccountId = form.fromAccountId,
                        toAccountId = form.toAccountId,
                        amountCents = amountCents,
                        occurredAtEpochSec = form.occurredAtEpochSec,
                        note = form.note.ifBlank { null }
                    )
                } else {
                    transferRepository.create(
                        userUid = uid,
                        fromAccountId = form.fromAccountId,
                        toAccountId = form.toAccountId,
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

    fun deleteTransfer(transferId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                transferRepository.delete(uid, transferId)
                loadTransfers()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
        _formState.value = _formState.value.copy(error = null)
    }
}
