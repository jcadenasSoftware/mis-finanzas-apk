package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.aggregate.Outcome
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.projection.LoanProjector
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService

class LoanApplicationService(
    private val aggregate: LoanAggregateService,
    private val projector: LoanProjector
) {
    fun process(command: LoanCommand): LoanCommandResult {
        val result = aggregate.process(command)
        if (result.outcome == Outcome.APPLIED) {
            projector.project(result)
        }
        return result
    }
}
