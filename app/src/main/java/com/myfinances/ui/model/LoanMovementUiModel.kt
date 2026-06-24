package com.jcadenas.xpendz.ui.model

import java.text.NumberFormat
import java.util.Locale

data class LoanMovementUiModel(
    val id: String,
    val loanId: String,
    val movementType: String,
    val amountCents: Long,
    val amountFormatted: String,
    val accountId: String?,
    val note: String?,
    val occurredAtEpochSec: Long,
    val occurredAtFormatted: String,
    val createdAtEpochSec: Long,
    val updatedAtEpochSec: Long,
    val updatedBy: String?,
    val linkedTransactionId: String?
) {
    companion object {
        fun fromEntity(
            entity: com.jcadenas.xpendz.data.local.entity.LoanMovementEntity,
            currency: String = "USD"
        ): LoanMovementUiModel {
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
            val amount = entity.amountCents / 100.0
            
            try {
                if (currency.isNotBlank()) {
                    currencyFormat.currency = java.util.Currency.getInstance(currency)
                }
            } catch (e: Exception) {
                currencyFormat.currency = java.util.Currency.getInstance("USD")
            }
            
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val occurredDate = java.util.Date(entity.occurredAtEpochSec * 1000)
            
            return LoanMovementUiModel(
                id = entity.id,
                loanId = entity.loanId,
                movementType = entity.movementType,
                amountCents = entity.amountCents,
                amountFormatted = currencyFormat.format(amount),
                accountId = entity.accountId,
                note = entity.note,
                occurredAtEpochSec = entity.occurredAtEpochSec,
                occurredAtFormatted = dateFormat.format(occurredDate),
                createdAtEpochSec = entity.createdAtEpochSec,
                updatedAtEpochSec = entity.updatedAtEpochSec,
                updatedBy = entity.updatedBy,
                linkedTransactionId = entity.linkedTransactionId
            )
        }
    }
}
