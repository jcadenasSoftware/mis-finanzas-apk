package com.jcadenas.xpendz.domain.loan.service.error

import com.jcadenas.xpendz.domain.loan.diagnostics.LoanDiagnostic

class InvariantViolation(
    code: LoanAggregateErrorCode,
    diagnostics: List<LoanDiagnostic> = emptyList()
) : LoanAggregateException(code, diagnostics)
