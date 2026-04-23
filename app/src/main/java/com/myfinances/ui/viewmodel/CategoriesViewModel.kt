package com.myfinances.ui.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.dao.CategorySpentTotal
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.CategoryRepository
import com.myfinances.data.repository.TransactionRepository
import com.myfinances.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CategoryMonthlyInsight(
    val categoryId: String,
    val categoryName: String,
    val totalSpentCents: Long
)

data class CategoriesState(
    val rootCategories: List<CategoryEntity> = emptyList(),
    val childrenMap: Map<String, List<CategoryEntity>> = emptyMap(),
    val expandedCategories: Set<String> = emptySet(),
    val monthlyInsight: CategoryMonthlyInsight? = null,
    val baseCurrency: String = "COP",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val addDialogParentId: String? = null
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    private val userUid: String?
        get() = authRepository.currentUser?.uid

    init {
        loadCategories()
    }

    fun loadCategories() {
        val uid = userUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val roots = categoryRepository.getRoots(uid)
                val childrenMap = mutableMapOf<String, List<CategoryEntity>>()
                
                roots.forEach { root ->
                    val children = categoryRepository.getChildren(uid, root.id)
                    if (children.isNotEmpty()) {
                        childrenMap[root.id] = children
                    }
                }

                val baseCurrency = userSettingsRepository.get(uid)?.baseCurrency ?: "COP"
                val monthlyInsight = loadMonthlyInsight(
                    userUid = uid,
                    currency = baseCurrency
                )

                _state.value = _state.value.copy(
                    rootCategories = roots,
                    childrenMap = childrenMap,
                    monthlyInsight = monthlyInsight,
                    baseCurrency = baseCurrency,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadMonthlyInsight(
        userUid: String,
        currency: String
    ): CategoryMonthlyInsight? {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromEpochSec = calendar.timeInMillis / 1000
        val toEpochSec = (calendar.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.SECOND, -1)
        }.timeInMillis / 1000

        val topCategory: CategorySpentTotal = transactionRepository
            .getExpenseTotalsByCategoryInRange(
                userUid = userUid,
                currency = currency,
                fromEpochSec = fromEpochSec,
                toEpochSec = toEpochSec
            )
            .maxByOrNull { it.totalSpentCents }
            ?: return null

        if (topCategory.totalSpentCents <= 0L) {
            return null
        }

        return CategoryMonthlyInsight(
            categoryId = topCategory.categoryId,
            categoryName = topCategory.categoryName,
            totalSpentCents = topCategory.totalSpentCents
        )
    }

    fun toggleExpanded(categoryId: String) {
        val expanded = _state.value.expandedCategories.toMutableSet()
        if (expanded.contains(categoryId)) {
            expanded.remove(categoryId)
        } else {
            expanded.add(categoryId)
        }
        _state.value = _state.value.copy(expandedCategories = expanded)
    }

    fun showAddDialog(parentId: String? = null) {
        _state.value = _state.value.copy(showAddDialog = true, addDialogParentId = parentId)
    }

    fun hideAddDialog() {
        _state.value = _state.value.copy(showAddDialog = false, addDialogParentId = null)
    }

    fun createCategory(name: String, iconKey: String? = null, kind: String? = null) {
        val uid = userUid ?: return
        val parentId = _state.value.addDialogParentId

        if (parentId == "root") {
            _state.value = _state.value.copy(
                error = "Para crear una subcategoría, primero selecciona una categoría padre."
            )
            return
        }
        
        viewModelScope.launch {
            try {
                val resolvedKind = if (parentId.isNullOrBlank()) {
                    kind?.trim()?.uppercase()?.takeIf { it == "INCOME" || it == "EXPENSE" }
                        ?: throw IllegalArgumentException("kind")
                } else {
                    val parent = categoryRepository.getById(parentId)
                        ?: throw IllegalArgumentException("parentId")
                    parent.kind.trim().uppercase().takeIf { it == "INCOME" || it == "EXPENSE" }
                        ?: "EXPENSE"
                }
                categoryRepository.create(
                    userUid = uid,
                    name = name,
                    kind = resolvedKind,
                    iconKey = iconKey,
                    parentId = parentId
                )
                hideAddDialog()
                loadCategories()
            } catch (e: SQLiteConstraintException) {
                _state.value = _state.value.copy(
                    error = "No se pudo crear la categoría/subcategoría. Verifica que la subcategoría tenga una categoría padre válida."
                )
            } catch (e: IllegalArgumentException) {
                _state.value = _state.value.copy(
                    error = if (e.message == "kind") {
                        "Selecciona si la categoría es de Ingreso o Egreso."
                    } else {
                        "No se pudo determinar el tipo de la categoría padre."
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun renameCategory(categoryId: String, newName: String, iconKey: String? = null, kind: String? = null) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                val existing = categoryRepository.getById(categoryId)
                val normalizedKind = kind?.trim()?.uppercase()?.takeIf { it == "INCOME" || it == "EXPENSE" }
                val kindToPersist = if (existing?.parentId.isNullOrBlank()) normalizedKind else null
                categoryRepository.updateCategory(
                    userUid = uid,
                    categoryId = categoryId,
                    newName = newName,
                    kind = kindToPersist,
                    iconKey = iconKey ?: categoryRepository.getById(categoryId)?.iconKey
                )
                loadCategories()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                categoryRepository.delete(categoryId, uid)
                loadCategories()
            } catch (e: SQLiteConstraintException) {
                _state.value = _state.value.copy(
                    error = "No se puede eliminar la categoría/subcategoría porque tiene movimientos o saldo asociado. Deja el saldo en cero y elimina/mueve sus movimientos para poder eliminarla."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
