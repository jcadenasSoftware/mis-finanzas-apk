package com.jcadenas.xpendz.ui.obligations

import com.jcadenas.xpendz.application.obligation.ObligationResolvedStatus
import com.jcadenas.xpendz.application.obligation.ResolvedObligationState
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.ui.screens.obligations.splitObligationsByCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sección "Canceladas" — mismo patrón que Metas → Archivadas:
 * CANCELADA sale de la lista activa y aparece bajo "Canceladas (N)".
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
        totalSettledCents = 0L,
        pendingAmountCents = 100_000L,
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
    fun cancelledLeavesActiveListAndJoinsCancelledSection() {
        val (active, cancelled) = splitObligationsByCancellation(obligations, states)
        assertEquals(
            setOf("pendiente", "parcial", "vencida", "pagada"),
            active.map { it.id }.toSet()
        )
        assertEquals(listOf("cancelada"), cancelled.map { it.id })
    }

    @Test
    fun cancelledCounterMatchesSectionSize() {
        val (_, cancelled) = splitObligationsByCancellation(obligations, states)
        assertEquals(1, cancelled.size)
    }

    @Test
    fun unresolvedStateStaysInActiveList() {
        val (active, cancelled) = splitObligationsByCancellation(obligations, emptyMap())
        assertEquals(5, active.size)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun allCancelledLeavesEmptyActiveList() {
        val all = states.keys.associateWith { state(it, ObligationResolvedStatus.CANCELADA) }
        val (active, cancelled) = splitObligationsByCancellation(obligations, all)
        assertTrue(active.isEmpty())
        assertEquals(5, cancelled.size)
    }
}
