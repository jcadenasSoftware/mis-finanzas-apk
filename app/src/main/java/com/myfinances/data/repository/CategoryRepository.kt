package com.myfinances.data.repository

import android.util.Log
import android.database.sqlite.SQLiteConstraintException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myfinances.data.local.dao.CategoryDao
import com.myfinances.data.local.entity.CategoryEntity
import com.myfinances.sync.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider
) {
    suspend fun ensureSystemLoanCategories(userUid: String): Pair<String, String> {
        val loanCategoryId = "system-loan-$userUid"
        val repaymentCategoryId = "system-loan-repayment-$userUid"

        ensureSystemCategory(userUid, loanCategoryId, "Préstamos")
        ensureSystemCategory(userUid, repaymentCategoryId, "Devoluciones")

        return loanCategoryId to repaymentCategoryId
    }

    private suspend fun ensureSystemCategory(userUid: String, id: String, name: String): CategoryEntity {
        val existing = categoryDao.getById(id)
        if (existing != null) {
            return existing
        }
        val now = System.currentTimeMillis() / 1000
        val category = CategoryEntity(
            id = id,
            userUid = userUid,
            name = name,
            kind = "BOTH",
            iconKey = null,
            parentId = null,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        categoryDao.insert(category)
        syncToFirestore(userUid, category)
        return category
    }

    fun observeCategories(userUid: String): Flow<List<CategoryEntity>> {
        return categoryDao.observeByUser(userUid)
    }

    fun observeRoots(userUid: String): Flow<List<CategoryEntity>> {
        return categoryDao.observeRoots(userUid)
    }

    fun observeChildren(userUid: String, parentId: String): Flow<List<CategoryEntity>> {
        return categoryDao.observeChildren(userUid, parentId)
    }

    suspend fun getCategories(userUid: String): List<CategoryEntity> {
        return categoryDao.getByUser(userUid)
    }

    suspend fun getRoots(userUid: String): List<CategoryEntity> {
        return categoryDao.getRoots(userUid)
    }

    suspend fun getChildren(userUid: String, parentId: String): List<CategoryEntity> {
        return categoryDao.getChildren(userUid, parentId)
    }

    suspend fun getById(id: String): CategoryEntity? {
        return categoryDao.getById(id)
    }

    suspend fun create(
        userUid: String,
        name: String,
        kind: String = "BOTH",
        iconKey: String? = null,
        parentId: String? = null
    ): CategoryEntity {
        val now = System.currentTimeMillis() / 1000
        val category = CategoryEntity(
            id = UUID.randomUUID().toString(),
            userUid = userUid,
            name = name,
            kind = kind,
            iconKey = iconKey,
            parentId = parentId,
            createdAtEpochSec = now,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        categoryDao.insert(category)
        syncToFirestore(userUid, category)
        return category
    }

    suspend fun updateCategory(
        userUid: String,
        categoryId: String,
        newName: String,
        iconKey: String? = null
    ): CategoryEntity? {
        val category = categoryDao.getById(categoryId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val updated = category.copy(
            name = newName,
            iconKey = iconKey,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        categoryDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }

    suspend fun rename(userUid: String, categoryId: String, newName: String): CategoryEntity? {
        val category = categoryDao.getById(categoryId) ?: return null
        return updateCategory(
            userUid = userUid,
            categoryId = categoryId,
            newName = newName,
            iconKey = category.iconKey
        )
    }

    suspend fun delete(categoryId: String, userUid: String) {
        categoryDao.delete(categoryId)
        deleteFromFirestore(userUid, categoryId)
    }

    suspend fun deleteAllByUser(userUid: String) {
        categoryDao.deleteAllByUser(userUid)
        deleteAllFromFirestore(userUid)
    }

    private suspend fun deleteAllFromFirestore(userUid: String) {
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userUid)
                .collection("categories")
            val snapshot = collectionRef.get().await()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("CategoryRepository", "Error deleting all from Firestore", e)
        }
    }

    suspend fun syncFromFirestore(userUid: String) {
        try {
            Log.d("CategoryRepository", "Syncing categories from Firestore for user: $userUid")
            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("categories")
                .get()
                .await()

            Log.d("CategoryRepository", "Snapshot size: ${snapshot.size()}")

            val remoteIds = snapshot.documents.map { it.id }.toSet()

            val categories = snapshot.documents.mapNotNull { doc ->
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

                    val name = (data["name"] as? String)?.trim().orEmpty()
                    if (name.isBlank()) return@mapNotNull null

                    val parentId = (data["parentId"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (data["parent_id"] as? String)?.takeIf { it.isNotBlank() }

                    val iconKey = (data["iconKey"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
                        ?: (data["icon_key"] as? String)?.trim().takeUnless { it.isNullOrBlank() }

                    val kind = (data["kind"] as? String)?.trim()?.uppercase()
                        ?.takeIf { it == "INCOME" || it == "EXPENSE" || it == "BOTH" }
                        ?: "BOTH"

                    val updatedBy = (data["updatedBy"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
                        ?: (data["updated_by"] as? String)?.trim().takeUnless { it.isNullOrBlank() }

                    val createdAt = anyLong("createdAtEpochSec", "created_at_epoch_sec") ?: (System.currentTimeMillis() / 1000)
                    val updatedAt = anyLong("updatedAtEpochSec", "updated_at_epoch_sec") ?: createdAt

                    CategoryEntity(
                        id = doc.id,
                        userUid = userUid,
                        name = name,
                        kind = kind,
                        iconKey = iconKey,
                        parentId = parentId,
                        createdAtEpochSec = createdAt,
                        updatedAtEpochSec = updatedAt,
                        updatedBy = updatedBy
                    )
                } catch (e: Exception) {
                    Log.e("CategoryRepository", "Error parsing category doc ${doc.id}", e)
                    null
                }
            }

            Log.d("CategoryRepository", "Parsed ${categories.size} valid categories")
            // Even if categories is empty we still need to delete missing locals.

            val roots = categories.filter { it.parentId.isNullOrBlank() }
            val children = categories.filterNot { it.parentId.isNullOrBlank() }

            var inserted = 0
            var skipped = 0

            // Upsert roots first (avoid REPLACE semantics that delete referenced rows)
            for (c in roots) {
                try {
                    val existing = categoryDao.getById(c.id)
                    if (existing == null) {
                        categoryDao.insert(c)
                    } else {
                        categoryDao.update(c)
                    }
                    inserted++
                } catch (e: SQLiteConstraintException) {
                    skipped++
                    Log.e("CategoryRepository", "FK error upserting root category ${c.id}", e)
                }
            }

            // Insert children after roots. If parent not present, skip (will cause FK error).
            // We do a few passes to allow multi-level trees.
            val remaining = children.toMutableList()
            repeat(5) {
                if (remaining.isEmpty()) return@repeat
                val it = remaining.iterator()
                while (it.hasNext()) {
                    val c = it.next()
                    val parentId = c.parentId
                    if (parentId.isNullOrBlank()) {
                        it.remove()
                        continue
                    }
                    val parentExists = categoryDao.getById(parentId) != null
                    if (!parentExists) {
                        continue
                    }
                    try {
                        val existing = categoryDao.getById(c.id)
                        if (existing == null) {
                            categoryDao.insert(c)
                        } else {
                            categoryDao.update(c)
                        }
                        inserted++
                        it.remove()
                    } catch (e: SQLiteConstraintException) {
                        skipped++
                        Log.e("CategoryRepository", "FK error upserting category ${c.id} parent=${c.parentId}", e)
                        it.remove()
                    }
                }
            }

            // Anything still remaining cannot be inserted safely
            for (c in remaining) {
                skipped++
                Log.w("CategoryRepository", "Skipping category ${c.id} because parent not found: ${c.parentId}")
            }

            Log.d("CategoryRepository", "Categories inserted=$inserted skipped=$skipped")

            // Delete local categories that no longer exist in Firestore.
            // Delete children first to avoid FK issues.
            val localAll = categoryDao.getByUser(userUid)
            val toDeleteChildren = localAll.filter { !it.parentId.isNullOrBlank() && !remoteIds.contains(it.id) }
            val toDeleteRoots = localAll.filter { it.parentId.isNullOrBlank() && !remoteIds.contains(it.id) }

            var deleted = 0
            var deleteSkipped = 0

            for (c in toDeleteChildren) {
                try {
                    categoryDao.delete(c.id)
                    deleted++
                } catch (e: SQLiteConstraintException) {
                    deleteSkipped++
                    Log.w("CategoryRepository", "FK error deleting category ${c.id}", e)
                }
            }
            for (c in toDeleteRoots) {
                try {
                    categoryDao.delete(c.id)
                    deleted++
                } catch (e: SQLiteConstraintException) {
                    deleteSkipped++
                    Log.w("CategoryRepository", "FK error deleting category ${c.id}", e)
                }
            }

            Log.d("CategoryRepository", "Categories deleted=$deleted skipped=$deleteSkipped")
        } catch (e: Exception) {
            Log.e("CategoryRepository", "Error syncing categories from Firestore", e)
            throw e
        }
    }

    private suspend fun syncToFirestore(userUid: String, category: CategoryEntity) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("categories")
                .document(category.id)
                .set(category, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }

    private suspend fun deleteFromFirestore(userUid: String, categoryId: String) {
        try {
            firestore.collection("users")
                .document(userUid)
                .collection("categories")
                .document(categoryId)
                .delete()
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }
}
