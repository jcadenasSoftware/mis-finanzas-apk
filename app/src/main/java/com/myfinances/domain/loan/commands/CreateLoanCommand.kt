package com.jcadenas.xpendz.domain.loan.commands

import com.jcadenas.xpendz.domain.loan.journal.LoanType

data class CreateLoanCommand(
    override val envelope: LoanCommandEnvelope,
    val loanType: LoanType,
    val initialPrincipalCents: Long,
    val counterpartyName: String,
    val currency: String,
    val defaultAccountId: String?,
    val transactionId: String?,
    val notes: String?
) : LoanCommand
