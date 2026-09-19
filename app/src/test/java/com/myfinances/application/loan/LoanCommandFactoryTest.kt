package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LoanCommandFactoryTest {

    private val factory = LoanCommandFactory()

    @Test
    fun createLoanBuildsCanonicalCommand() {
        val command = factory.createLoan(
            ownerId = "owner-1",
            loanType = LoanType.LENT,
            counterpartyName = "Ana",
            currency = "USD",
            defaultAccountId = "A1",
            initialPrincipalCents = 100_000,
            occurredAt = 1_000,
            transactionId = "tx-1",
            notes = "Inicial"
        )

        assertEquals(LoanCommandType.CREATE_LOAN, command.envelope.commandType)
        assertEquals(LoanType.LENT, command.loanType)
        assertEquals("Ana", command.counterpartyName)
        assertEquals("USD", command.currency)
        assertEquals("A1", command.defaultAccountId)
        assertEquals(100_000, command.initialPrincipalCents)
        assertEquals("Inicial", command.notes)
        assertEquals("tx-1", command.transactionId)

        assertTrue("operationId debe ser un UUID v4 canónico", isCanonicalUuid(command.envelope.operationId))
        assertTrue("loanId debe ser un UUID v4 canónico", isCanonicalUuid(command.envelope.loanId))
        assertEquals("owner-1", command.envelope.actorId)
        assertEquals("owner-1", command.envelope.originId)
        assertEquals("owner-1", command.envelope.ownerId)
        assertNull(command.envelope.expectedJournalFingerprint)
        assertEquals(1_000, command.envelope.occurredAt)
    }

    @Test
    fun registerPaymentBuildsCanonicalCommand() {
        val command = factory.registerPayment(
            ownerId = "owner-1",
            loanId = "loan-1",
            expectedJournalFingerprint = "fingerprint-1",
            amountCents = 50_000,
            accountId = "A1",
            transactionId = null,
            note = "Pago parcial"
        )

        assertEquals(LoanCommandType.REGISTER_PAYMENT, command.envelope.commandType)
        assertEquals("loan-1", command.envelope.loanId)
        assertEquals(50_000, command.amountCents)
        assertEquals("A1", command.accountId)
        assertNull(command.transactionId)
        assertEquals("Pago parcial", command.note)

        assertTrue("operationId debe ser un UUID v4 canónico", isCanonicalUuid(command.envelope.operationId))
        assertEquals("owner-1", command.envelope.actorId)
        assertEquals("owner-1", command.envelope.originId)
        assertEquals("fingerprint-1", command.envelope.expectedJournalFingerprint)
        assertTrue(command.envelope.occurredAt > 0)
    }

    @Test
    fun reversePaymentBuildsCanonicalCommand() {
        val command = factory.reversePayment(
            ownerId = "owner-1",
            loanId = "loan-1",
            paymentEventId = "event-1",
            expectedJournalFingerprint = "fingerprint-1",
            reason = "Devolución",
            note = "Nota"
        )

        assertEquals(LoanCommandType.REVERSE_PAYMENT, command.envelope.commandType)
        assertEquals("loan-1", command.envelope.loanId)
        assertEquals("event-1", command.targetPaymentEventId)
        assertEquals("Devolución", command.reason)
        assertEquals("Nota", command.note)

        assertTrue("operationId debe ser un UUID v4 canónico", isCanonicalUuid(command.envelope.operationId))
        assertEquals("owner-1", command.envelope.actorId)
        assertEquals("owner-1", command.envelope.originId)
        assertEquals("fingerprint-1", command.envelope.expectedJournalFingerprint)
        assertTrue(command.envelope.occurredAt > 0)
    }

    private fun isCanonicalUuid(value: String): Boolean {
        return try {
            UUID.fromString(value).version() == 4
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
