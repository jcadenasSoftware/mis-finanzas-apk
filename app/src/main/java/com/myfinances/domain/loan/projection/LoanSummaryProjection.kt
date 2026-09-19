package com.jcadenas.xpendz.domain.loan.projection

import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus

data class LoanSummaryProjection(
    val loanId: String,
    val ownerId: String,
    val counterparty: String,
    val loanType: LoanType,
    val currency: String,
    val defaultAccountId: String?,
    val notes: String?,
    val principalCents: Long,
    val totalPaidCents: Long,
    val pendingCents: Long,
    val overpaidCents: Long,
    val paymentCount: Int,
    val lastPaymentAt: Long?,
    val progressPercent: Int,
    val status: LoanStatus,
    val closedAt: Long?,
    val lastActivity: Long,
    val journalFingerprint: String
)
