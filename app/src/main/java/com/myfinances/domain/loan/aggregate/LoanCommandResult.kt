package com.jcadenas.xpendz.domain.loan.aggregate

import com.jcadenas.xpendz.domain.loan.commands.LoanCommandType
import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionChange
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot

data class LoanCommandResult(
    val outcome: Outcome,
    val operationId: String,
    val commandType: LoanCommandType,
    val event: LoanMovement,
    val previousSnapshot: LoanSnapshot?,
    val currentSnapshot: LoanSnapshot,
    val projectionChanges: List<LoanProjectionChange>,
    val diagnostics: List<LoanDiagnostic>
)
