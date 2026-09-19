package com.jcadenas.xpendz.domain.loan.commands

data class LoanCommandEnvelope(
    val commandType: LoanCommandType,
    val operationId: String,
    val loanId: String,
    val ownerId: String,
    val expectedJournalFingerprint: String?,
    val occurredAt: Long,
    val actorId: String?,
    val originId: String?
)
