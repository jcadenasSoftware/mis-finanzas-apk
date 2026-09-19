package com.jcadenas.xpendz.domain.loan.snapshot

import com.jcadenas.xpendz.domain.loan.journal.LoanType

data class LoanSnapshot(
    val loanId: String,
    val ownerId: String,
    val loanType: LoanType,
    val counterpartyName: String,
    val currency: String,
    val defaultAccountId: String?,
    val notes: String?,
    val principalCents: Long,
    val totalPaidCents: Long,
    val netBalanceCents: Long,
    val pendingCents: Long,
    val overpaidCents: Long,
    val status: LoanStatus,
    val closedAt: Long?,
    val lastActivityAt: Long,
    val journalEventCount: Int,
    val journalFingerprint: String,
    val reducerVersion: Int
)
