package com.jcadenas.xpendz.domain.loan.reducer

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot

data class LoanReductionResult(
    val type: ReductionResultType,
    val snapshot: LoanSnapshot?,
    val normalizedJournal: List<LoanMovement>,
    val diagnostics: List<LoanDiagnostic>,
    val effectiveEvents: List<LoanMovement>,
    val revertedEvents: List<LoanMovement>,
    val discardedDuplicates: List<LoanMovement>
)
