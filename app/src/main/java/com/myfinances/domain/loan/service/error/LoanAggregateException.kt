package com.jcadenas.xpendz.domain.loan.service.error

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic

sealed class LoanAggregateException(
    val code: LoanAggregateErrorCode,
    val diagnostics: List<LoanDiagnostic> = emptyList(),
    cause: Throwable? = null
) : RuntimeException(code.name, cause)
