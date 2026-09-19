package com.jcadenas.xpendz.infrastructure.loan.room

import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.commands.LoanCommand
import com.jcadenas.xpendz.domain.loan.service.LoanAggregateService

class RoomLoanAggregateExecutor(
    private val database: AppDatabase,
    private val delegate: LoanAggregateService
) : LoanAggregateService {
    override fun process(command: LoanCommand): LoanCommandResult =
        database.runInTransaction<LoanCommandResult> {
            delegate.process(command)
        }
}
