package com.jcadenas.xpendz.domain.loan.projection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeLoanProjectionQueryRepository(
    private val projector: FakeLoanProjector
) : LoanProjectionQueryRepository {

    override fun getPaymentProjections(ownerId: String, loanId: String): List<LoanPaymentProjection> =
        projector.getPaymentProjections(ownerId, loanId)

    override fun getSummaryProjection(ownerId: String, loanId: String): LoanSummaryProjection? =
        projector.getSummaryProjection(ownerId, loanId)

    override fun listSummaries(ownerId: String, filter: LoanSummaryFilter): List<LoanSummaryProjection> =
        projector.listSummaries(ownerId, filter)

    override fun observeActiveSummaries(ownerId: String, filter: LoanSummaryFilter): Flow<List<LoanSummaryProjection>> =
        flowOf(listSummaries(ownerId, filter))
}
