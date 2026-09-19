package com.jcadenas.xpendz.ui.transactions

object LoanTransactionPolicy {
    private val loanKinds = setOf(
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

    fun isLoanKind(kind: String?): Boolean {
        return kind?.trim()?.uppercase() in loanKinds
    }

    fun protectedMessage(): String = "Esta transacción pertenece a un préstamo. Modifícala desde el módulo Préstamos."
}
