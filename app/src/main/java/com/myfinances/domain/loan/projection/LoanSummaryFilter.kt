package com.jcadenas.xpendz.domain.loan.projection

import com.jcadenas.xpendz.domain.loan.journal.LoanType
import com.jcadenas.xpendz.domain.loan.snapshot.LoanStatus

data class LoanSummaryFilter(
    val loanType: LoanType? = null,
    val status: LoanStatus? = null,
    val sortBy: SortBy = SortBy.LAST_ACTIVITY,
    val ascending: Boolean = false,
    val limit: Int? = null
)
