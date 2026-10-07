package com.jcadenas.xpendz.ui.obligations

import com.jcadenas.xpendz.application.obligation.ObligationResolvedStatus
import com.jcadenas.xpendz.application.obligation.ResolvedObligationState
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.ui.screens.obligations.partitionObligationsByStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Partición operativa de la pantalla Obligaciones (paridad Desktop v1.0.06):
 * PENDIENTE/PARCIAL/VENCIDA → "Activas"; PAGADA → "Pagadas (N)";
 * CANCELADA → "Canceladas (N)". Pagadas y canceladas salen del listado
 * operativo pero permanecen accesibles en secciones contraíbles.
 */
class ObligationCancelledSectionTest {

    private fun obligation(id: String) = ObligationEntity(
        id = id,
        userUid = "user",
        type = ObligationEntity.TYPE_RECEIVABLE,
        title = "Obl $id",
        counterpartyName = "Cliente",
        notes = null,
        reference = null,
        obligationCategoryId = null,
        currency = "COP",
        originalAmountCents = 100_000L,
        issuedAtEpochSec = 1L,
        dueAtEpochSec = null,
        cancelledAtEpochSec = null,
        createdAtEpochSec = 1L,
        updatedAtEpochSec = 1L
    )

    private fun state(id: String, status: ObligationResolvedStatus) = ResolvedObligationState(
        obligationId = id,
        status = status,
        originalAmountCents = 100_000L,
        totalSettledCents = if (status == ObligationResolvedStatus.PAGADA) 100_000L else 0L,
        pendingAmountCents = if (status == ObligationResolvedStatus.PAGADA) 0L else 100_000L,
        dueAtEpochSec = null,
        cancelledAtEpochSec = if (status == ObligationResolvedStatus.CANCELADA) 2L else null
    )

    private val obligations = listOf(
        obligation("pendiente"),
        obligation("parcial"),
        obligation("vencida"),
        obligation("pagada"),
        obligation("cancelada")
    )

    private val states = mapOf(
        "pendiente" to state("pendiente", ObligationResolvedStatus.PENDIENTE),
        "parcial" to state("parcial", ObligationResolvedStatus.PARCIAL),
        "vencida" to state("vencida", ObligationResolvedStatus.VENCIDA),
        "pagada" to state("pagada", ObligationResolvedStatus.PAGADA),
        "cancelada" to state("cancelada", ObligationResolvedStatus.CANCELADA)
    )

    @Test
    fun pendingPartialAndOverdueStayInActiveList() {
        val (active, _, _) = partitionObligationsByStatus(obligations, states)
        assertEquals(
            setOf("pendiente", "parcial", "vencida"),
            active.map { it.id }.toSet()
        )
    }

    @Test
    fun paidLeavesActiveListAndJoinsPaidSection() {
        val (active, paid, _) = partitionObligationsByStatus(obligations, states)
        assertTrue("pagada" !in active.map { it.id })
        assertEquals(listOf("pagada"), paid.map { it.id })
    }

    @Test
    fun cancelledLeavesActiveListAndJoinsCancelledSection() {
        val (active, _, cancelled) = partitionObligationsByStatus(obligations, states)
        assertTrue("cancelada" !in active.map { it.id })
        assertEquals(listOf("cancelada"), cancelled.map { it.id })
    }

    @Test
    fun unresolvedStateStaysInActiveList() {
        val (active, paid, cancelled) = partitionObligationsByStatus(obligations, emptyMap())
        assertEquals(5, active.size)
        assertTrue(paid.isEmpty())
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun allPaidOrCancelledLeavesEmptyActiveList() {
        val all = states.keys.associateWith {
            state(it, ObligationResolvedStatus.PAGADA)
        }
        val (active, paid, cancelled) = partitionObligationsByStatus(obligations, all)
        assertTrue(active.isEmpty())
        assertEquals(5, paid.size)
        assertTrue(cancelled.isEmpty())
    }
}
