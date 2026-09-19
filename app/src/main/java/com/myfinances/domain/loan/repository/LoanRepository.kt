package com.jcadenas.xpendz.domain.loan.repository

import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot

interface LoanRepository {
    fun getJournal(ownerId: String, loanId: String): List<LoanMovement>

    fun appendEvent(event: LoanMovement)

    fun findByOperationId(ownerId: String, operationId: String): LoanMovement?

    fun findByEventId(ownerId: String, eventId: String): LoanMovement?

    fun loadSnapshot(ownerId: String, loanId: String): LoanSnapshot?

    fun replaceSnapshot(snapshot: LoanSnapshot)
}
