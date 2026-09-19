package com.jcadenas.xpendz.domain.loan.commands

data class CloseLoanCommand(
    override val envelope: LoanCommandEnvelope,
    val reason: String?,
    val note: String?
) : LoanCommand
