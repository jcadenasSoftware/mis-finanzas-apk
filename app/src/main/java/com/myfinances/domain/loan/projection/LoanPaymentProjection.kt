package com.jcadenas.xpendz.domain.loan.projection

data class LoanPaymentProjection(
    val sourceEventId: String,
    val operationId: String,
    val ownerId: String,
    val loanId: String,
    val accountId: String?,
    val transactionId: String?,
    val occurredAt: Long,
    val amountCents: Long,
    val direction: LoanPaymentDirection?,
    val note: String?
)
