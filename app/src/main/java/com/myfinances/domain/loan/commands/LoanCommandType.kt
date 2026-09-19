package com.jcadenas.xpendz.domain.loan.commands

enum class LoanCommandType {
    CREATE_LOAN,
    REGISTER_PAYMENT,
    ADD_PRINCIPAL,
    ADJUST_PRINCIPAL,
    UPDATE_METADATA,
    REVERSE_PAYMENT,
    CLOSE_LOAN
}
