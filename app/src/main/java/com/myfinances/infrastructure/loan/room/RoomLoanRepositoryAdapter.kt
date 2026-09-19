package com.jcadenas.xpendz.infrastructure.loan.room

import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.repository.LoanRepository
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot
import com.jcadenas.xpendz.infrastructure.loan.mapper.LoanInfrastructureMapper

class RoomLoanRepositoryAdapter(
    private val database: AppDatabase,
    private val dao: CanonicalLoanDao,
    private val mapper: LoanInfrastructureMapper = LoanInfrastructureMapper()
) : LoanRepository {
    override fun getJournal(ownerId: String, loanId: String): List<LoanMovement> =
        dao.getJournal(ownerId, loanId).map(mapper::toDomain)

    override fun appendEvent(event: LoanMovement) {
        requireTransaction()
        dao.insertEvent(mapper.toEntity(event))
    }

    override fun findByOperationId(ownerId: String, operationId: String): LoanMovement? =
        dao.findByOperationId(ownerId, operationId)?.let(mapper::toDomain)

    override fun findByEventId(ownerId: String, eventId: String): LoanMovement? =
        dao.findByEventId(ownerId, eventId)?.let(mapper::toDomain)

    override fun loadSnapshot(ownerId: String, loanId: String): LoanSnapshot? =
        dao.loadSnapshot(ownerId, loanId)?.let(mapper::toDomain)

    override fun replaceSnapshot(snapshot: LoanSnapshot) {
        requireTransaction()
        dao.replaceSnapshot(mapper.toEntity(snapshot))
    }

    private fun requireTransaction() {
        if (!database.inTransaction()) {
            throw LoanPersistenceException("Loan writes require a transaction executor")
        }
    }
}
