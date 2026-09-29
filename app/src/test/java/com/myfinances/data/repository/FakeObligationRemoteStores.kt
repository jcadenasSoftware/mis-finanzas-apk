package com.jcadenas.xpendz.data.repository

import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity

fun obligationFields(obligation: ObligationEntity): Map<String, Any?> = mapOf(
    "id" to obligation.id,
    "userUid" to obligation.userUid,
    "type" to obligation.type,
    "title" to obligation.title,
    "counterpartyName" to obligation.counterpartyName,
    "notes" to obligation.notes,
    "reference" to obligation.reference,
    "obligationCategoryId" to obligation.obligationCategoryId,
    "currency" to obligation.currency,
    "originalAmountCents" to obligation.originalAmountCents,
    "issuedAtEpochSec" to obligation.issuedAtEpochSec,
    "dueAtEpochSec" to obligation.dueAtEpochSec,
    "cancelledAtEpochSec" to obligation.cancelledAtEpochSec,
    "createdAtEpochSec" to obligation.createdAtEpochSec,
    "updatedAtEpochSec" to obligation.updatedAtEpochSec,
    "updatedBy" to obligation.updatedBy
)

fun settlementFields(settlement: ObligationSettlementEntity): Map<String, Any?> = mapOf(
    "id" to settlement.id,
    "obligationId" to settlement.obligationId,
    "userUid" to settlement.userUid,
    "accountId" to settlement.accountId,
    "amountCents" to settlement.amountCents,
    "occurredAtEpochSec" to settlement.occurredAtEpochSec,
    "linkedTransactionId" to settlement.linkedTransactionId,
    "note" to settlement.note,
    "createdAtEpochSec" to settlement.createdAtEpochSec,
    "updatedAtEpochSec" to settlement.updatedAtEpochSec,
    "updatedBy" to settlement.updatedBy
)

fun transactionFields(transaction: TransactionEntity): Map<String, Any?> = mapOf(
    "id" to transaction.id,
    "userUid" to transaction.userUid,
    "accountId" to transaction.accountId,
    "categoryId" to transaction.categoryId,
    "kind" to transaction.kind,
    "amountCents" to transaction.amountCents,
    "occurredAtEpochSec" to transaction.occurredAtEpochSec,
    "note" to transaction.note,
    "createdAtEpochSec" to transaction.createdAtEpochSec,
    "updatedAtEpochSec" to transaction.updatedAtEpochSec,
    "updatedBy" to transaction.updatedBy
)

class FakeObligationRemoteStore : ObligationRemoteStore {
    val docs = linkedMapOf<String, Map<String, Any?>>()
    var isFromCache = false
    var failUpserts = false
    var failDeletes = false
    var upsertCalls = 0
    var deleteCalls = 0
    var fetchCalls = 0

    override suspend fun upsertObligation(userUid: String, obligation: ObligationEntity) {
        upsertCalls++
        if (failUpserts) throw RuntimeException("upsert failed")
        docs[obligation.id] = obligationFields(obligation)
    }

    override suspend fun deleteObligation(userUid: String, obligationId: String) {
        deleteCalls++
        if (failDeletes) throw RuntimeException("delete failed")
        docs.remove(obligationId)
    }

    override suspend fun fetchObligations(userUid: String): ObligationRemoteSnapshot {
        fetchCalls++
        return ObligationRemoteSnapshot(
            documents = docs.map { ObligationRemoteDoc(id = it.key, fields = it.value) },
            isFromCache = isFromCache
        )
    }

    override suspend fun deleteAllObligationsByUser(userUid: String) {
        docs.clear()
    }
}

class FakeObligationSettlementRemoteStore : ObligationSettlementRemoteStore {
    val settlements = linkedMapOf<String, Map<String, Any?>>()
    val transactions = linkedMapOf<String, Map<String, Any?>>()
    var isFromCache = false
    var failUpserts = false
    var failDeletes = false
    var upsertSettlementCalls = 0
    var upsertTransactionCalls = 0
    var deleteSettlementCalls = 0
    var deleteTransactionCalls = 0

    override suspend fun upsertSettlement(userUid: String, settlement: ObligationSettlementEntity) {
        upsertSettlementCalls++
        if (failUpserts) throw RuntimeException("upsert failed")
        settlements[settlement.id] = settlementFields(settlement)
    }

    override suspend fun upsertSettlementTransaction(userUid: String, transaction: TransactionEntity) {
        upsertTransactionCalls++
        if (failUpserts) throw RuntimeException("upsert failed")
        transactions[transaction.id] = transactionFields(transaction)
    }

    override suspend fun deleteSettlement(userUid: String, settlementId: String) {
        deleteSettlementCalls++
        if (failDeletes) throw RuntimeException("delete failed")
        settlements.remove(settlementId)
    }

    override suspend fun deleteSettlementTransaction(userUid: String, transactionId: String) {
        deleteTransactionCalls++
        if (failDeletes) throw RuntimeException("delete failed")
        transactions.remove(transactionId)
    }

    override suspend fun fetchSettlements(userUid: String): ObligationSettlementRemoteSnapshot {
        return ObligationSettlementRemoteSnapshot(
            documents = settlements.map { ObligationSettlementRemoteDoc(id = it.key, fields = it.value) },
            isFromCache = isFromCache
        )
    }

    override suspend fun deleteAllSettlementsByUser(userUid: String) {
        settlements.clear()
        transactions.clear()
    }
}
