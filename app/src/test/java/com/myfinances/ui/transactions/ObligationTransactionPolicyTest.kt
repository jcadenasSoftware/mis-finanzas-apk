package com.jcadenas.xpendz.ui.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObligationTransactionPolicyTest {

    @Test
    fun transactionLinkedFromSettlementIsProtected() {
        val linked = setOf("tx-1", "tx-2")

        assertTrue(ObligationTransactionPolicy.isObligationLinked("tx-1", linked))
        assertTrue(ObligationTransactionPolicy.isObligationLinked("tx-2", linked))
    }

    @Test
    fun unlinkedTransactionsAreNotProtected() {
        val linked = setOf("tx-1")

        listOf(null, "", "tx-1 ", "tx-3").forEach {
            assertFalse("$it no debe estar protegido",
                ObligationTransactionPolicy.isObligationLinked(it, linked))
        }
    }

    @Test
    fun emptyLinkedSetProtectsNothing() {
        assertFalse(ObligationTransactionPolicy.isObligationLinked("tx-1", emptySet()))
    }

    @Test
    fun protectedMessageIsClear() {
        assertTrue(
            ObligationTransactionPolicy.protectedMessage()
                .contains("obligación", ignoreCase = true)
        )
    }
}
