package com.jcadenas.xpendz.domain.loan.projection

import kotlinx.coroutines.flow.Flow

interface LoanProjectionQueryRepository {
    fun getPaymentProjections(ownerId: String, loanId: String): List<LoanPaymentProjection>
    fun getSummaryProjection(ownerId: String, loanId: String): LoanSummaryProjection?
    fun listSummaries(ownerId: String, filter: LoanSummaryFilter): List<LoanSummaryProjection>
    fun observeActiveSummaries(ownerId: String, filter: LoanSummaryFilter): Flow<List<LoanSummaryProjection>>
}
