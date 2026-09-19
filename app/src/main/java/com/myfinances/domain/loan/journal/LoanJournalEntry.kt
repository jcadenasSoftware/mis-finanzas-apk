package com.jcadenas.xpendz.domain.loan.journal

data class LoanJournalEntry(
    val eventId: String,
    val operationId: String,
    val loanId: String,
    val ownerId: String,
    val rawEventType: String,
    val eventSchemaVersion: Int,
    val amountCents: Long?,
    val accountId: String?,
    val transactionId: String?,
    val note: String?,
    val occurredAt: Long,
    val recordedAt: Long,
    val actorId: String?,
    val originId: String?,
    val rawPayload: Map<String, Any?>?
)
