package com.jcadenas.xpendz.domain.loan.service.error

class UnexpectedFailure(cause: Throwable) : LoanAggregateException(
    LoanAggregateErrorCode.UNEXPECTED_FAILURE,
    cause = cause
)
