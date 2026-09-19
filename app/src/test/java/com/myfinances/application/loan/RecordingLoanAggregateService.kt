package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService

class RecordingLoanAggregateService(
    private val delegate: LoanAggregateService
) : LoanAggregateService {
    var callCount = 0
        private set

    override fun process(command: LoanCommand): LoanCommandResult {
        callCount++
        return delegate.process(command)
    }
}
