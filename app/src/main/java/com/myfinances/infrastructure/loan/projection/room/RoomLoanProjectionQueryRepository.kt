package com.jcadenas.xpendz.infrastructure.loan.projection.room

import com.jcadenas.xpendz.domain.loan.projection.LoanPaymentProjection
import com.jcadenas.xpendz.domain.loan.projection.LoanProjectionQueryRepository
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryFilter
import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection
import com.jcadenas.xpendz.domain.loan.projection.SortBy
import com.jcadenas.xpendz.infrastructure.loan.projection.mapper.LoanProjectionMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLoanProjectionQueryRepository(
    private val dao: LoanProjectionDao,
    private val mapper: LoanProjectionMapper = LoanProjectionMapper()
) : LoanProjectionQueryRepository {

    override fun getPaymentProjections(ownerId: String, loanId: String): List<LoanPaymentProjection> =
        dao.getPaymentProjections(ownerId, loanId).map(mapper::toDomain)

    override fun getSummaryProjection(ownerId: String, loanId: String): LoanSummaryProjection? =
        dao.getSummaryProjection(ownerId, loanId)?.let(mapper::toDomain)

    override fun listSummaries(ownerId: String, filter: LoanSummaryFilter): List<LoanSummaryProjection> {
        val entities = dao.listSummaryProjections(ownerId, filter.loanType?.name, filter.status?.name)

        val sorted = when (filter.sortBy) {
            SortBy.COUNTERPARTY -> if (filter.ascending) entities.sortedBy { it.counterparty } else entities.sortedByDescending { it.counterparty }
            SortBy.PRINCIPAL_CENTS -> if (filter.ascending) entities.sortedBy { it.principalCents } else entities.sortedByDescending { it.principalCents }
            SortBy.PENDING_CENTS -> if (filter.ascending) entities.sortedBy { it.pendingCents } else entities.sortedByDescending { it.pendingCents }
            SortBy.LAST_ACTIVITY -> if (filter.ascending) entities.sortedBy { it.lastActivity } else entities.sortedByDescending { it.lastActivity }
        }

        return (if (filter.limit != null && filter.limit > 0) sorted.take(filter.limit) else sorted)
            .map(mapper::toDomain)
    }

    override fun observeActiveSummaries(ownerId: String, filter: LoanSummaryFilter): Flow<List<LoanSummaryProjection>> {
        return dao.observeActiveSummaryProjections(ownerId, filter.loanType?.name)
            .map { entities ->
                val sorted = when (filter.sortBy) {
                    SortBy.COUNTERPARTY -> if (filter.ascending) entities.sortedBy { it.counterparty } else entities.sortedByDescending { it.counterparty }
                    SortBy.PRINCIPAL_CENTS -> if (filter.ascending) entities.sortedBy { it.principalCents } else entities.sortedByDescending { it.principalCents }
                    SortBy.PENDING_CENTS -> if (filter.ascending) entities.sortedBy { it.pendingCents } else entities.sortedByDescending { it.pendingCents }
                    SortBy.LAST_ACTIVITY -> if (filter.ascending) entities.sortedBy { it.lastActivity } else entities.sortedByDescending { it.lastActivity }
                }
                (if (filter.limit != null && filter.limit > 0) sorted.take(filter.limit) else sorted).map(mapper::toDomain)
            }
    }
}
