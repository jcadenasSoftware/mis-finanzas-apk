package com.jcadenas.xpendz.domain.loan.projection

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository

interface LoanProjector {
    fun project(result: LoanCommandResult)
    fun rebuild(ownerId: String, loanId: String, repository: LoanRepository, reducer: LoanReducer)
}
