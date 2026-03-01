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
import javax.inject.Inject

data class TransfersState(
    val transfers: List<TransferWithDetails> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class TransferFormState(
    val id: String? = null,
    val fromAccountId: String = "",
    val fromAccountBalanceCents: Long? = null,
    val toAccountId: String = "",
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

    init {
        loadTransfers()
    }

    fun loadTransfers() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val transfers = transferRepository.getRecent(uid, 100)
                val accounts = accountRepository.getAccounts(uid)
                _state.value = _state.value.copy(
                    transfers = transfers,
                    accounts = accounts,
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

    private fun applyFilters() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val transfers = transferRepository.getFiltered(
                    userUid = uid,
                    accountId = _state.value.selectedAccountId,
                    fromEpochSec = null,
                    toEpochSec = null,
                    limit = 100
                )
                _state.value = _state.value.copy(transfers = transfers, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
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
                        _formState.value = TransferFormState(
                            id = transfer.id,
                            fromAccountId = transfer.fromAccountId,
                            fromAccountBalanceCents = accountRepository.computeBalance(uid, transfer.fromAccountId),
                            toAccountId = transfer.toAccountId,
                            amountText = (transfer.amountCents / 100.0).toString(),
                            note = transfer.note ?: "",
                            occurredAtEpochSec = transfer.occurredAtEpochSec,
                            accounts = accounts,
                            isLoading = false
                        )
                        return@launch
                    }
                }

                _formState.value = TransferFormState(
                    accounts = accounts,
                    fromAccountId = accounts.getOrNull(0)?.id ?: "",
                    fromAccountBalanceCents = accounts.getOrNull(0)?.id?.let { accountRepository.computeBalance(uid, it) },
                    toAccountId = accounts.getOrNull(1)?.id ?: "",
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
            val balance = try {
                accountRepository.computeBalance(uid, accountId)
            } catch (e: Exception) {
                null
            }
            _formState.value = _formState.value.copy(
                fromAccountId = accountId,
                fromAccountBalanceCents = balance
            )
        }
    }

    fun updateFormToAccount(accountId: String) {
        _formState.value = _formState.value.copy(toAccountId = accountId)
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
