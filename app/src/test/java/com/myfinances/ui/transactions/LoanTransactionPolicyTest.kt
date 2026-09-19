package com.jcadenas.xpendz.ui.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanTransactionPolicyTest {

    @Test
    fun allLoanKindsAreProtected() {
        val loanKinds = listOf(
            "LOAN_LENT_OUT",
            "LOAN_BORROWED_IN",
            "LOAN_LENT_TOPUP",
            "LOAN_BORROWED_TOPUP",
            "LOAN_LENT_CORRECTION",
            "LOAN_BORROWED_CORRECTION",
            "LOAN_LENT_CORRECTION_IN",
            "LOAN_LENT_CORRECTION_OUT",
            "LOAN_BORROWED_CORRECTION_IN",
            "LOAN_BORROWED_CORRECTION_OUT",
            "LOAN_REPAYMENT_PRINCIPAL_IN",
            "LOAN_REPAYMENT_PRINCIPAL_OUT"
        )
        loanKinds.forEach { assertTrue("$it debe estar protegido", LoanTransactionPolicy.isLoanKind(it)) }
    }

    @Test
    fun normalKindsAreNotProtected() {
        listOf(null, "", "INCOME", "EXPENSE", "TRANSFER").forEach {
            assertFalse("$it no debe estar protegido", LoanTransactionPolicy.isLoanKind(it))
        }
    }

    @Test
    fun protectedMessageIsClear() {
        assertTrue(LoanTransactionPolicy.protectedMessage().contains("préstamo", ignoreCase = true))
    }
}
