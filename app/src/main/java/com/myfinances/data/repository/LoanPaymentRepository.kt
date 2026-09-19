package com.jcadenas.xpendz.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.data.local.dao.LoanDao
import com.jcadenas.xpendz.data.local.dao.LoanPaymentDao
import com.jcadenas.xpendz.data.local.entity.LoanPaymentEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanPaymentRepository @Inject constructor(
    private val loanPaymentDao: LoanPaymentDao,
    private val loanDao: LoanDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider,
    private val loanMovementRepository: LoanMovementRepository,
    private val transactionDao: com.jcadenas.xpendz.data.local.dao.TransactionDao
) : com.jcadenas.xpendz.infrastructure.loan.migration.ReversedLoanPaymentStore {
    fun observeByLoan(userUid: String, loanId: String): Flow<List<LoanPaymentEntity>> {
        return loanPaymentDao.observeByLoan(userUid, loanId)
    }

    suspend fun sumPrincipalByLoan(userUid: String, loanId: String): Long {
        return loanPaymentDao.sumPrincipalByLoan(userUid, loanId)
    }

    suspend fun create(
        userUid: String,
        loanId: String,
        accountId: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): LoanPaymentEntity {
        error("Use LoanApplicationService.process(RegisterPaymentCommand)")
    }

    suspend fun deleteAllByUser(userUid: String) {
        deleteAllLocalByUser(userUid)
        try {
            deleteAllRemoteByUser(userUid)
        } catch (e: Exception) {
            Log.e("LoanPaymentRepository", "Error al eliminar datos remotos en deleteAllByUser", e)
        }
    }

    internal suspend fun deleteAllLocalByUser(userUid: String) {
        loanPaymentDao.deleteAllByUser(userUid)
    }

    internal suspend fun deleteAllRemoteByUser(userUid: String) {
        val batch = firestore.batch()
        val collectionRef = firestore.collection("users")
            .document(userUid)
            .collection("loanPayments")
        val snapshot = collectionRef.get().await()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    suspend fun updateByTransaction(
        transactionId: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        note: String?
    ): LoanPaymentEntity? {
        val payment = loanPaymentDao.getByLinkedTransactionId(transactionId) ?: return null
        
        val now = System.currentTimeMillis() / 1000
        val updatedPayment = payment.copy(
            principalCents = principalCents,
            occurredAtEpochSec = occurredAtEpochSec,
            note = note,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanPaymentDao.update(updatedPayment)
        syncToFirestore(payment.userUid, updatedPayment)
        return updatedPayment
    }

    suspend fun deleteByTransaction(transactionId: String): LoanPaymentEntity? {
        val payment = loanPaymentDao.getByLinkedTransactionId(transactionId) ?: return null
        
        loanPaymentDao.delete(payment.id)
        deleteFromFirestore(payment.userUid, payment.id)
        return payment
    }

    /**
     * Elimina el registro de transporte asociado a un pago revertido. La fila
     * puede tener un id distinto al eventId del journal local (el doc remoto usa
     * el eventId del dispositivo origen), por eso la localización se hace por
     * transactionId vinculado o por la firma (loanId, accountId, monto, fecha)
     * que usa el reconciliador.
     */
    override suspend fun deleteByPaymentSignature(
        userUid: String,
        loanId: String,
        accountId: String?,
        principalCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String?
    ): LoanPaymentEntity? {
        val row = linkedTransactionId
            ?.let { loanPaymentDao.getByLinkedTransactionId(it) }
            ?: loanPaymentDao.getBySignature(userUid, loanId, accountId, principalCents, occurredAtEpochSec)
            ?: return null
        loanPaymentDao.delete(row.id)
        deleteFromFirestore(userUid, row.id)
        return row
    }

    /** Elimina solo la fila local; usado por la poda de pull (el doc remoto ya no existe). */
    suspend fun deleteLocal(id: String) {
        loanPaymentDao.delete(id)
    }

    override suspend fun deleteFromFirestore(userUid: String, paymentId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
                .document(paymentId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("LoanPaymentRepository", "Error deleting payment from Firestore", e)
        }
    }

    /**
     * Publica el pago en la colección de transporte users/{uid}/loanPayments
     * para que Desktop lo reconcilie en su journal canónico. El documento usa
     * el eventId canónico del pago como id para mantener correspondencia
     * determinista con el journal.
     */
    suspend fun publishPaymentToFirestore(
        userUid: String,
        paymentId: String,
        loanId: String,
        accountId: String,
        principalCents: Long,
        occurredAtEpochSec: Long,
        linkedTransactionId: String?,
        note: String?,
        createdAtEpochSec: Long,
        operationId: String? = null,
        eventId: String? = null
    ): Boolean {
        return try {
            val now = System.currentTimeMillis() / 1000
            val updatedBy = deviceIdProvider.get()
            val data = hashMapOf<String, Any>(
                "id" to paymentId,
                "userUid" to userUid,
                "loanId" to loanId,
                "accountId" to accountId,
                "principalCents" to principalCents,
                "occurredAtEpochSec" to occurredAtEpochSec,
                "createdAtEpochSec" to createdAtEpochSec,
                "updatedAtEpochSec" to now,
                "updatedBy" to updatedBy
            )
            linkedTransactionId?.let { data["linkedTransactionId"] = it }
            note?.let { data["note"] = it }
            firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
                .document(paymentId)
                .set(data, SetOptions.merge())
                .await()
            Log.d(
                "LoanPaymentTrace",
                "LOAN_PAYMENT_PUBLISHED loanId=$loanId paymentId=$paymentId transactionId=${linkedTransactionId ?: "-"} operationId=${operationId ?: "-"} eventId=${eventId ?: paymentId} updatedAt=$now updatedBy=${updatedBy ?: "-"} accountId=$accountId principalCents=$principalCents occurredAt=$occurredAtEpochSec"
            )
            true
        } catch (e: Exception) {
            Log.e(
                "LoanPaymentTrace",
                "PUBLISH_FAILED stage=loanPayment loanId=$loanId paymentId=$paymentId transactionId=${linkedTransactionId ?: "-"} operationId=${operationId ?: "-"} eventId=${eventId ?: paymentId} updatedAt=- updatedBy=- accountId=$accountId principalCents=$principalCents occurredAt=$occurredAtEpochSec",
                e
            )
            false
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("LoanPaymentRepository", "Syncing loanPayments from Firestore user=$userUid")
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
            val snapshot = collectionRef
                .get()
                .await()

            // Un doc remoto existe aunque su contenido no se pueda parsear: su
            // id cuenta como presente para que la poda no borre la fila local.
            val remoteIds = snapshot.documents.mapTo(HashSet()) { it.id }
            val payments = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    fun anyLong(vararg keys: String): Long? {
                        for (k in keys) {
                            val v = data[k]
                            when (v) {
                                is Number -> return v.toLong()
                                is String -> v.toLongOrNull()?.let { return it }
                            }
                        }
                        return null
                    }

                    fun anyString(vararg keys: String): String? {
                        for (k in keys) {
                            val v = data[k]
                            if (v is String && v.isNotBlank()) return v
                        }
                        return null
                    }

                    val loanId = anyString("loanId", "loan_id") ?: return@mapNotNull null
                    val accountId = anyString("accountId", "account_id") ?: return@mapNotNull null
                    val principalCents = anyLong("principalCents", "principal_cents") ?: return@mapNotNull null
                    val occurredAt = anyLong("occurredAtEpochSec", "occurred_at_epoch_sec") ?: return@mapNotNull null
                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
                    val note = (data["note"] as? String)
                    val updatedBy = anyString("updatedBy", "updated_by")
                    val linkedTransactionId = anyString("linkedTransactionId", "linked_transaction_id")

                    Log.d(
                        "LoanPaymentRepository",
                        "Parsed loanPayment doc=${doc.id} loanId=$loanId accountId=$accountId principalCents=$principalCents linkedTransactionId=$linkedTransactionId createdAt=$createdAt updatedAt=$updatedAt"
                    )

                    LoanPaymentEntity(
                        id = doc.id,
                        userUid = userUid,
                        loanId = loanId,
                        accountId = accountId,
                        principalCents = principalCents,
                        occurredAtEpochSec = occurredAt,
                        note = note,
                        linkedTransactionId = linkedTransactionId,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("LoanPaymentRepository", "Error parsing loanPayment doc=${doc.id}", e)
                    null
                }
            }

            var inserted = 0
            var updated = 0
            var skipped = 0
            for (p in payments) {
                try {
                    val existing = loanPaymentDao.getById(p.id)
                    if (existing == null) {
                        loanPaymentDao.insert(p)
                        inserted++
                    } else if (p.updatedAtEpochSec > existing.updatedAtEpochSec) {
                        loanPaymentDao.update(p)
                        updated++
                    }
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e(
                        "LoanPaymentRepository",
                        "FK error inserting loanPayment id=${p.id} loanId=${p.loanId} accountId=${p.accountId}",
                        e
                    )
                }
            }

            // Poda simétrica con Desktop: el snapshot remoto es autoritativo.
            // Una fila local ausente en remoto fue revertida en otro dispositivo;
            // conservarla haría que el replay la reconstruyera ("pago resucitado").
            // Solo con datos de servidor: un snapshot de caché puede estar vacío
            // o incompleto y no puede autorizar borrados.
            var pruned = 0
            if (snapshot.metadata.isFromCache) {
                Log.d("LoanPaymentRepository", "LoanPayments snapshot from cache; skipping prune")
            } else for (local in loanPaymentDao.getByUser(userUid)) {
                if (local.id !in remoteIds) {
                    try {
                        loanPaymentDao.delete(local.id)
                        pruned++
                        Log.d("LoanPaymentRepository", "Pruned local loanPayment id=${local.id} loanId=${local.loanId} (absent in remote)")
                    } catch (e: Exception) {
                        Log.e("LoanPaymentRepository", "Error pruning loanPayment ${local.id}", e)
                    }
                }
            }

            Log.d("LoanPaymentRepository", "LoanPayments inserted=$inserted updated=$updated skipped=$skipped pruned=$pruned")
        } catch (e: Exception) {
            Log.e("LoanPaymentRepository", "Error syncing loanPayments", e)
        }
    }

    private suspend fun syncToFirestore(userUid: String, payment: LoanPaymentEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("loanPayments")
                .document(payment.id)
                .set(payment, SetOptions.merge())
                .await()
        } catch (_: Exception) {
        }
    }

    /**
     * Migración retroactiva: crea LoanMovement para pagos históricos que no tienen movimiento.
     * Este método busca pagos sin movimiento correspondiente y crea el movimiento PAYMENT_IN/PAYMENT_OUT.
     */
    suspend fun migrateHistoricalPayments(userUid: String): MigrationResult {
        var migrated = 0
        var skipped = 0
        var errors = 0

        try {
            val payments = loanPaymentDao.getByUser(userUid)
            val allMovements = loanMovementRepository.getAllByUser(userUid).groupBy { it.loanId }

            for (payment in payments) {
                try {
                    // Verificar si ya existe movimiento para este pago
                    val loanMovements = allMovements[payment.loanId] ?: emptyList()
                    val hasMovement = loanMovements.any { movement ->
                        movement.movementType == "PAYMENT_IN" || movement.movementType == "PAYMENT_OUT" &&
                        movement.amountCents == payment.principalCents &&
                        movement.occurredAtEpochSec == payment.occurredAtEpochSec
                    }

                    if (hasMovement) {
                        skipped++
                        continue
                    }

                    // Obtener el préstamo para determinar el tipo de movimiento
                    val loan = loanDao.getById(payment.loanId) ?: run {
                        errors++
                        continue
                    }

                    // Determinar el tipo de movimiento según el tipo de préstamo
                    val movementType = when (loan.type) {
                        "LENT" -> "PAYMENT_IN"
                        "BORROWED" -> "PAYMENT_OUT"
                        else -> "PAYMENT"
                    }

                    // Buscar la transacción asociada por accountId, occurredAtEpochSec, kind y amountCents
                    val kind = when (loan.type) {
                        "LENT" -> "LOAN_REPAYMENT_PRINCIPAL_IN"
                        "BORROWED" -> "LOAN_REPAYMENT_PRINCIPAL_OUT"
                        else -> "LOAN_REPAYMENT_PRINCIPAL_IN"
                    }

                    val transactions = transactionDao.getFiltered(
                        userUid = userUid,
                        accountId = payment.accountId,
                        categoryId = null,
                        fromEpochSec = payment.occurredAtEpochSec,
                        toEpochSec = payment.occurredAtEpochSec,
                        limit = 100
                    )

                    val matchingTransaction = transactions.find { tx ->
                        tx.kind == kind && tx.amountCents == payment.principalCents
                    }

                    // Crear el movimiento de pago
                    loanMovementRepository.create(
                        userUid = userUid,
                        loanId = payment.loanId,
                        movementType = movementType,
                        amountCents = payment.principalCents,
                        accountId = payment.accountId,
                        linkedTransactionId = matchingTransaction?.id,
                        note = payment.note,
                        occurredAtEpochSec = payment.occurredAtEpochSec
                    )

                    migrated++
                } catch (e: Exception) {
                    errors++
                }
            }
        } catch (e: Exception) {
            return MigrationResult(migrated, skipped, errors, e.message)
        }

        return MigrationResult(migrated, skipped, errors, null)
    }

    data class MigrationResult(
        val migrated: Int,
        val skipped: Int,
        val errors: Int,
        val errorMessage: String?
    )
}
