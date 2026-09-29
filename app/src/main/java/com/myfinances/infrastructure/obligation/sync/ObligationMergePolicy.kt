package com.jcadenas.xpendz.infrastructure.obligation.sync

import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity

object ObligationMergePolicy {
    fun shouldAcceptRemote(local: ObligationEntity?, remote: ObligationEntity): Boolean {
        if (local == null) return true
        return shouldAcceptRemote(
            localUpdatedAt = local.updatedAtEpochSec,
            remoteUpdatedAt = remote.updatedAtEpochSec,
            localUpdatedBy = local.updatedBy,
            remoteUpdatedBy = remote.updatedBy,
            localSignature = obligationSignature(local),
            remoteSignature = obligationSignature(remote)
        )
    }

    fun shouldAcceptRemote(local: ObligationSettlementEntity?, remote: ObligationSettlementEntity): Boolean {
        if (local == null) return true
        return shouldAcceptRemote(
            localUpdatedAt = local.updatedAtEpochSec,
            remoteUpdatedAt = remote.updatedAtEpochSec,
            localUpdatedBy = local.updatedBy,
            remoteUpdatedBy = remote.updatedBy,
            localSignature = settlementSignature(local),
            remoteSignature = settlementSignature(remote)
        )
    }

    private fun shouldAcceptRemote(
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
        localUpdatedBy: String?,
        remoteUpdatedBy: String?,
        localSignature: String,
        remoteSignature: String
    ): Boolean {
        return when {
            remoteUpdatedAt > localUpdatedAt -> true
            remoteUpdatedAt < localUpdatedAt -> false
            localSignature == remoteSignature -> false
            else -> {
                val authorCmp = normalize(remoteUpdatedBy).compareTo(normalize(localUpdatedBy))
                when {
                    authorCmp != 0 -> authorCmp > 0
                    else -> remoteSignature.compareTo(localSignature) > 0
                }
            }
        }
    }

    private fun obligationSignature(obligation: ObligationEntity): String = buildString {
        append(normalize(obligation.type))
        append('|')
        append(normalize(obligation.title))
        append('|')
        append(normalize(obligation.counterpartyName))
        append('|')
        append(normalize(obligation.notes))
        append('|')
        append(normalize(obligation.reference))
        append('|')
        append(normalize(obligation.obligationCategoryId))
        append('|')
        append(normalize(obligation.currency))
        append('|')
        append(obligation.originalAmountCents)
        append('|')
        append(obligation.issuedAtEpochSec)
        append('|')
        append(obligation.dueAtEpochSec ?: -1L)
        append('|')
        append(obligation.cancelledAtEpochSec ?: -1L)
        append('|')
        append(obligation.createdAtEpochSec)
        append('|')
        append(normalize(obligation.userUid))
        append('|')
        append(normalize(obligation.id))
    }

    private fun settlementSignature(settlement: ObligationSettlementEntity): String = buildString {
        append(normalize(settlement.obligationId))
        append('|')
        append(normalize(settlement.accountId))
        append('|')
        append(settlement.amountCents)
        append('|')
        append(settlement.occurredAtEpochSec)
        append('|')
        append(normalize(settlement.linkedTransactionId))
        append('|')
        append(normalize(settlement.note))
        append('|')
        append(settlement.createdAtEpochSec)
        append('|')
        append(normalize(settlement.userUid))
        append('|')
        append(normalize(settlement.id))
    }

    private fun normalize(value: String?): String = value?.trim().orEmpty()
}
