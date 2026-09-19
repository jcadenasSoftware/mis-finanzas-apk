package com.jcadenas.xpendz.domain.loan.commands

data class UpdateMetadataCommand(
    override val envelope: LoanCommandEnvelope,
    val counterpartyName: FieldChange<String>,
    val defaultAccountId: FieldChange<String>,
    val notes: FieldChange<String>
) : LoanCommand
