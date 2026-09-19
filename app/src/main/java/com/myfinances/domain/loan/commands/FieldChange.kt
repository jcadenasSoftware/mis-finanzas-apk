package com.jcadenas.xpendz.domain.loan.commands

data class FieldChange<T>(val present: Boolean, val value: T?)
