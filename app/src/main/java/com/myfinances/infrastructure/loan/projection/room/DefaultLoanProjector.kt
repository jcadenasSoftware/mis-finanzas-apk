package com.jcadenas.xpendz.infrastructure.loan.projection.room

import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.aggregate.LoanCommandResult
import com.jcadenas.xpendz.domain.loan.journal.LoanEventType
import com.jcadenas.xpendz.domain.loan.projection.LoanProjector
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionChangeType
import com.jcadenas.xpendz.domain.loan.reducer.LoanReducer
import com.jcadenas.xpendz.domain.loan.reducer.ReductionResultType
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository
import com.jcadenas.xpendz.infrastructure.loan.projection.mapper.LoanProjectionMapper

class DefaultLoanProjector(
    private val database: AppDatabase,
    private val dao: LoanProjectionDao,
    private val mapper: LoanProjectionMapper = LoanProjectionMapper()
) : LoanProjector {

    override fun project(result: LoanCommandResult) {
        database.runInTransaction {
            for (change in result.projectionChanges) {
                when (change.type) {
                    LoanProjectionChangeType.NONE -> Unit
                    LoanProjectionChangeType.ADD_PAYMENT_PROJECTION -> dao.insertPaymentProjection(
                        mapper.toEntity(mapper.toPaymentProjection(result.event))
                    )

                    LoanProjectionChangeType.REMOVE_PAYMENT_PROJECTION -> change.sourceEventId?.let {
                        dao.deletePaymentProjection(it)
                    }

                    LoanProjectionChangeType.REBUILD_LOAN_SNAPSHOT -> {
                        val current = result.currentSnapshot
                        val payments = dao.getPaymentProjections(current.ownerId, current.loanId)
                        val paymentCount = payments.size
                        val lastPaymentAt = payments.maxOfOrNull { it.occurredAt }
                        dao.replaceSummaryProjection(
                            mapper.toEntity(mapper.toSummaryProjection(current, paymentCount, lastPaymentAt))
                        )
                    }
                }
            }
        }
    }

    override fun rebuild(ownerId: String, loanId: String, repository: LoanRepository, reducer: LoanReducer) {
        val journal = repository.getJournal(ownerId, loanId)
        val reduction = reducer.reduceCanonical(journal)
        if (reduction.type != ReductionResultType.VALID || reduction.snapshot == null) {
            return
        }
        database.runInTransaction {
            dao.deletePaymentProjections(ownerId, loanId)
            dao.deleteSummaryProjection(ownerId, loanId)
            for (event in reduction.effectiveEvents) {
                if (event.eventType == LoanEventType.PAYMENT) {
                    dao.insertPaymentProjection(mapper.toEntity(mapper.toPaymentProjection(event)))
                }
            }
            val payments = dao.getPaymentProjections(ownerId, loanId)
            val paymentCount = payments.size
            val lastPaymentAt = payments.maxOfOrNull { it.occurredAt }
            dao.replaceSummaryProjection(mapper.toEntity(mapper.toSummaryProjection(reduction.snapshot!!, paymentCount, lastPaymentAt)))
        }
    }
}
