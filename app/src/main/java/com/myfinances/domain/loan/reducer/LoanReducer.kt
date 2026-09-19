package com.jcadenas.xpendz.domain.loan.reducer

import com.jcadenas.xpendz.domain.loan.journal.LoanJournalEntry
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement

interface LoanReducer {
    fun reduce(events: Collection<LoanJournalEntry>): LoanReductionResult

    fun reduceCanonical(events: Collection<LoanMovement>): LoanReductionResult
}
