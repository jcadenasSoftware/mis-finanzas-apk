package com.jcadenas.xpendz.domain.loan.commands

data class AdjustPrincipalCommand(
    override val envelope: LoanCommandEnvelope,
    val deltaCents: Long,
    val reason: String,
    val accountId: String?,
    val transactionId: String?,
    val note: String?
) : LoanCommand
