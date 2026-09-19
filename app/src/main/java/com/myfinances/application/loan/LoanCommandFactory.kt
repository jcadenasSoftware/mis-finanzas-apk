package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.AdjustPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CloseLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.FieldChange
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandEnvelope
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.ReversePaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.UpdateMetadataCommand
import com.jcadenas.xpendz.domain.loan.journal.LoanType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanCommandFactory @Inject constructor() {

    fun createLoan(
        ownerId: String,
        loanType: LoanType,
        counterpartyName: String,
        currency: String,
        defaultAccountId: String?,
        initialPrincipalCents: Long,
        occurredAt: Long,
        transactionId: String?,
        notes: String?
    ): CreateLoanCommand {
        val loanId = UUID.randomUUID().toString()
        val envelope = buildEnvelope(
            commandType = LoanCommandType.CREATE_LOAN,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = null,
            occurredAt = occurredAt
        )
        return CreateLoanCommand(
            envelope = envelope,
            loanType = loanType,
            initialPrincipalCents = initialPrincipalCents,
            counterpartyName = counterpartyName,
            currency = currency,
            defaultAccountId = defaultAccountId,
            transactionId = transactionId,
            notes = notes
        )
    }

    fun registerPayment(
        ownerId: String,
        loanId: String,
        expectedJournalFingerprint: String,
        amountCents: Long,
        accountId: String?,
        transactionId: String?,
        note: String?
    ): RegisterPaymentCommand {
        val envelope = buildEnvelope(
            commandType = LoanCommandType.REGISTER_PAYMENT,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = System.currentTimeMillis() / 1000
        )
        return RegisterPaymentCommand(
            envelope = envelope,
            amountCents = amountCents,
            accountId = accountId,
            transactionId = transactionId,
            note = note
        )
    }

    fun reversePayment(
        ownerId: String,
        loanId: String,
        paymentEventId: String,
        expectedJournalFingerprint: String,
        reason: String,
        note: String?
    ): ReversePaymentCommand {
        val envelope = buildEnvelope(
            commandType = LoanCommandType.REVERSE_PAYMENT,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = System.currentTimeMillis() / 1000
        )
        return ReversePaymentCommand(
            envelope = envelope,
            targetPaymentEventId = paymentEventId,
            reason = reason,
            note = note
        )
    }

    fun updateLoanMetadata(
        ownerId: String,
        loanId: String,
        expectedJournalFingerprint: String,
        counterpartyName: FieldChange<String>,
        defaultAccountId: FieldChange<String>,
        notes: FieldChange<String>
    ): UpdateMetadataCommand {
        val envelope = buildEnvelope(
            commandType = LoanCommandType.UPDATE_METADATA,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = System.currentTimeMillis() / 1000
        )
        return UpdateMetadataCommand(
            envelope = envelope,
            counterpartyName = counterpartyName,
            defaultAccountId = defaultAccountId,
            notes = notes
        )
    }

    fun adjustPrincipal(
        ownerId: String,
        loanId: String,
        expectedJournalFingerprint: String,
        deltaCents: Long,
        reason: String,
        accountId: String?,
        transactionId: String?,
        note: String?
    ): AdjustPrincipalCommand {
        val envelope = buildEnvelope(
            commandType = LoanCommandType.ADJUST_PRINCIPAL,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = System.currentTimeMillis() / 1000
        )
        return AdjustPrincipalCommand(
            envelope = envelope,
            deltaCents = deltaCents,
            reason = reason,
            accountId = accountId,
            transactionId = transactionId,
            note = note
        )
    }

    @Deprecated("Misleading API: administrative archiving uses LoanAdminStateRepository")
    fun archiveLoan(
        ownerId: String,
        loanId: String,
        expectedJournalFingerprint: String,
        reason: String?,
        note: String?
    ): CloseLoanCommand {
        val envelope = buildEnvelope(
            commandType = LoanCommandType.CLOSE_LOAN,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = System.currentTimeMillis() / 1000
        )
        return CloseLoanCommand(
            envelope = envelope,
            reason = reason,
            note = note
        )
    }

    fun addPrincipal(
        ownerId: String,
        loanId: String,
        expectedJournalFingerprint: String,
        amountCents: Long,
        accountId: String?,
        transactionId: String?,
        note: String?
    ): AddPrincipalCommand {
        val envelope = buildEnvelope(
            commandType = LoanCommandType.ADD_PRINCIPAL,
            ownerId = ownerId,
            loanId = loanId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = System.currentTimeMillis() / 1000
        )
        return AddPrincipalCommand(
            envelope = envelope,
            amountCents = amountCents,
            accountId = accountId,
            transactionId = transactionId,
            note = note
        )
    }

    private fun buildEnvelope(
        commandType: LoanCommandType,
        ownerId: String,
        loanId: String,
        expectedJournalFingerprint: String?,
        occurredAt: Long
    ): LoanCommandEnvelope {
        val operationId = UUID.randomUUID().toString()
        return LoanCommandEnvelope(
            commandType = commandType,
            operationId = operationId,
            loanId = loanId,
            ownerId = ownerId,
            expectedJournalFingerprint = expectedJournalFingerprint,
            occurredAt = occurredAt,
            actorId = ownerId,
            originId = ownerId
        )
    }
}
