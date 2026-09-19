package com.jcadenas.xpendz.domain.loan.projection

enum class LoanProjectionChangeType {
    NONE,
    ADD_PAYMENT_PROJECTION,
    REMOVE_PAYMENT_PROJECTION,
    REBUILD_LOAN_SNAPSHOT
}
