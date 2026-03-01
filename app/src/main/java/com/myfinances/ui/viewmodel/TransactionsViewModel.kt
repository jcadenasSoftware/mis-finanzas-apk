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

data class TransactionsState(
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
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

    private val _state = MutableStateFlow(TransactionsState())
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    init {
        loadTransactions()
    }

    fun loadTransactions() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val transactions = transactionRepository.getRecent(uid, 100)
                val accounts = accountRepository.getAccounts(uid)
                val categories = categoryRepository.getCategories(uid)
                _state.value = _state.value.copy(
                    transactions = transactions,
                    accounts = accounts,
                    categories = categories,
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

    fun filterByCategory(categoryId: String?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
        applyFilters()
    }

    private fun applyFilters() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val transactions = transactionRepository.getFiltered(
                    userUid = uid,
                    accountId = _state.value.selectedAccountId,
                    categoryId = _state.value.selectedCategoryId,
                    fromEpochSec = null,
                    toEpochSec = null,
                    limit = 100
                )
                _state.value = _state.value.copy(transactions = transactions, isLoading = false)
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
            val subCategories = categoryRepository.getChildren(uid, categoryId)
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
        _formState.value = _formState.value.copy(kind = kind)
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

    fun clearError() {
        _state.value = _state.value.copy(error = null)
        _formState.value = _formState.value.copy(error = null)
    }
}
