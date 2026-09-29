package com.jcadenas.xpendz.ui.obligations

import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.ui.screens.obligations.settlementCompatibleRoots
import com.jcadenas.xpendz.ui.screens.obligations.settlementCompatibleSubcategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobro → solo categorías/subcategorías de INGRESOS.
 * Pago → solo categorías/subcategorías de GASTOS.
 * Mismo concepto Categoría → Subcategoría que Nueva transacción.
 */
class SettlementCategoryFilterTest {

    private fun cat(id: String, name: String, kind: String, parentId: String? = null) = CategoryEntity(
        id = id,
        userUid = "user",
        name = name,
        kind = kind,
        parentId = parentId,
        createdAtEpochSec = 1L,
        updatedAtEpochSec = 1L,
        updatedBy = "test"
    )

    private val categories = listOf(
        cat("income-root", "Ventas", "INCOME"),
        cat("expense-root", "Servicios", "EXPENSE"),
        cat("both-root", "General", "BOTH"),
        cat("system-income", "Interna", "INCOME").let { it.copy(id = "system-income") },
        cat("income-child", "Ventas mayoreo", "INCOME", "income-root"),
        cat("income-child-2", "Ventas menudeo", "BOTH", "income-root"),
        cat("expense-child-under-income-root", "Mal clasificada", "EXPENSE", "income-root"),
        cat("expense-child", "Servicios pro", "EXPENSE", "expense-root"),
        cat("income-child-under-expense-root", "Mal clasificada 2", "INCOME", "expense-root"),
        cat("deep-child", "Nieto", "INCOME", "income-child")
    )

    @Test
    fun receivableRootsOnlyIncomeOrBoth() {
        val roots = settlementCompatibleRoots(categories, "INCOME")
        assertEquals(setOf("income-root", "both-root"), roots.map { it.id }.toSet())
    }

    @Test
    fun payableRootsOnlyExpenseOrBoth() {
        val roots = settlementCompatibleRoots(categories, "EXPENSE")
        assertEquals(setOf("expense-root", "both-root"), roots.map { it.id }.toSet())
    }

    @Test
    fun subcategoriesMatchRequestedKind() {
        val incomeSubs = settlementCompatibleSubcategories(categories, "income-root", "INCOME")
        assertEquals(setOf("income-child", "income-child-2"), incomeSubs.map { it.id }.toSet())

        val expenseSubs = settlementCompatibleSubcategories(categories, "expense-root", "EXPENSE")
        assertEquals(setOf("expense-child"), expenseSubs.map { it.id }.toSet())
    }

    @Test
    fun subcategoriesRequireExistingParentAndExcludeSystem() {
        assertTrue(settlementCompatibleSubcategories(categories, null, "INCOME").isEmpty())
        assertTrue(settlementCompatibleSubcategories(categories, "nonexistent", "INCOME").isEmpty())
        // "deep-child" es hija de income-child, no de income-root.
        assertTrue(settlementCompatibleSubcategories(categories, "income-root", "INCOME")
            .none { it.id == "deep-child" })
    }
}
