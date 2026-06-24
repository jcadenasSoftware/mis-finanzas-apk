package com.jcadenas.xpendz.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jcadenas.xpendz.data.repository.AccountRepository
import com.jcadenas.xpendz.data.repository.AuthRepository
import com.jcadenas.xpendz.data.repository.BudgetRepository
import com.jcadenas.xpendz.data.repository.CategoryRepository
import com.jcadenas.xpendz.data.repository.ExchangeRateRepository
import com.jcadenas.xpendz.data.repository.GoalRepository
import com.jcadenas.xpendz.data.repository.LoanPaymentRepository
import com.jcadenas.xpendz.data.repository.LoanRepository
import com.jcadenas.xpendz.data.repository.TransactionRepository
import com.jcadenas.xpendz.data.repository.TransferRepository
import com.jcadenas.xpendz.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PrivacyAndDataState(
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class PrivacyAndDataViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val goalRepository: GoalRepository,
    private val budgetRepository: BudgetRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PrivacyAndDataState())
    val state: StateFlow<PrivacyAndDataState> = _state.asStateFlow()

    private val uid: String?
        get() = authRepository.currentUser?.uid

    private val context get() = getApplication<Application>().applicationContext

    fun deleteAllUserData(onComplete: (Boolean) -> Unit) {
        val userUid = uid ?: return onComplete(false)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            try {
                // Delete all data from all repositories
                transactionRepository.deleteAllByUser(userUid)
                transferRepository.deleteAllByUser(userUid)
                loanPaymentRepository.deleteAllByUser(userUid)
                loanRepository.deleteAllByUser(userUid)
                goalRepository.deleteAllByUser(userUid)
                budgetRepository.deleteAllByUser(userUid)
                accountRepository.deleteAllByUser(userUid)
                categoryRepository.deleteAllByUser(userUid)
                exchangeRateRepository.deleteAllByUser(userUid)
                userSettingsRepository.deleteAllByUser(userUid)

                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Todos tus datos han sido eliminados"
                )
                onComplete(true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Error: ${e.message}"
                )
                onComplete(false)
            }
        }
    }

    fun exportTransactionsReport() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val userUid = uid ?: throw IllegalStateException("Usuario no autenticado")

                // Current month range
                val start = Calendar.getInstance().apply {
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
                val fromEpochSec = start.timeInMillis / 1000
                val toEpochSec = end.timeInMillis / 1000

                val transactions = transactionRepository.getFiltered(
                    userUid = userUid,
                    fromEpochSec = fromEpochSec,
                    toEpochSec = toEpochSec,
                    limit = 500
                )

                val firebaseUser = authRepository.currentUser
                val userName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Usuario"

                val pdfFile = com.jcadenas.xpendz.ui.pdf.TransactionsPdfGenerator.generate(
                    context = context,
                    transactions = transactions,
                    fromDate = java.util.Date(fromEpochSec * 1000),
                    toDate = java.util.Date(toEpochSec * 1000),
                    userName = userName
                )

                sharePdfFile(pdfFile, "Reporte de transacciones generado")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Error al exportar: ${e.message}"
                )
            }
        }
    }

    fun exportAccountsReport() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val userUid = uid ?: throw IllegalStateException("Usuario no autenticado")
                val accounts = accountRepository.getAccounts(userUid)

                val accountsWithBalance = accounts.map { account ->
                    com.jcadenas.xpendz.ui.pdf.AccountsPdfGenerator.AccountWithBalance(
                        account = account,
                        balanceCents = accountRepository.computeBalance(userUid, account.id)
                    )
                }

                val firebaseUser = authRepository.currentUser
                val userName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Usuario"

                val pdfFile = com.jcadenas.xpendz.ui.pdf.AccountsPdfGenerator.generate(
                    context = context,
                    accounts = accountsWithBalance,
                    userName = userName
                )

                sharePdfFile(pdfFile, "Balance de cuentas generado")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Error al exportar: ${e.message}"
                )
            }
        }
    }

    fun exportMonthlyReport() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val userUid = uid ?: throw IllegalStateException("Usuario no autenticado")

                // Current month key "yyyy-MM"
                val cal = Calendar.getInstance()
                val monthKey = "%04d-%02d".format(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1
                )

                // Month range
                val start = Calendar.getInstance().apply {
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
                val fromEpochSec = start.timeInMillis / 1000
                val toEpochSec = end.timeInMillis / 1000

                // Income & expense totals (including loan movements)
                val transactions = transactionRepository.getFiltered(
                    userUid = userUid,
                    fromEpochSec = fromEpochSec,
                    toEpochSec = toEpochSec,
                    limit = 2000
                )
                val incomeKinds = setOf("INCOME", "LOAN_BORROWED_IN", "LOAN_REPAYMENT_PRINCIPAL_IN")
                val expenseKinds = setOf("EXPENSE", "LOAN_LENT_OUT", "LOAN_REPAYMENT_PRINCIPAL_OUT")
                val incomeCents = transactions.filter { it.kind in incomeKinds }.sumOf { it.amountCents }
                val expenseCents = transactions.filter { it.kind in expenseKinds }.sumOf { it.amountCents }

                // User settings for currency
                val userSettings = userSettingsRepository.get(userUid)
                val currency = userSettings?.baseCurrency ?: "COP"

                // Category hierarchy for income and expense (including loan movements)
                val incomeHierarchy = transactionRepository.getHierarchyTotalsInRange(
                    userUid = userUid, kinds = incomeKinds.toList(), currency = currency,
                    fromEpochSec = fromEpochSec, toEpochSec = toEpochSec
                )
                val expenseHierarchy = transactionRepository.getHierarchyTotalsInRange(
                    userUid = userUid, kinds = expenseKinds.toList(), currency = currency,
                    fromEpochSec = fromEpochSec, toEpochSec = toEpochSec
                )

                // Budget lines (base budget)
                val budgetEntities = budgetRepository.getByMonth(userUid, "__BASE__", currency)
                val spentByCategory = transactionRepository.getExpenseTotalsByCategoryInRange(
                    userUid = userUid,
                    currency = currency,
                    fromEpochSec = fromEpochSec,
                    toEpochSec = toEpochSec
                )
                val spentMap = spentByCategory.associate { it.categoryId to it.totalSpentCents }

                // Build a full categoryId→name map (roots + children)
                val rootCategories = categoryRepository.getRoots(userUid)
                val allCatNameMap = rootCategories.associate { it.id to it.name }.toMutableMap()
                val rootIdMap = mutableMapOf<String, String>() // categoryId -> rootCategoryId
                for (root in rootCategories) {
                    val children = runCatching { categoryRepository.getChildren(userUid, root.id) }.getOrDefault(emptyList())
                    children.forEach {
                        allCatNameMap[it.id] = it.name
                        rootIdMap[it.id] = root.id
                    }
                    rootIdMap[root.id] = root.id
                }

                val budgetLines = budgetEntities.map { b ->
                    val rootId = rootIdMap[b.categoryId] ?: b.categoryId
                    val rootName = allCatNameMap[rootId] ?: "Desconocido"
                    com.jcadenas.xpendz.ui.pdf.MonthlySummaryPdfGenerator.BudgetLine(
                        categoryId = b.categoryId,
                        categoryName = allCatNameMap[b.categoryId] ?: b.categoryId.take(12),
                        rootCategoryId = rootId,
                        rootCategoryName = rootName,
                        limitCents = b.limitCents,
                        spentCents = spentMap[b.categoryId] ?: 0L
                    )
                }.sortedByDescending { it.spentCents }

                // Get active loans
                val activeLoans = loanRepository.getFiltered(userUid, null, "OPEN", currency)
                val loanLines = activeLoans.map { loan ->
                    val paidCents = loanPaymentRepository.sumPrincipalByLoan(userUid, loan.id)
                    com.jcadenas.xpendz.ui.pdf.MonthlySummaryPdfGenerator.LoanLine(
                        counterpartyName = loan.counterpartyName,
                        principalCents = loan.principalCents,
                        paidCents = paidCents,
                        type = loan.type
                    )
                }

                // Get active goals
                val activeGoals = goalRepository.getByUser(userUid).filter { it.status == "OPEN" && it.currency == currency }
                val goalLines = activeGoals.map { goal ->
                    val currentCents = accountRepository.computeBalance(userUid, goal.accountId)
                    com.jcadenas.xpendz.ui.pdf.MonthlySummaryPdfGenerator.GoalLine(
                        name = goal.name,
                        targetCents = goal.targetCents,
                        currentCents = currentCents,
                        targetDateEpochSec = goal.targetDateEpochSec
                    )
                }

                val firebaseUser = authRepository.currentUser
                val userName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Usuario"

                val pdfFile = com.jcadenas.xpendz.ui.pdf.MonthlySummaryPdfGenerator.generate(
                    context = context,
                    monthKey = monthKey,
                    incomeCents = incomeCents,
                    expenseCents = expenseCents,
                    incomeHierarchy = incomeHierarchy,
                    expenseHierarchy = expenseHierarchy,
                    budgetLines = budgetLines,
                    loanLines = loanLines,
                    goalLines = goalLines,
                    userName = userName
                )

                sharePdfFile(pdfFile, "Resumen mensual generado")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    message = "Error al exportar: ${e.message}"
                )
            }
        }
    }

    private fun shareFile(fileName: String, content: String, successMessage: String) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(content)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartir reporte")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            _state.value = _state.value.copy(isLoading = false, message = successMessage)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                message = "Error al guardar archivo: ${e.message}"
            )
        }
    }

    private fun sharePdfFile(file: File, successMessage: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartir PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            _state.value = _state.value.copy(isLoading = false, message = successMessage)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                message = "Error al compartir PDF: ${e.message}"
            )
        }
    }
}
