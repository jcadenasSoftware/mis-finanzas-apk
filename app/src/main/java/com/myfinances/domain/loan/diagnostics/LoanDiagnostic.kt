package com.jcadenas.xpendz.domain.loan.diagnostics

data class LoanDiagnostic(
    val code: LoanDiagnosticCode,
    val primaryEventId: String?,
    val relatedEventId: String?
) {
    val category: LoanDiagnosticCategory
        get() = code.category
}
