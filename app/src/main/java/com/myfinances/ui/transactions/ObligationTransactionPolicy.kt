package com.jcadenas.xpendz.ui.transactions

/**
 * Política de protección de transacciones enlazadas a un settlement de
 * obligación. El enlace vive en obligation_settlements.linked_transaction_id
 * (no en transaction.kind), por lo que la UI lo resuelve con el conjunto de
 * ids enlazados que entrega el ViewModel.
 */
object ObligationTransactionPolicy {

    fun isObligationLinked(transactionId: String?, linkedTransactionIds: Set<String>): Boolean {
        return transactionId != null && transactionId in linkedTransactionIds
    }

    fun protectedMessage(): String =
        "Esta transacción pertenece a una obligación. Modifícala desde el módulo Obligaciones."
}
