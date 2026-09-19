package com.jcadenas.xpendz.domain.loan.commands

sealed interface LoanCommand {
    val envelope: LoanCommandEnvelope
}
