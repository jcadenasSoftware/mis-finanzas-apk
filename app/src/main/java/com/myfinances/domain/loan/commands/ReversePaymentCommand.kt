package com.jcadenas.xpendz.domain.loan.commands

data class ReversePaymentCommand(
    override val envelope: LoanCommandEnvelope,
    val targetPaymentEventId: String,
    val reason: String,
    val note: String?
) : LoanCommand
