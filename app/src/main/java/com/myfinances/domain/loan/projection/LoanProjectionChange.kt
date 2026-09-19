package com.jcadenas.xpendz.domain.loan.projection

data class LoanProjectionChange(
    val type: LoanProjectionChangeType,
    val sourceEventId: String?
)
