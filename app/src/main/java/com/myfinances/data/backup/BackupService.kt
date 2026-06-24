package com.jcadenas.xpendz.data.backup

import java.io.InputStream
import java.io.OutputStream

/**
 * Servicio de orquestación para exportar e importar backups cifrados.
 *
 * Esta interfaz define la fachada única para el flujo completo de backup:
 * - Exportación: Room → BackupData → JSON → UTF-8 → Cifrado → Archivo
 * - Importación: Archivo → Bytes cifrados → Descifrado → JSON → BackupData → Room
 *
 * Características:
 * - Portable a Android y Desktop JVM (usa InputStream/OutputStream estándar)
 * - Usa CharArray para password (seguridad, permite limpieza de memoria)
 * - No contiene lógica Room (delega a DataExporter/DataImporter)
 * - No contiene lógica criptográfica (delega a BackupEncryptionManager)
 * - No contiene lógica de serialización (delega a BackupJsonSerializer)
 *
 * Seguridad:
 * - password se pasa como CharArray para permitir limpieza defensiva
 * - La implementación debe limpiar el CharArray en finally
 * - No debe exponer JSON en logs
 * - No debe exponer contraseñas
 */
interface BackupService {

    /**
     * Exporta los datos de un usuario a un backup cifrado.
     *
     * Flujo:
     * 1. Exportar datos desde Room usando DataExporter
     * 2. Serializar BackupData a JSON usando BackupJsonSerializer
     * 3. Convertir JSON a ByteArray UTF-8
     * 4. Cifrar ByteArray usando BackupEncryptionManager
     * 5. Escribir datos cifrados al OutputStream
     *
     * @param userUid UID del usuario a exportar
     * @param password Contraseña para cifrar el backup (CharArray para seguridad)
     * @param outputStream Stream donde escribir el backup cifrado
     * @throws BackupExportException si ocurre un error durante la exportación
     */
    suspend fun exportBackup(
        userUid: String,
        password: CharArray,
        outputStream: OutputStream
    )

    /**
     * Importa un backup cifrado a la base de datos.
     *
     * Flujo:
     * 1. Leer datos cifrados del InputStream
     * 2. Descifrar datos usando BackupEncryptionManager
     * 3. Convertir ByteArray a String UTF-8
     * 4. Deserializar JSON a BackupData usando BackupJsonSerializer
     * 5. Restaurar datos en Room usando DataImporter
     *
     * @param password Contraseña para descifrar el backup (CharArray para seguridad)
     * @param inputStream Stream desde donde leer el backup cifrado
     * @throws BackupImportException si ocurre un error durante la importación
     */
    suspend fun importBackup(
        userUid: String,
        password: CharArray,
        inputStream: InputStream
    )
}
