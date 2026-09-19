package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.projection.LoanProjector
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository

class RecordingLoanProjector(
    private val delegate: LoanProjector
) : LoanProjector {
    var projectCallCount = 0
        private set
    var rebuildCallCount = 0
        private set

    override fun project(result: LoanCommandResult) {
        projectCallCount++
        delegate.project(result)
    }

    override fun rebuild(ownerId: String, loanId: String, repository: LoanRepository, reducer: LoanReducer) {
        rebuildCallCount++
        delegate.rebuild(ownerId, loanId, repository, reducer)
    }
}
