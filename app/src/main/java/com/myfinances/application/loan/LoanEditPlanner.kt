package com.jcadenas.xpendz.application.loan

import com.jcadenas.xpendz.domain.loan.projection.LoanSummaryProjection

/**
 * Decide qué comandos debe emitir la edición de un préstamo comparando los
 * valores nuevos contra la proyección actual, antes de llamar a
 * LoanApplicationService.
 *
 * Reglas:
 * - principalChanged  -> AdjustPrincipalCommand
 * - metadataChanged   -> UpdateMetadataCommand (solo con los campos que difieren)
 * - sin cambios       -> ningún comando
 *
 * Así un ajuste de solo monto nunca produce un UpdateMetadataCommand que el
 * aggregate rechazaría con NO_EFFECTIVE_CHANGE, y un "Guardar" repetido sobre
 * el mismo estado no vuelve a calcular deltas.
 */
object LoanEditPlanner {

    data class Decision(
        val principalChanged: Boolean,
        val deltaCents: Long,
        val counterpartyChanged: Boolean,
        val counterpartyName: String?,
        val accountChanged: Boolean,
        val accountId: String?,
        val notesChanged: Boolean,
        val notes: String?
    ) {
        val metadataChanged: Boolean
            get() = counterpartyChanged || accountChanged || notesChanged

        val hasChanges: Boolean
            get() = principalChanged || metadataChanged
    }

    fun decide(
        existing: LoanSummaryProjection,
        counterpartyName: String?,
        accountId: String?,
        principalCents: Long?,
        notes: String?
    ): Decision {
        val principalChanged = principalCents != null && principalCents != existing.principalCents
        val counterpartyValue = counterpartyName?.trim()?.takeIf { it.isNotBlank() }
        val notesValue = notes?.trim()?.takeIf { it.isNotBlank() }
        return Decision(
            principalChanged = principalChanged,
            deltaCents = if (principalChanged) principalCents!! - existing.principalCents else 0L,
            counterpartyChanged = counterpartyValue != null && counterpartyValue != existing.counterparty,
            counterpartyName = counterpartyValue,
            accountChanged = accountId != null && accountId != existing.defaultAccountId,
            accountId = accountId,
            notesChanged = notesValue != null && notesValue != existing.notes,
            notes = notesValue
        )
    }
}
