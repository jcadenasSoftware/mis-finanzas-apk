package com.jcadenas.xpendz.data.repository

import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.dao.AccountDao
import com.jcadenas.xpendz.data.local.dao.GoalDao
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.GoalEntity
import com.jcadenas.xpendz.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class GoalRemoteDoc(
    val id: String,
    val fields: Map<String, Any?>
)

interface GoalRemoteStore {
    suspend fun upsertGoal(userUid: String, goal: GoalEntity)
    suspend fun deleteGoal(userUid: String, goalId: String)
    suspend fun fetchGoals(userUid: String): List<GoalRemoteDoc>
    suspend fun deleteAllGoalsByUser(userUid: String)
    suspend fun upsertGoalAccount(userUid: String, account: AccountEntity)
    suspend fun deleteGoalAccount(userUid: String, accountId: String)
}

@Singleton
class FirestoreGoalRemoteStore @Inject constructor(
    private val firestore: FirebaseFirestore
) : GoalRemoteStore {

    override suspend fun upsertGoal(userUid: String, goal: GoalEntity) {
        firestore.collection("users")
            .document(userUid)
            .collection("goals")
            .document(goal.id)
            .set(goal, SetOptions.merge())
            .await()
    }

    override suspend fun deleteGoal(userUid: String, goalId: String) {
        firestore.collection("users")
            .document(userUid)
            .collection("goals")
            .document(goalId)
            .delete()
            .await()
    }

    override suspend fun fetchGoals(userUid: String): List<GoalRemoteDoc> {
        val snapshot = firestore.collection("users")
            .document(userUid)
            .collection("goals")
            .get()
            .await()
        return snapshot.documents.map { GoalRemoteDoc(it.id, it.data ?: emptyMap()) }
    }

    override suspend fun deleteAllGoalsByUser(userUid: String) {
        val batch = firestore.batch()
        val collectionRef = firestore.collection("users")
            .document(userUid)
            .collection("goals")
        val snapshot = collectionRef.get().await()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    override suspend fun upsertGoalAccount(userUid: String, account: AccountEntity) {
        firestore.collection("users")
            .document(userUid)
            .collection("accounts")
            .document(account.id)
            .set(account, SetOptions.merge())
            .await()
    }

    override suspend fun deleteGoalAccount(userUid: String, accountId: String) {
        firestore.collection("users")
            .document(userUid)
            .collection("accounts")
            .document(accountId)
            .delete()
            .await()
    }
}

enum class GoalDeletionOutcome { DELETED, ARCHIVED }

data class GoalDeletionInfo(
    val balanceCents: Long,
    val hasHistory: Boolean
)

@Singleton
class GoalRepository @Inject constructor(
    private val db: AppDatabase,
    private val goalDao: GoalDao,
    private val accountDao: AccountDao,
    private val remoteStore: GoalRemoteStore,
    private val deviceIdProvider: DeviceIdProvider,
    private val sharedPreferences: SharedPreferences
) {
    private companion object {
        const val TAG = "GoalRepository"
        const val KEY_PENDING_PUSH = "goal_pending_push_ids"
        const val KEY_PENDING_DELETE = "goal_pending_delete_ids"
        const val KEY_PENDING_ACCOUNT_PUSH = "goal_pending_account_push_ids"
        const val KEY_PENDING_ACCOUNT_DELETE = "goal_pending_account_delete_ids"
    }

    fun observeByUser(userUid: String): Flow<List<GoalEntity>> {
        return goalDao.observeByUser(userUid)
    }

    suspend fun getByUser(userUid: String): List<GoalEntity> {
        return goalDao.getByUser(userUid)
    }

    suspend fun getById(id: String): GoalEntity? {
        return goalDao.getById(id)
    }

    suspend fun requireOpen(goalId: String): GoalEntity {
        val goal = goalDao.getById(goalId) ?: throw IllegalArgumentException("Meta no encontrada")
        check(goal.status == GoalEntity.STATUS_OPEN) { "goal_not_open" }
        return goal
    }

    suspend fun createWithAccount(
        userUid: String,
        name: String,
        currency: String,
        targetCents: Long,
        targetDateEpochSec: Long
    ): GoalEntity {
        require(name.isNotBlank()) { "name" }
        require(targetCents > 0) { "targetCents" }
        val now = System.currentTimeMillis() / 1000
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            name = "Meta: ${name.trim()}",
            type = "SAVINGS",
            currency = currency,
            iconKey = null,
            colorHex = null,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        val goal = GoalEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            name = name.trim(),
            currency = currency,
            targetCents = targetCents,
            targetDateEpochSec = targetDateEpochSec,
            accountId = account.id,
            status = GoalEntity.STATUS_OPEN,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        db.withTransaction {
            accountDao.insert(account)
            goalDao.insert(goal)
        }
        pushGoalAccount(userUid, account)
        pushGoal(userUid, goal)
        return goal
    }

    suspend fun update(
        userUid: String,
        goalId: String,
        name: String,
        currency: String,
        targetCents: Long,
        targetDateEpochSec: Long
    ): GoalEntity {
        val existing = requireOpen(goalId)
        require(name.isNotBlank()) { "name" }
        require(targetCents > 0) { "targetCents" }
        val updated = existing.copy(
            name = name.trim(),
            currency = currency,
            targetCents = targetCents,
            targetDateEpochSec = targetDateEpochSec,
            updatedAtEpochSec = nextUpdatedAt(existing.updatedAtEpochSec),
            updatedBy = deviceIdProvider.get()
        )
        goalDao.update(updated)
        pushGoal(userUid, updated)
        return updated
    }

    suspend fun close(userUid: String, goalId: String): GoalEntity {
        val existing = goalDao.getById(goalId) ?: throw IllegalArgumentException("Meta no encontrada")
        check(existing.status == GoalEntity.STATUS_OPEN) { "goal_not_open" }
        val updated = existing.copy(
            status = GoalEntity.STATUS_CLOSED,
            updatedAtEpochSec = nextUpdatedAt(existing.updatedAtEpochSec),
            updatedBy = deviceIdProvider.get()
        )
        goalDao.update(updated)
        pushGoal(userUid, updated)
        return updated
    }

    suspend fun reopen(userUid: String, goalId: String): GoalEntity {
        val existing = goalDao.getById(goalId) ?: throw IllegalArgumentException("Meta no encontrada")
        check(existing.status == GoalEntity.STATUS_CLOSED) { "goal_not_archived" }
        val updated = existing.copy(
            status = GoalEntity.STATUS_OPEN,
            updatedAtEpochSec = nextUpdatedAt(existing.updatedAtEpochSec),
            updatedBy = deviceIdProvider.get()
        )
        goalDao.update(updated)
        pushGoal(userUid, updated)
        return updated
    }

    suspend fun getDeletionInfo(userUid: String, goalId: String): GoalDeletionInfo? {
        val goal = goalDao.getById(goalId) ?: return null
        return GoalDeletionInfo(
            balanceCents = accountDao.computeBalanceCents(userUid, goal.accountId),
            hasHistory = accountDao.hasMovements(userUid, goal.accountId)
        )
    }

    suspend fun deleteGoal(
        userUid: String,
        goalId: String,
        deleteLinkedAccount: Boolean = true
    ): GoalDeletionOutcome {
        val goal = goalDao.getById(goalId) ?: throw IllegalArgumentException("Meta no encontrada")

        val balance = accountDao.computeBalanceCents(userUid, goal.accountId)
        check(balance <= 0L) { "goal_has_balance" }

        if (accountDao.hasMovements(userUid, goal.accountId)) {
            if (goal.status != GoalEntity.STATUS_CLOSED) {
                val archived = goal.copy(
                    status = GoalEntity.STATUS_CLOSED,
                    updatedAtEpochSec = nextUpdatedAt(goal.updatedAtEpochSec),
                    updatedBy = deviceIdProvider.get()
                )
                goalDao.update(archived)
                pushGoal(userUid, archived)
            }
            return GoalDeletionOutcome.ARCHIVED
        }

        goalDao.delete(goalId)
        markPending(KEY_PENDING_DELETE, goalId)
        try {
            remoteStore.deleteGoal(userUid, goalId)
            clearPending(KEY_PENDING_DELETE, goalId)
        } catch (e: Exception) {
            Log.w(TAG, "Remote goal delete failed id=$goalId; kept pending", e)
        }

        if (deleteLinkedAccount) {
            try {
                accountDao.delete(goal.accountId)
                markPending(KEY_PENDING_ACCOUNT_DELETE, goal.accountId)
                try {
                    remoteStore.deleteGoalAccount(userUid, goal.accountId)
                    clearPending(KEY_PENDING_ACCOUNT_DELETE, goal.accountId)
                } catch (e: Exception) {
                    Log.w(TAG, "Remote account delete failed id=${goal.accountId}; kept pending", e)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Linked account delete failed id=${goal.accountId}", e)
            }
        }
        return GoalDeletionOutcome.DELETED
    }

    suspend fun deleteAllByUser(userUid: String) {
        deleteAllLocalByUser(userUid)
        try {
            deleteAllRemoteByUser(userUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar datos remotos en deleteAllByUser", e)
        }
    }

    internal suspend fun deleteAllLocalByUser(userUid: String) {
        goalDao.deleteAllByUser(userUid)
    }

    internal suspend fun deleteAllRemoteByUser(userUid: String) {
        remoteStore.deleteAllGoalsByUser(userUid)
    }

    suspend fun syncFromFirestore(userUid: String) {
        retryPendingRemoteOps(userUid)

        val remoteDocs = remoteStore.fetchGoals(userUid)

        val goals = mutableListOf<GoalEntity>()
        val rejectedRemoteIds = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val parsed = parseRemoteGoal(doc.id, doc.fields, userUid)
            if (parsed == null) {
                rejectedRemoteIds += doc.id
            } else {
                goals += parsed
            }
        }

        val pendingDeletes = pendingIds(KEY_PENDING_DELETE)
        var inserted = 0
        var updated = 0
        var skipped = 0

        for (g in goals) {
            if (pendingDeletes.contains(g.id)) continue
            try {
                val existing = goalDao.getById(g.id)
                if (existing == null) {
                    goalDao.insert(g)
                    inserted++
                } else {
                    if (g.updatedAtEpochSec <= existing.updatedAtEpochSec) {
                        continue
                    }
                    goalDao.update(g)
                    updated++
                }
            } catch (e: SQLiteConstraintException) {
                skipped++
                Log.e(TAG, "FK error upserting goal id=${g.id} account=${g.accountId}", e)
            } catch (e: Exception) {
                skipped++
                Log.e(TAG, "Error upserting goal id=${g.id}", e)
            }
        }

        Log.d(TAG, "Goals upserted inserted=$inserted updated=$updated skipped=$skipped rejected=${rejectedRemoteIds.size}")

        val remoteIds = goals.map { it.id }.toSet() + rejectedRemoteIds
        val pendingPushes = pendingIds(KEY_PENDING_PUSH)
        val localAll = goalDao.getByUser(userUid)
        var deleted = 0
        for (local in localAll) {
            if (remoteIds.contains(local.id) || pendingPushes.contains(local.id)) continue
            try {
                goalDao.delete(local.id)
                deleted++
            } catch (e: Exception) {
                Log.w(TAG, "Could not prune goal ${local.id}: ${e.message}")
            }
        }
        if (deleted > 0) Log.d(TAG, "Goals pruned deleted=$deleted")
    }

    private suspend fun retryPendingRemoteOps(userUid: String) {
        for (id in pendingIds(KEY_PENDING_DELETE)) {
            try {
                remoteStore.deleteGoal(userUid, id)
                clearPending(KEY_PENDING_DELETE, id)
            } catch (e: Exception) {
                Log.w(TAG, "Pending goal delete retry failed id=$id", e)
            }
        }
        for (id in pendingIds(KEY_PENDING_ACCOUNT_DELETE)) {
            try {
                remoteStore.deleteGoalAccount(userUid, id)
                clearPending(KEY_PENDING_ACCOUNT_DELETE, id)
            } catch (e: Exception) {
                Log.w(TAG, "Pending account delete retry failed id=$id", e)
            }
        }
        for (id in pendingIds(KEY_PENDING_PUSH)) {
            val local = goalDao.getById(id)
            if (local == null) {
                clearPending(KEY_PENDING_PUSH, id)
                continue
            }
            try {
                remoteStore.upsertGoal(userUid, local)
                clearPending(KEY_PENDING_PUSH, id)
            } catch (e: Exception) {
                Log.w(TAG, "Pending goal push retry failed id=$id", e)
            }
        }
        for (id in pendingIds(KEY_PENDING_ACCOUNT_PUSH)) {
            val account = accountDao.getById(id)
            if (account == null) {
                clearPending(KEY_PENDING_ACCOUNT_PUSH, id)
                continue
            }
            try {
                remoteStore.upsertGoalAccount(userUid, account)
                clearPending(KEY_PENDING_ACCOUNT_PUSH, id)
            } catch (e: Exception) {
                Log.w(TAG, "Pending account push retry failed id=$id", e)
            }
        }
    }

    private fun parseRemoteGoal(id: String, data: Map<String, Any?>, userUid: String): GoalEntity? {
        fun reject(reason: String): GoalEntity? {
            Log.w(TAG, "Remote goal rejected id=$id reason=$reason")
            return null
        }

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

        val name = anyString("name") ?: return reject("missingName")
        val currency = anyString("currency") ?: return reject("missingCurrency")
        val targetCents = anyLong("targetCents", "target_cents") ?: return reject("missingTargetCents")
        val targetDate = anyLong("targetDateEpochSec", "target_date_epoch_sec") ?: return reject("missingTargetDate")
        val accountId = anyString("accountId", "account_id") ?: return reject("missingAccountId")
        val status = GoalEntity.normalizeStatus(anyString("status"), id)
        val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
        val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt
        val updatedBy = anyString("updatedBy", "updated_by")

        return GoalEntity(
            id = id,
            userUid = userUid,
            name = name,
            currency = currency,
            targetCents = targetCents,
            targetDateEpochSec = targetDate,
            accountId = accountId,
            status = status,
            createdAtEpochSec = createdAt,
            updatedAtEpochSec = updatedAt,
            updatedBy = updatedBy
        )
    }

    private suspend fun pushGoal(userUid: String, goal: GoalEntity) {
        markPending(KEY_PENDING_PUSH, goal.id)
        try {
            remoteStore.upsertGoal(userUid, goal)
            clearPending(KEY_PENDING_PUSH, goal.id)
        } catch (e: Exception) {
            Log.w(TAG, "Goal push failed id=${goal.id}; kept pending", e)
        }
    }

    private suspend fun pushGoalAccount(userUid: String, account: AccountEntity) {
        markPending(KEY_PENDING_ACCOUNT_PUSH, account.id)
        try {
            remoteStore.upsertGoalAccount(userUid, account)
            clearPending(KEY_PENDING_ACCOUNT_PUSH, account.id)
        } catch (e: Exception) {
            Log.w(TAG, "Goal account push failed id=${account.id}; kept pending", e)
        }
    }

    private fun nextUpdatedAt(previous: Long): Long {
        val now = System.currentTimeMillis() / 1000
        return if (now <= previous) previous + 1 else now
    }

    private fun pendingIds(key: String): MutableSet<String> {
        return sharedPreferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
    }

    private fun markPending(key: String, id: String) {
        val ids = pendingIds(key)
        if (ids.add(id)) savePendingIds(key, ids)
    }

    private fun clearPending(key: String, id: String) {
        val ids = pendingIds(key)
        if (ids.remove(id)) savePendingIds(key, ids)
    }

    private fun savePendingIds(key: String, ids: Set<String>) {
        sharedPreferences.edit().putStringSet(key, ids).apply()
    }
}
