package com.jcadenas.xpendz.domain.loan.admin

data class LoanAdminState(
    val loanId: String,
    val ownerId: String,
    val archived: Boolean,
    val archivedAtEpochSec: Long?,
    val updatedAtEpochSec: Long,
    val updatedBy: String?
)
