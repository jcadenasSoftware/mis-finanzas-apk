package com.jcadenas.xpendz.infrastructure.loan.sync

import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateEntity

object LoanMergePolicy {
    fun shouldAcceptRemote(local: LoanEntity?, remote: LoanEntity): Boolean {
        if (local == null) return true
        return shouldAcceptRemote(
            localUpdatedAt = local.updatedAtEpochSec,
            remoteUpdatedAt = remote.updatedAtEpochSec,
            localUpdatedBy = local.updatedBy,
            remoteUpdatedBy = remote.updatedBy,
            localSignature = loanSignatureOrEmpty(local),
            remoteSignature = loanSignatureOrEmpty(remote)
        )
    }

    fun shouldAcceptRemote(local: LoanAdminStateEntity?, remote: LoanAdminStateEntity): Boolean {
        if (local == null) return true
        return shouldAcceptRemote(
            localUpdatedAt = local.updatedAtEpochSec,
            remoteUpdatedAt = remote.updatedAtEpochSec,
            localUpdatedBy = local.updatedBy,
            remoteUpdatedBy = remote.updatedBy,
            localSignature = adminSignatureOrEmpty(local),
            remoteSignature = adminSignatureOrEmpty(remote)
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

    private fun loanSignature(loan: LoanEntity): String = buildString {
        append(normalize(loan.type))
        append('|')
        append(normalize(loan.counterpartyName))
        append('|')
        append(normalize(loan.accountId))
        append('|')
        append(loan.principalCents)
        append('|')
        append(normalize(loan.currency))
        append('|')
        append(normalize(loan.status))
        append('|')
        append(normalize(loan.notes))
        append('|')
        append(loan.createdAtEpochSec)
        append('|')
        append(loan.userUid)
        append('|')
        append(loan.id)
    }

    private fun loanSignatureOrEmpty(loan: LoanEntity?) = loan?.let(::loanSignature).orEmpty()

    private fun adminSignature(state: LoanAdminStateEntity): String = buildString {
        append(state.archived)
        append('|')
        append(state.archivedAtEpochSec ?: -1L)
        append('|')
        append(state.ownerId)
        append('|')
        append(state.loanId)
    }

    private fun adminSignatureOrEmpty(state: LoanAdminStateEntity?) = state?.let(::adminSignature).orEmpty()

    private fun normalize(value: String?): String = value?.trim().orEmpty()
}
