package com.jcadenas.xpendz.infrastructure.loan.sync

import com.jcadenas.xpendz.data.local.entity.LoanEntity
import com.jcadenas.xpendz.infrastructure.loan.admin.LoanAdminStateEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanMergePolicyTest {

    private fun loan(
        updatedAt: Long,
        updatedBy: String?,
        principal: Long = 100_000L,
        counterparty: String = "Ana",
        status: String = "OPEN",
        notes: String? = null,
        accountId: String? = "acc-1",
        createdAt: Long = 1_000L,
        currency: String = "COP"
    ) = LoanEntity(
        id = "loan-1",
        userUid = "owner-1",
        type = "LENT",
        counterpartyName = counterparty,
        accountId = accountId,
        currency = currency,
        principalCents = principal,
        status = status,
        notes = notes,
        createdAtEpochSec = createdAt,
        updatedAtEpochSec = updatedAt,
        updatedBy = updatedBy
    )

    private fun admin(
        updatedAt: Long,
        updatedBy: String?,
        archived: Boolean = false,
        archivedAt: Long? = null
    ) = LoanAdminStateEntity(
        loanId = "loan-1",
        ownerId = "owner-1",
        archived = archived,
        archivedAtEpochSec = archivedAt,
        updatedAtEpochSec = updatedAt,
        updatedBy = updatedBy,
        pendingSync = false
    )

    @Test
    fun remoteNewerWins() {
        assertTrue(LoanMergePolicy.shouldAcceptRemote(loan(1_000, "a"), loan(1_001, "b", principal = 120_000L)))
        assertTrue(LoanMergePolicy.shouldAcceptRemote(admin(1_000, "a"), admin(1_001, "b", archived = true, archivedAt = 1_001L)))
    }

    @Test
    fun remoteOlderLoses() {
        assertFalse(LoanMergePolicy.shouldAcceptRemote(loan(1_001, "b"), loan(1_000, "a", principal = 120_000L)))
        assertFalse(LoanMergePolicy.shouldAcceptRemote(admin(1_001, "b"), admin(1_000, "a", archived = true, archivedAt = 1_000L)))
    }

    @Test
    fun equalTimestampSameContentIsNoOp() {
        assertFalse(LoanMergePolicy.shouldAcceptRemote(loan(1_000, "device-a"), loan(1_000, "device-b")))
        assertFalse(LoanMergePolicy.shouldAcceptRemote(admin(1_000, "device-a"), admin(1_000, "device-b")))
    }

    @Test
    fun equalTimestampDifferentContentUsesUpdatedByTieBreak() {
        // b gana sobre a, de forma idéntica en ambas plataformas.
        assertTrue(LoanMergePolicy.shouldAcceptRemote(loan(1_000, "a", principal = 80_000L), loan(1_000, "b", principal = 120_000L)))
        assertTrue(LoanMergePolicy.shouldAcceptRemote(admin(1_000, "a", archived = false), admin(1_000, "b", archived = true, archivedAt = 1_000L)))

        assertFalse(LoanMergePolicy.shouldAcceptRemote(loan(1_000, "b", principal = 120_000L), loan(1_000, "a", principal = 80_000L)))
        assertFalse(LoanMergePolicy.shouldAcceptRemote(admin(1_000, "b", archived = true, archivedAt = 1_000L), admin(1_000, "a", archived = false)))
    }

    @Test
    fun equalTimestampSameUpdatedByFallsBackToSignatureAndIsIdempotent() {
        val local = loan(1_000, "device-x", principal = 80_000L)
        val remote = loan(1_000, "device-x", principal = 120_000L)
        val first = LoanMergePolicy.shouldAcceptRemote(local, remote)
        val winner = if (first) remote else local
        val second = LoanMergePolicy.shouldAcceptRemote(winner, winner)
        assertFalse(second)

        val localAdmin = admin(1_000, "device-x", archived = false)
        val remoteAdmin = admin(1_000, "device-x", archived = true, archivedAt = 1_000L)
        val firstAdmin = LoanMergePolicy.shouldAcceptRemote(localAdmin, remoteAdmin)
        val adminWinner = if (firstAdmin) remoteAdmin else localAdmin
        val secondAdmin = LoanMergePolicy.shouldAcceptRemote(adminWinner, adminWinner)
        assertFalse(secondAdmin)
    }
}
