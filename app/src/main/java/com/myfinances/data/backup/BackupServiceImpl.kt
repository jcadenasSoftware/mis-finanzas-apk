package com.jcadenas.xpendz.data.backup

import android.util.Log
import com.jcadenas.xpendz.core.security.backup.BackupEncryptionException
import com.jcadenas.xpendz.core.security.backup.BackupEncryptionManager
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de BackupService que orquesta el flujo completo de backup.
 *
 * Responsabilidades:
 * - Coordinar DataExporter, BackupJsonSerializer y BackupEncryptionManager para exportación
 * - Coordinar BackupEncryptionManager, BackupJsonSerializer y DataImporter para importación
 * - Manejar conversiones entre tipos (String ↔ ByteArray, CharArray ↔ String)
 * - Asegurar limpieza defensiva de datos sensibles
 * - Cerrar streams apropiadamente
 *
 * Características:
 * - Constructor injection para testabilidad
 * - Portable a Android y Desktop JVM
 * - Limpieza defensiva de password (CharArray)
 * - Cierre automático de streams
 *
 * No contiene:
 * - Lógica Room (delega a DataExporter/DataImporter)
 * - Lógica criptográfica (delega a BackupEncryptionManager)
 * - Lógica de serialización (delega a BackupJsonSerializer)
 */
@Singleton
class BackupServiceImpl @Inject constructor(
    private val dataExporter: DataExporter,
    private val dataImporter: DataImporter,
    private val jsonSerializer: BackupJsonSerializer,
    private val encryptionManager: BackupEncryptionManager
) : BackupService {

    override suspend fun exportBackup(
        userUid: String,
        password: CharArray,
        outputStream: OutputStream
    ) {
        var passwordString: String? = null
        try {
            Log.d("Backup", "exportBackup: inicio para userUid=$userUid")

            // 1. Exportar datos desde Room
            val backupData = dataExporter.export(userUid)
            Log.d(
                "Backup",
                "exportBackup: dataExported user=${backupData.user.uid}, categories=${backupData.categories.size}, accounts=${backupData.accounts.size}, loans=${backupData.loans.size}, transactions=${backupData.transactions.size}, transfers=${backupData.transfers.size}, budgets=${backupData.budgets.size}, goals=${backupData.goals.size}, loanPayments=${backupData.loanPayments.size}, loanMovements=${backupData.loanMovements.size}, exchangeRates=${backupData.exchangeRates.size}"
            )

            // 2. Serializar BackupData a JSON
            val jsonString = jsonSerializer.serialize(backupData)
            Log.d("Backup", "exportBackup: jsonLength=${jsonString.length}")

            // 3. Convertir JSON a ByteArray UTF-8
            val jsonData = jsonString.toByteArray(StandardCharsets.UTF_8)
            Log.d("Backup", "exportBackup: jsonBytes=${jsonData.size}")

            // 4. Convertir CharArray a String (necesario para BackupEncryptionManager)
            // NOTA: Esto es una limitación de seguridad documentada en la auditoría
            passwordString = String(password)

            // 5. Cifrar datos
            val encryptedData = encryptionManager.encryptBackup(jsonData, passwordString)

            // Log para depuración
            Log.d("Backup", "exportBackup: encryptedBytes=${encryptedData.size}")

            // 6. Escribir datos cifrados al OutputStream
            outputStream.use { stream ->
                stream.write(encryptedData)
                stream.flush()
            }

            Log.d("Backup", "exportBackup: escritos ${encryptedData.size} bytes al archivo")
        } catch (e: BackupExportException) {
            Log.e("Backup", "exportBackup: BackupExportException", e)
            throw e
        } catch (e: BackupEncryptionException) {
            Log.e("Backup", "exportBackup: BackupEncryptionException", e)
            throw BackupExportException("Error al cifrar el backup", e)
        } catch (e: Exception) {
            Log.e("Backup", "exportBackup: excepción inesperada", e)
            throw BackupExportException("Error durante la exportación del backup", e)
        } finally {
            // Limpieza defensiva del password String temporal (no efectivo, mejor esfuerzo)
            passwordString = null
            // Limpieza defensiva del CharArray original
            Arrays.fill(password, '\u0000')
        }
    }

    override suspend fun importBackup(
        userUid: String,
        password: CharArray,
        inputStream: InputStream
    ) {
        var passwordString: String? = null
        try {
            Log.d("Backup", "importBackup: inicio para userUid=$userUid")

            // 1. Leer datos cifrados del InputStream
            val encryptedData = inputStream.use { stream ->
                stream.readBytes()
            }

            // 2. Validación defensiva: archivo vacío
            if (encryptedData.isEmpty()) {
                throw BackupImportException("Archivo de backup vacío (0 bytes). Verifica que hayas seleccionado el archivo correcto.")
            }

            // Log para depuración
            Log.d("Backup", "importBackup: encryptedBytes=${encryptedData.size}")

            // 3. Convertir CharArray a String (necesario para BackupEncryptionManager)
            // NOTA: Esto es una limitación de seguridad documentada en la auditoría
            passwordString = String(password)

            // 4. Descifrar datos
            val jsonData = encryptionManager.decryptBackup(encryptedData, passwordString)

            // 5. Validación defensiva: datos vacíos tras descifrado
            if (jsonData.isEmpty()) {
                throw BackupImportException("Backup descifrado vacío")
            }

            // 6. Convertir ByteArray a String UTF-8
            val jsonString = String(jsonData, StandardCharsets.UTF_8)

            // 7. Deserializar JSON a BackupData
            val backupData = jsonSerializer.deserialize(jsonString)
            Log.d(
                "Backup",
                "importBackup: dataImported user=${backupData.user.uid}, categories=${backupData.categories.size}, accounts=${backupData.accounts.size}, loans=${backupData.loans.size}, transactions=${backupData.transactions.size}, transfers=${backupData.transfers.size}, budgets=${backupData.budgets.size}, goals=${backupData.goals.size}, loanPayments=${backupData.loanPayments.size}, loanMovements=${backupData.loanMovements.size}, exchangeRates=${backupData.exchangeRates.size}"
            )

            // 8. Restaurar datos en Room
            dataImporter.restore(userUid, backupData)
        } catch (e: BackupImportException) {
            Log.e("Backup", "importBackup: BackupImportException", e)
            throw e
        } catch (e: BackupEncryptionException) {
            Log.e("Backup", "importBackup: BackupEncryptionException", e)
            throw BackupImportException("Error al descifrar el backup", e)
        } catch (e: Exception) {
            Log.e("Backup", "importBackup: excepción inesperada", e)
            throw BackupImportException("Error durante la importación del backup", e)
        } finally {
            // Limpieza defensiva del password String temporal (no efectivo, mejor esfuerzo)
            passwordString = null
            // Limpieza defensiva del CharArray original
            Arrays.fill(password, '\u0000')
        }
    }
}
