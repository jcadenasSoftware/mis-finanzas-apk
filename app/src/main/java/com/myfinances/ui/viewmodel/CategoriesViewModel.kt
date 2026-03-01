package com.myfinances.ui.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.data.repository.AuthRepository
import com.myfinances.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesState(
    val rootCategories: List<CategoryEntity> = emptyList(),
    val childrenMap: Map<String, List<CategoryEntity>> = emptyMap(),
    val expandedCategories: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val addDialogParentId: String? = null
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository
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

                _state.value = _state.value.copy(
                    rootCategories = roots,
                    childrenMap = childrenMap,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
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

    fun createCategory(name: String) {
        val uid = userUid ?: return
        val parentId = _state.value.addDialogParentId
        
        viewModelScope.launch {
            try {
                categoryRepository.create(uid, name, parentId)
                hideAddDialog()
                loadCategories()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun renameCategory(categoryId: String, newName: String) {
        val uid = userUid ?: return
        viewModelScope.launch {
            try {
                categoryRepository.rename(uid, categoryId, newName)
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
