package com.jcadenas.xpendz.domain.loan.commands

data class AddPrincipalCommand(
    override val envelope: LoanCommandEnvelope,
    val amountCents: Long,
    val accountId: String?,
    val transactionId: String?,
    val note: String?
) : LoanCommand
