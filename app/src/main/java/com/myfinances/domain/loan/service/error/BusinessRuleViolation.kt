package com.jcadenas.xpendz.domain.loan.service.error

class BusinessRuleViolation(code: LoanAggregateErrorCode) : LoanAggregateException(code)
