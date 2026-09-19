package com.jcadenas.xpendz.domain.loan.journal

enum class LoanEventType {
    CREATION,
    TOPUP,
    PAYMENT,
    ADJUSTMENT,
    METADATA_CHANGED,
    REVERSAL,
    CLOSE
}
