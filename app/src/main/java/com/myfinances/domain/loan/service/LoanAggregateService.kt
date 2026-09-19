package com.jcadenas.xpendz.domain.loan.service

import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand

interface LoanAggregateService {
    fun process(command: LoanCommand): LoanCommandResult
}
