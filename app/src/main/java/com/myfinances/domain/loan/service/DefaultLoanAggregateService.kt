package com.jcadenas.xpendz.domain.loan.service

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.AddPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.AdjustPrincipalCommand
import com.jcadenas.xpendz.domain.loan.commands.CloseLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.CreateLoanCommand
import com.jcadenas.xpendz.domain.loan.commands.FieldChange
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.commands.RegisterPaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.ReversePaymentCommand
import com.jcadenas.xpendz.domain.loan.commands.UpdateMetadataCommand
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnosticCode
import com.jcadenas.xpendz.domain.loan.journal.LoanEventPayload
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionChange
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionChangeType
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.reducer.LoanReductionResult
import com.jcadenas.xpendz.domain.loan.reducer.ReductionResultType
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository
import com.jcadenas.xpendz.domain.loan.service.error.BusinessRuleViolation
import com.jcadenas.xpendz.domain.loan.service.error.InvariantViolation
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateErrorCode
import com.jcadenas.xpendz.domain.loan.service.error.LoanAggregateException
import com.jcadenas.xpendz.domain.loan.service.error.UnexpectedFailure
import com.jcadenas.xpendz.domain.loan.service.error.ValidationError
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

class DefaultLoanAggregateService(
    private val repository: LoanRepository,
    private val reducer: LoanReducer
) : LoanAggregateService {
    override fun process(command: LoanCommand): LoanCommandResult = try {
        processCommand(command)
    } catch (exception: LoanAggregateException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw UnexpectedFailure(exception)
    }

    private fun processCommand(command: LoanCommand): LoanCommandResult {
        validateEnvelope(command)
        val envelope = command.envelope

        val replayEvent = repository.findByOperationId(envelope.ownerId, envelope.operationId)
        if (replayEvent != null) {
            return replay(command, replayEvent)
        }

        val currentJournal = repository.getJournal(envelope.ownerId, envelope.loanId).toList()
        var currentReduction: LoanReductionResult? = null
        var previousSnapshot: LoanSnapshot? = null

        if (currentJournal.isNotEmpty()) {
            currentReduction = reducer.reduceCanonical(currentJournal)
            requireCurrentJournalValid(currentReduction)
            previousSnapshot = currentReduction.snapshot
        } else if (command !is CreateLoanCommand) {
            reducer.reduceCanonical(currentJournal)
            throw BusinessRuleViolation(LoanAggregateErrorCode.LOAN_NOT_FOUND)
        }

        validateExpectedFingerprint(command, previousSnapshot)
        validateCommandRules(command, currentJournal, currentReduction, previousSnapshot)
        val generatedEvent = buildEvent(command)

        val candidateJournal = currentJournal + generatedEvent
        val candidateReduction = reducer.reduceCanonical(candidateJournal)
        requireCandidateJournalValid(candidateReduction)

        val projectionChanges = projectionChanges(command, generatedEvent)
        repository.appendEvent(generatedEvent)
        repository.replaceSnapshot(candidateReduction.snapshot!!)

        return LoanCommandResult(
            outcome = Outcome.APPLIED,
            operationId = envelope.operationId,
            commandType = envelope.commandType,
            event = generatedEvent,
            previousSnapshot = previousSnapshot,
            currentSnapshot = candidateReduction.snapshot,
            projectionChanges = projectionChanges,
            diagnostics = candidateReduction.diagnostics
        )
    }

    private fun replay(command: LoanCommand, existingEvent: LoanMovement): LoanCommandResult {
        val candidate = buildEvent(command)
        if (existingEvent != candidate) {
            throw InvariantViolation(LoanAggregateErrorCode.OPERATION_CONFLICT)
        }

        val currentJournal = repository.getJournal(existingEvent.ownerId, existingEvent.loanId).toList()
        val currentReduction = reducer.reduceCanonical(currentJournal)
        requireCurrentJournalValid(currentReduction)
        val currentSnapshot = currentReduction.snapshot!!

        return LoanCommandResult(
            outcome = Outcome.REPLAYED,
            operationId = existingEvent.operationId,
            commandType = command.envelope.commandType,
            event = existingEvent,
            previousSnapshot = currentSnapshot,
            currentSnapshot = currentSnapshot,
            projectionChanges = listOf(LoanProjectionChange(LoanProjectionChangeType.NONE, null)),
            diagnostics = currentReduction.diagnostics
        )
    }

    private fun validateEnvelope(command: LoanCommand) {
        val envelope = command.envelope
        if (envelope.commandType != commandType(command)) {
            throw ValidationError(LoanAggregateErrorCode.COMMAND_TYPE_MISMATCH)
        }
        if (!isCanonicalUuid(envelope.operationId)) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_OPERATION_ID)
        }
        if (envelope.loanId.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_LOAN_ID)
        }
        if (envelope.ownerId.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_OWNER_ID)
        }
    }

    private fun validateExpectedFingerprint(command: LoanCommand, previousSnapshot: LoanSnapshot?) {
        val expected = command.envelope.expectedJournalFingerprint
        if (command is CreateLoanCommand) {
            if (expected != null) {
                throw ValidationError(LoanAggregateErrorCode.EXPECTED_FINGERPRINT_NOT_ALLOWED)
            }
            return
        }
        if (expected.isNullOrBlank()) {
            throw ValidationError(LoanAggregateErrorCode.EXPECTED_FINGERPRINT_REQUIRED)
        }
        if (expected != previousSnapshot!!.journalFingerprint) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.STALE_AGGREGATE_VERSION)
        }
    }

    private fun validateCommandRules(
        command: LoanCommand,
        currentJournal: List<LoanMovement>,
        currentReduction: LoanReductionResult?,
        currentSnapshot: LoanSnapshot?
    ) {
        when (command) {
            is CreateLoanCommand -> validateCreate(command, currentJournal)
            is RegisterPaymentCommand -> validatePayment(command, currentSnapshot!!)
            is AddPrincipalCommand -> validateTopup(command)
            is AdjustPrincipalCommand -> validateAdjustment(command, currentSnapshot!!)
            is UpdateMetadataCommand -> validateMetadata(command, currentSnapshot!!)
            is ReversePaymentCommand -> validateReversePayment(command, currentReduction!!)
            is CloseLoanCommand -> validateClose(currentSnapshot!!, currentReduction!!)
        }
    }

    private fun validateCreate(command: CreateLoanCommand, currentJournal: List<LoanMovement>) {
        if (currentJournal.isNotEmpty()) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.LOAN_ALREADY_EXISTS)
        }
        if (command.initialPrincipalCents <= 0L) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_INITIAL_PRINCIPAL)
        }
        if (command.counterpartyName.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_COUNTERPARTY)
        }
        val currency = normalizedCurrency(command.currency)
        if (!currency.matches(Regex("[A-Z]{3}"))) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_CURRENCY)
        }
        validateOptionalIdentifier(command.defaultAccountId)
        validateOptionalIdentifier(command.transactionId)
    }

    private fun validatePayment(command: RegisterPaymentCommand, snapshot: LoanSnapshot) {
        if (command.amountCents <= 0L) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_PAYMENT_AMOUNT)
        }
        if (snapshot.pendingCents <= 0L) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.LOAN_HAS_NO_PENDING_BALANCE)
        }
        if (command.amountCents > snapshot.pendingCents) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.PAYMENT_EXCEEDS_PENDING)
        }
        validateOptionalIdentifier(command.accountId)
        validateOptionalIdentifier(command.transactionId)
    }

    private fun validateTopup(command: AddPrincipalCommand) {
        if (command.amountCents <= 0L) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_TOPUP_AMOUNT)
        }
        validateOptionalIdentifier(command.accountId)
        validateOptionalIdentifier(command.transactionId)
    }

    private fun validateAdjustment(command: AdjustPrincipalCommand, snapshot: LoanSnapshot) {
        if (command.deltaCents == 0L) {
            throw ValidationError(LoanAggregateErrorCode.ZERO_ADJUSTMENT)
        }
        if (command.reason.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.ADJUSTMENT_REASON_REQUIRED)
        }
        val resultingPrincipal = try {
            addExact(snapshot.principalCents, command.deltaCents)
        } catch (_: ArithmeticException) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.ARITHMETIC_OVERFLOW)
        }
        if (resultingPrincipal <= 0L) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.NON_POSITIVE_RESULTING_PRINCIPAL)
        }
        if (resultingPrincipal < snapshot.totalPaidCents) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.PRINCIPAL_BELOW_TOTAL_PAID)
        }
        validateOptionalIdentifier(command.accountId)
        validateOptionalIdentifier(command.transactionId)
    }

    private fun validateMetadata(command: UpdateMetadataCommand, snapshot: LoanSnapshot) {
        val counterparty = field(command.counterpartyName)
        val account = field(command.defaultAccountId)
        val notes = field(command.notes)
        if (!counterparty.present && !account.present && !notes.present) {
            throw ValidationError(LoanAggregateErrorCode.EMPTY_METADATA_CHANGE)
        }
        if (counterparty.present && counterparty.value.isNullOrBlank()) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_COUNTERPARTY)
        }
        if (account.present) {
            validateOptionalIdentifier(account.value)
        }

        val counterpartyChanged = counterparty.present &&
            normalizeRequired(counterparty.value!!) != snapshot.counterpartyName
        val accountChanged = account.present && account.value != snapshot.defaultAccountId
        val notesChanged = notes.present && normalizeNullable(notes.value) != snapshot.notes
        if (!counterpartyChanged && !accountChanged && !notesChanged) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.NO_EFFECTIVE_CHANGE)
        }
    }

    private fun validateReversePayment(
        command: ReversePaymentCommand,
        currentReduction: LoanReductionResult
    ) {
        if (command.targetPaymentEventId.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.TARGET_EVENT_NOT_FOUND)
        }
        if (command.reason.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.REVERSAL_REASON_REQUIRED)
        }
        val target = repository.findByEventId(
            command.envelope.ownerId,
            command.targetPaymentEventId
        ) ?: throw BusinessRuleViolation(LoanAggregateErrorCode.TARGET_EVENT_NOT_FOUND)
        if (target.loanId != command.envelope.loanId || target.ownerId != command.envelope.ownerId) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.CROSS_LOAN_TARGET)
        }
        if (target.eventType != LoanEventType.PAYMENT) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.TARGET_NOT_PAYMENT)
        }
        if (currentReduction.effectiveEvents.none { it.eventId == target.eventId }) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.PAYMENT_ALREADY_REVERSED)
        }
    }

    private fun validateClose(snapshot: LoanSnapshot, currentReduction: LoanReductionResult) {
        if (snapshot.pendingCents > 0L) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.LOAN_HAS_PENDING_BALANCE)
        }
        if (currentReduction.effectiveEvents.any { it.eventType == LoanEventType.CLOSE }) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.LOAN_ALREADY_EXPLICITLY_CLOSED)
        }
    }

    private fun buildEvent(command: LoanCommand): LoanMovement {
        val envelope = command.envelope
        val eventType: LoanEventType
        val amountCents: Long?
        val accountId: String?
        val transactionId: String?
        val note: String?
        val payload: LoanEventPayload

        when (command) {
            is CreateLoanCommand -> {
                eventType = LoanEventType.CREATION
                amountCents = command.initialPrincipalCents
                accountId = command.defaultAccountId
                transactionId = command.transactionId
                note = normalizeNullable(command.notes)
                payload = LoanEventPayload.CreationPayload(
                    loanType = command.loanType,
                    counterpartyName = normalizeRequired(command.counterpartyName),
                    currency = normalizedCurrency(command.currency),
                    defaultAccountId = command.defaultAccountId,
                    notes = normalizeNullable(command.notes)
                )
            }
            is RegisterPaymentCommand -> {
                eventType = LoanEventType.PAYMENT
                amountCents = command.amountCents
                accountId = command.accountId
                transactionId = command.transactionId
                note = normalizeNullable(command.note)
                payload = LoanEventPayload.PaymentPayload(null, null)
            }
            is AddPrincipalCommand -> {
                eventType = LoanEventType.TOPUP
                amountCents = command.amountCents
                accountId = command.accountId
                transactionId = command.transactionId
                note = normalizeNullable(command.note)
                payload = LoanEventPayload.EmptyPayload
            }
            is AdjustPrincipalCommand -> {
                eventType = LoanEventType.ADJUSTMENT
                amountCents = command.deltaCents
                accountId = command.accountId
                transactionId = command.transactionId
                note = normalizeNullable(command.note)
                payload = LoanEventPayload.AdjustmentPayload(normalizeRequired(command.reason), null)
            }
            is UpdateMetadataCommand -> {
                eventType = LoanEventType.METADATA_CHANGED
                amountCents = null
                accountId = null
                transactionId = null
                note = null
                payload = LoanEventPayload.MetadataChangedPayload(
                    changes = LoanEventPayload.MetadataChanges(
                        counterpartyName = eventField(command.counterpartyName, normalize = true, trim = true),
                        defaultAccountId = eventField(command.defaultAccountId, normalize = false, trim = false),
                        notes = eventField(command.notes, normalize = true, trim = false)
                    ),
                    legacySource = null
                )
            }
            is ReversePaymentCommand -> {
                eventType = LoanEventType.REVERSAL
                amountCents = null
                accountId = null
                transactionId = null
                note = normalizeNullable(command.note)
                payload = LoanEventPayload.ReversalPayload(
                    command.targetPaymentEventId,
                    normalizeRequired(command.reason)
                )
            }
            is CloseLoanCommand -> {
                eventType = LoanEventType.CLOSE
                amountCents = null
                accountId = null
                transactionId = null
                note = normalizeNullable(command.note)
                payload = LoanEventPayload.ClosePayload(normalizeNullable(command.reason))
            }
        }

        return LoanMovement(
            eventId = envelope.operationId,
            operationId = envelope.operationId,
            loanId = envelope.loanId,
            ownerId = envelope.ownerId,
            eventType = eventType,
            eventSchemaVersion = 1,
            amountCents = amountCents,
            accountId = accountId,
            transactionId = transactionId,
            note = note,
            occurredAt = envelope.occurredAt,
            recordedAt = envelope.occurredAt,
            actorId = envelope.actorId,
            originId = envelope.originId,
            payload = payload
        )
    }

    private fun projectionChanges(
        command: LoanCommand,
        event: LoanMovement
    ): List<LoanProjectionChange> {
        val rebuild = LoanProjectionChange(
            LoanProjectionChangeType.REBUILD_LOAN_SNAPSHOT,
            event.eventId
        )
        return when (command) {
            is RegisterPaymentCommand -> listOf(
                LoanProjectionChange(LoanProjectionChangeType.ADD_PAYMENT_PROJECTION, event.eventId),
                rebuild
            )
            is ReversePaymentCommand -> listOf(
                LoanProjectionChange(
                    LoanProjectionChangeType.REMOVE_PAYMENT_PROJECTION,
                    command.targetPaymentEventId
                ),
                rebuild
            )
            else -> listOf(rebuild)
        }
    }

    private fun requireCurrentJournalValid(result: LoanReductionResult) {
        when {
            result.type == ReductionResultType.INVALID -> throw InvariantViolation(
                LoanAggregateErrorCode.CURRENT_JOURNAL_INVALID,
                result.diagnostics
            )
            result.type == ReductionResultType.INCOMPLETE || result.snapshot == null ->
                throw InvariantViolation(
                    LoanAggregateErrorCode.CURRENT_JOURNAL_INCOMPLETE,
                    result.diagnostics
                )
        }
    }

    private fun requireCandidateJournalValid(result: LoanReductionResult) {
        if (result.diagnostics.any { it.code == LoanDiagnosticCode.ARITHMETIC_OVERFLOW }) {
            throw BusinessRuleViolation(LoanAggregateErrorCode.ARITHMETIC_OVERFLOW)
        }
        when {
            result.type == ReductionResultType.INVALID -> throw InvariantViolation(
                LoanAggregateErrorCode.CANDIDATE_JOURNAL_INVALID,
                result.diagnostics
            )
            result.type == ReductionResultType.INCOMPLETE || result.snapshot == null ->
                throw InvariantViolation(
                    LoanAggregateErrorCode.CANDIDATE_JOURNAL_INCOMPLETE,
                    result.diagnostics
                )
        }
    }

    private fun commandType(command: LoanCommand): LoanCommandType = when (command) {
        is CreateLoanCommand -> LoanCommandType.CREATE_LOAN
        is RegisterPaymentCommand -> LoanCommandType.REGISTER_PAYMENT
        is AddPrincipalCommand -> LoanCommandType.ADD_PRINCIPAL
        is AdjustPrincipalCommand -> LoanCommandType.ADJUST_PRINCIPAL
        is UpdateMetadataCommand -> LoanCommandType.UPDATE_METADATA
        is ReversePaymentCommand -> LoanCommandType.REVERSE_PAYMENT
        is CloseLoanCommand -> LoanCommandType.CLOSE_LOAN
    }

    private fun <T> field(value: FieldChange<T>?): FieldChange<T> = value ?: FieldChange(false, null)

    private fun eventField(
        change: FieldChange<String>?,
        normalize: Boolean,
        trim: Boolean
    ): LoanEventPayload.FieldValue<String> {
        val safe = field(change)
        val value = if (normalize && safe.value != null) {
            Normalizer.normalize(if (trim) safe.value.trim() else safe.value, Normalizer.Form.NFC)
        } else {
            safe.value
        }
        return LoanEventPayload.FieldValue(safe.present, value)
    }

    private fun validateOptionalIdentifier(value: String?) {
        if (value != null && value.isBlank()) {
            throw ValidationError(LoanAggregateErrorCode.INVALID_IDENTIFIER)
        }
    }

    private fun isCanonicalUuid(value: String): Boolean = try {
        val parsed = UUID.fromString(value)
        parsed.toString() == value && (parsed.version() == 4 || parsed.version() == 7)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun normalizeRequired(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC)

    private fun normalizeNullable(value: String?): String? =
        value?.let { Normalizer.normalize(it, Normalizer.Form.NFC) }

    private fun normalizedCurrency(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC).uppercase(Locale.ROOT)

    private fun addExact(left: Long, right: Long): Long {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0L) {
            throw ArithmeticException()
        }
        return result
    }
}
