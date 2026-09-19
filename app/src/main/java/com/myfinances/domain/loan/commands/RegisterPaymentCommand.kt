package com.jcadenas.xpendz.domain.loan.commands

data class RegisterPaymentCommand(
    override val envelope: LoanCommandEnvelope,
    val amountCents: Long,
    val accountId: String?,
    val transactionId: String?,
    val note: String?
) : LoanCommand
