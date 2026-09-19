package com.jcadenas.xpendz.domain.loan.service.error

class ValidationError(code: LoanAggregateErrorCode) : LoanAggregateException(code)
