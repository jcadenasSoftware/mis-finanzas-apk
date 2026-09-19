package com.jcadenas.xpendz.domain.loan.repository

import com.jcadenas.xpendz.domain.loan.journal.LoanMovement
import com.jcadenas.xpendz.domain.loan.snapshot.LoanSnapshot

class FakeLoanRepository : LoanRepository {
    private val journals = mutableMapOf<String, MutableList<LoanMovement>>()
    private val operations = mutableMapOf<String, LoanMovement>()
    private val events = mutableMapOf<String, LoanMovement>()
    private val snapshots = mutableMapOf<String, LoanSnapshot>()
    var appendCount: Int = 0
        private set

    override fun getJournal(ownerId: String, loanId: String): List<LoanMovement> =
        journals[loanKey(ownerId, loanId)]?.toList().orEmpty()

    override fun appendEvent(event: LoanMovement) {
        journals.getOrPut(loanKey(event.ownerId, event.loanId)) { mutableListOf() }.add(event)
        operations[operationKey(event.ownerId, event.operationId)] = event
        events[eventKey(event.ownerId, event.eventId)] = event
        appendCount++
    }

    override fun findByOperationId(ownerId: String, operationId: String): LoanMovement? =
        operations[operationKey(ownerId, operationId)]

    override fun findByEventId(ownerId: String, eventId: String): LoanMovement? =
        events[eventKey(ownerId, eventId)]

    override fun loadSnapshot(ownerId: String, loanId: String): LoanSnapshot? =
        snapshots[loanKey(ownerId, loanId)]

    override fun replaceSnapshot(snapshot: LoanSnapshot) {
        snapshots[loanKey(snapshot.ownerId, snapshot.loanId)] = snapshot
    }

    private fun loanKey(ownerId: String, loanId: String) = "$ownerId\u0000$loanId"

    private fun operationKey(ownerId: String, operationId: String) = "$ownerId\u0000$operationId"

    private fun eventKey(ownerId: String, eventId: String) = "$ownerId\u0000$eventId"
}
