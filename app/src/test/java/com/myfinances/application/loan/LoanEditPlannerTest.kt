package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 7B — decisión explícita de comandos en la edición de préstamos.
 * Verifica que solo se emitan los comandos que representan un cambio efectivo.
 */
class LoanEditPlannerTest {

    private fun summary(
        principal: Long = 100_000L,
        account: String? = "acc-1",
        counterparty: String = "Ana",
        notes: String? = null
    ) = LoanSummaryProjection(
        loanId = "loan-1",
        ownerId = "owner-1",
        counterparty = counterparty,
        loanType = LoanType.LENT,
        currency = "COP",
        defaultAccountId = account,
        notes = notes,
        principalCents = principal,
        totalPaidCents = 0L,
        pendingCents = principal,
        overpaidCents = 0L,
        paymentCount = 0,
        lastPaymentAt = null,
        progressPercent = 0,
        status = LoanStatus.OPEN,
        closedAt = null,
        lastActivity = 0L,
        journalFingerprint = "fp"
    )

    @Test
    fun onlyPrincipalChanged_emitsOnlyAdjustment() {
        val d = LoanEditPlanner.decide(
            summary(), counterpartyName = "Ana", accountId = "acc-1",
            principalCents = 150_000L, notes = null
        )
        assertTrue(d.principalChanged)
        assertEquals(50_000L, d.deltaCents)
        assertFalse(d.metadataChanged)
        assertTrue(d.hasChanges)
    }

    @Test
    fun onlyMetadataChanged_emitsOnlyMetadata() {
        val d = LoanEditPlanner.decide(
            summary(), counterpartyName = "Bruno", accountId = "acc-1",
            principalCents = 100_000L, notes = null
        )
        assertFalse(d.principalChanged)
        assertTrue(d.counterpartyChanged)
        assertTrue(d.metadataChanged)
        assertFalse(d.accountChanged)
        assertFalse(d.notesChanged)
    }

    @Test
    fun accountAndNotesChanges_emitMetadataOnly() {
        val d = LoanEditPlanner.decide(
            summary(notes = "vieja"), counterpartyName = "Ana", accountId = "acc-2",
            principalCents = 100_000L, notes = "nueva"
        )
        assertFalse(d.principalChanged)
        assertTrue(d.accountChanged)
        assertTrue(d.notesChanged)
        assertEquals("acc-2", d.accountId)
        assertEquals("nueva", d.notes)
    }

    @Test
    fun bothChanged_emitsBothCommands() {
        val d = LoanEditPlanner.decide(
            summary(), counterpartyName = "Bruno", accountId = "acc-1",
            principalCents = 80_000L, notes = null
        )
        assertTrue(d.principalChanged)
        assertEquals(-20_000L, d.deltaCents)
        assertTrue(d.metadataChanged)
    }

    @Test
    fun noChanges_emitsNothing() {
        val d = LoanEditPlanner.decide(
            summary(notes = "nota"), counterpartyName = "Ana", accountId = "acc-1",
            principalCents = 100_000L, notes = "nota"
        )
        assertFalse(d.hasChanges)
    }

    @Test
    fun blankNotesAreNotAChange() {
        val d = LoanEditPlanner.decide(
            summary(notes = "nota"), counterpartyName = "Ana", accountId = "acc-1",
            principalCents = 100_000L, notes = "   "
        )
        assertFalse(d.notesChanged)
        assertFalse(d.hasChanges)
    }

    /** Doble Guardar: decidir de nuevo contra el estado actualizado no emite nada. */
    @Test
    fun secondSaveAgainstUpdatedState_emitsNothing() {
        val first = LoanEditPlanner.decide(
            summary(), counterpartyName = "Ana", accountId = "acc-1",
            principalCents = 150_000L, notes = null
        )
        assertTrue(first.principalChanged)

        val updated = summary(principal = 150_000L)
        val second = LoanEditPlanner.decide(
            updated, counterpartyName = "Ana", accountId = "acc-1",
            principalCents = 150_000L, notes = null
        )
        assertFalse(second.hasChanges)
        assertEquals(0L, second.deltaCents)
    }

    @Test
    fun nullPrincipalMeansUnchanged() {
        val d = LoanEditPlanner.decide(
            summary(), counterpartyName = "Ana", accountId = "acc-1",
            principalCents = null, notes = null
        )
        assertFalse(d.principalChanged)
        assertNull(d.notes)
        assertFalse(d.hasChanges)
    }
}
