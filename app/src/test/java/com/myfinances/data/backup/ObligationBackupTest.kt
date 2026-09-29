package com.jcadenas.xpendz.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jcadenas.xpendz.core.security.backup.BackupEncryptionManager
import com.jcadenas.xpendz.core.security.backup.BackupFileCodec
import com.jcadenas.xpendz.core.security.backup.PasswordKeyDerivationService
import com.jcadenas.xpendz.data.local.AppDatabase
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import com.jcadenas.xpendz.data.local.entity.CategoryEntity
import com.jcadenas.xpendz.data.local.entity.ObligationEntity
import com.jcadenas.xpendz.data.local.entity.ObligationSettlementEntity
import com.jcadenas.xpendz.data.local.entity.TransactionEntity
import com.jcadenas.xpendz.data.local.entity.UserEntity
import com.jcadenas.xpendz.data.local.entity.UserSettingsEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fase 2D — Backup/Restore para el módulo de obligaciones.
 *
 * Regla estricta verificada aquí: la restauración preserva la cadena
 * Obligation → ObligationSettlement → Transaction completa y rechaza la
 * restauración ante cualquier inconsistencia (settlement sin Transaction
 * enlazada, sin obligación padre, sin cuenta, o linkedTransactionId duplicado).
 * Jamás se fabrica una Transaction para "reparar" un settlement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObligationBackupTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var databaseName: String
    private val validator = BackupSchemaValidator()
    private val serializer = BackupJsonSerializer(validator)
    private lateinit var exporter: RoomDataExporter
    private lateinit var importer: RoomDataImporter
    private lateinit var backupService: BackupServiceImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "bk${UUID.randomUUID().toString().take(8)}.db"
        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        ).allowMainThreadQueries().build()

        exporter = RoomDataExporter(
            userDao = database.userDao(),
            userSettingsDao = database.userSettingsDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
            loanDao = database.loanDao(),
            transactionDao = database.transactionDao(),
            transferDao = database.transferDao(),
            budgetDao = database.budgetDao(),
            goalDao = database.goalDao(),
            obligationDao = database.obligationDao(),
            obligationSettlementDao = database.obligationSettlementDao(),
            loanPaymentDao = database.loanPaymentDao(),
            loanMovementDao = database.loanMovementDao(),
            exchangeRateDao = database.exchangeRateDao(),
            validator = validator
        )
        importer = RoomDataImporter(
            database = database,
            userDao = database.userDao(),
            userSettingsDao = database.userSettingsDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
            loanDao = database.loanDao(),
            transactionDao = database.transactionDao(),
            transferDao = database.transferDao(),
            budgetDao = database.budgetDao(),
            goalDao = database.goalDao(),
            obligationDao = database.obligationDao(),
            obligationSettlementDao = database.obligationSettlementDao(),
            loanPaymentDao = database.loanPaymentDao(),
            loanMovementDao = database.loanMovementDao(),
            exchangeRateDao = database.exchangeRateDao(),
            validator = validator
        )
        backupService = BackupServiceImpl(
            dataExporter = exporter,
            dataImporter = importer,
            jsonSerializer = serializer,
            encryptionManager = BackupEncryptionManager(
                PasswordKeyDerivationService(),
                BackupFileCodec()
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun exportIncludesObligationSettlementAndLinkedTransaction() = runBlocking {
        seedDatabase()

        val data = exporter.export(UID)

        assertEquals(1, data.obligations.size)
        assertEquals(OBLIGATION_ID, data.obligations[0].id)
        assertEquals(1, data.obligationSettlements.size)
        assertEquals(TX_ID, data.obligationSettlements[0].linkedTransactionId)
        assertTrue(data.transactions.any { it.id == TX_ID })
    }

    @Test
    fun restorePreservesExactIdsAndLinksOfTheChain() = runBlocking {
        importer.restore(UID, validBackupWithChain())

        val obligation = database.obligationDao().getById(OBLIGATION_ID)
        val settlement = database.obligationSettlementDao().getById(SETTLEMENT_ID)
        val transaction = database.transactionDao().getByUser(UID).firstOrNull { it.id == TX_ID }

        assertNotNull(obligation)
        assertEquals(OBLIGATION_ID, obligation?.id)
        assertNotNull(settlement)
        assertEquals(OBLIGATION_ID, settlement?.obligationId)
        assertEquals(TX_ID, settlement?.linkedTransactionId)
        assertNotNull(transaction)
    }

    @Test
    fun restoreRejectsSettlementWhoseLinkedTransactionIsNotInBackup() = runBlocking {
        seedDatabase()

        val corruptBackup = validBackupWithChain().copy(
            transactions = emptyList()
        )

        assertThrows(BackupImportException::class.java) {
            runBlocking { importer.restore(UID, corruptBackup) }
        }

        // La restauración se rechazó antes de borrar: los datos previos quedan intactos.
        assertNotNull(database.obligationDao().getById(OBLIGATION_ID))
        assertNotNull(database.obligationSettlementDao().getById(SETTLEMENT_ID))
        assertTrue(database.transactionDao().getByUser(UID).any { it.id == TX_ID })
        // Y no se materializó ninguna entidad del backup corrupto.
        assertEquals(1, database.obligationDao().getByUser(UID).size)
    }

    @Test
    fun restoreRejectsSettlementWithMissingObligation() = runBlocking {
        val corruptBackup = validBackupWithChain().copy(
            obligations = emptyList()
        )

        assertThrows(BackupImportException::class.java) {
            runBlocking { importer.restore(UID, corruptBackup) }
        }
        assertNull(database.obligationSettlementDao().getById(SETTLEMENT_ID))
    }

    @Test
    fun restoreRejectsSettlementWithMissingAccount() = runBlocking {
        val corruptBackup = validBackupWithChain().copy(
            accounts = emptyList()
        )

        assertThrows(BackupImportException::class.java) {
            runBlocking { importer.restore(UID, corruptBackup) }
        }
        assertNull(database.obligationSettlementDao().getById(SETTLEMENT_ID))
    }

    @Test
    fun restoreRejectsTwoSettlementsClaimingSameTransaction() = runBlocking {
        val duplicate = settlement(
            id = "set-dup",
            obligationId = OBLIGATION_ID,
            linkedTransactionId = TX_ID
        )
        val corruptBackup = validBackupWithChain().copy(
            obligationSettlements = validBackupWithChain().obligationSettlements + duplicate
        )

        assertThrows(BackupImportException::class.java) {
            runBlocking { importer.restore(UID, corruptBackup) }
        }
        assertEquals(0, database.obligationSettlementDao().getByUser(UID).size)
    }

    @Test
    fun restoreRejectsObligationWithInvalidCategoryReference() = runBlocking {
        val corruptBackup = validBackupWithChain().copy(
            obligations = listOf(obligation().copy(obligationCategoryId = "cat-ghost"))
        )

        assertThrows(BackupImportException::class.java) {
            runBlocking { importer.restore(UID, corruptBackup) }
        }
        assertNull(database.obligationDao().getById(OBLIGATION_ID))
    }

    @Test
    fun legacyBackupWithoutObligationFieldsRestoresSuccessfully() = runBlocking {
        // JSON equivalente a un backup v1 previo al módulo: sin las claves
        // "obligations" ni "obligationSettlements". Deben aplicar los defaults.
        val legacyJson = """
            {"metadata":{"schemaVersion":1,"exportedAtEpochSec":1700000000,"userUid":"$UID","appVersion":null},
            "user":{"uid":"$UID","email":"user@test.dev","createdAtEpochSec":1700000000,"updatedAtEpochSec":1700000000},
            "userSettings":{"userUid":"$UID","countryCode":"CO","baseCurrency":"COP","updatedAtEpochSec":1700000000,"updatedBy":null}}
        """.trimIndent()

        val data = serializer.deserialize(legacyJson)
        assertTrue(data.obligations.isEmpty())
        assertTrue(data.obligationSettlements.isEmpty())

        importer.restore(UID, data)

        assertNotNull(database.userDao().getByUid(UID))
        assertEquals(0, database.obligationDao().getByUser(UID).size)
    }

    @Test
    fun restoreReplacesExistingObligationData() = runBlocking {
        seedDatabase()
        val incoming = obligation().copy(id = "obl-incoming", title = "Nueva")
        val data = validBackupWithChain().copy(
            obligations = listOf(incoming),
            obligationSettlements = validBackupWithChain().obligationSettlements
                .map { it.copy(obligationId = "obl-incoming") }
        )

        importer.restore(UID, data)

        assertNull(database.obligationDao().getById(OBLIGATION_ID))
        assertNotNull(database.obligationDao().getById("obl-incoming"))
    }

    @Test
    fun encryptedRoundTripPreservesObligationSettlementTransactionChain() = runBlocking {
        seedDatabase()

        val output = ByteArrayOutputStream()
        backupService.exportBackup(UID, "test-password".toCharArray(), output)

        // Borrar el módulo para probar que el restore lo reconstruye desde el archivo.
        database.obligationSettlementDao().deleteAllByUser(UID)
        database.obligationDao().deleteAllByUser(UID)
        assertEquals(0, database.obligationDao().getByUser(UID).size)

        backupService.importBackup(UID, "test-password".toCharArray(), ByteArrayInputStream(output.toByteArray()))

        val obligation = database.obligationDao().getById(OBLIGATION_ID)
        val settlement = database.obligationSettlementDao().getById(SETTLEMENT_ID)
        assertNotNull(obligation)
        assertNotNull(settlement)
        assertEquals(TX_ID, settlement?.linkedTransactionId)
        assertEquals(SETTLEMENT_ID, settlement?.id)
    }

    private suspend fun seedDatabase() {
        database.userDao().upsert(user())
        database.userSettingsDao().upsert(userSettings())
        database.categoryDao().insert(category())
        database.accountDao().insert(account())
        database.obligationDao().insert(obligation())
        database.transactionDao().insert(transaction())
        database.obligationSettlementDao().insert(settlement())
    }

    private fun validBackupWithChain() = BackupData(
        metadata = BackupMetadata(
            schemaVersion = BackupMetadata.CURRENT_BACKUP_SCHEMA_VERSION,
            exportedAtEpochSec = NOW,
            userUid = UID,
            appVersion = null
        ),
        user = user(),
        userSettings = userSettings(),
        categories = listOf(category()),
        accounts = listOf(account()),
        transactions = listOf(transaction()),
        obligations = listOf(obligation()),
        obligationSettlements = listOf(settlement())
    )

    private fun user() = UserEntity(UID, "user@test.dev", NOW, NOW)

    private fun userSettings() = UserSettingsEntity(
        userUid = UID,
        countryCode = "CO",
        baseCurrency = "COP",
        updatedAtEpochSec = NOW,
        updatedBy = null
    )

    private fun category() = CategoryEntity(
        id = CATEGORY_ID,
        userUid = UID,
        name = "Clientes",
        kind = "BOTH",
        parentId = null,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun account() = AccountEntity(
        id = ACCOUNT_ID,
        userUid = UID,
        name = "Banco",
        type = "BANK",
        currency = "COP",
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun transaction() = TransactionEntity(
        id = TX_ID,
        userUid = UID,
        accountId = ACCOUNT_ID,
        categoryId = CATEGORY_ID,
        kind = "INCOME",
        amountCents = 200_000L,
        occurredAtEpochSec = NOW,
        note = "Abono registrado",
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun obligation() = ObligationEntity(
        id = OBLIGATION_ID,
        userUid = UID,
        type = ObligationEntity.TYPE_RECEIVABLE,
        title = "Factura pendiente",
        counterpartyName = "Pedro",
        notes = null,
        reference = "FAC-001",
        obligationCategoryId = CATEGORY_ID,
        currency = "COP",
        originalAmountCents = 500_000L,
        issuedAtEpochSec = NOW,
        dueAtEpochSec = NOW + 86_400L,
        cancelledAtEpochSec = null,
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    private fun settlement(
        id: String = SETTLEMENT_ID,
        obligationId: String = OBLIGATION_ID,
        linkedTransactionId: String = TX_ID
    ) = ObligationSettlementEntity(
        id = id,
        userUid = UID,
        obligationId = obligationId,
        accountId = ACCOUNT_ID,
        amountCents = 200_000L,
        occurredAtEpochSec = NOW + 1L,
        linkedTransactionId = linkedTransactionId,
        note = "Abono 1",
        createdAtEpochSec = NOW,
        updatedAtEpochSec = NOW,
        updatedBy = "test-device"
    )

    companion object {
        private const val UID = "backup-user"
        private const val ACCOUNT_ID = "account-1"
        private const val CATEGORY_ID = "category-1"
        private const val OBLIGATION_ID = "obl-1"
        private const val SETTLEMENT_ID = "set-1"
        private const val TX_ID = "tx-1"
        private const val NOW = 1_700_000_000L
    }
}
