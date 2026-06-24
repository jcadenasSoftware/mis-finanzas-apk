package com.jcadenas.xpendz.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Excepción lanzada cuando ocurre un error al acceder a archivos mediante SAF.
 *
 * Esta excepción encapsula errores de ContentResolver y proporciona
 * mensajes amigables para la UI.
 */
class BackupFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Administrador de archivos de backup usando Android Storage Access Framework (SAF).
 *
 * Responsabilidades:
 * - Encapsular el acceso a ContentResolver
 * - Abrir OutputStream desde Uri para escritura
 * - Abrir InputStream desde Uri para lectura
 * - Manejar errores de acceso a archivos
 *
 * Características:
 * - Singleton para inyección por Hilt
 * - Lanza BackupFileException controlada
 * - No contiene lógica de backup (solo acceso a archivos)
 *
 * Arquitectura:
 * - BackupFileManager está aislado de BackupService
 * - BackupService no tiene dependencias de Android (portable)
 * - SAF queda encapsulado en esta capa de Android
 */
@Singleton
class BackupFileManager @Inject constructor(
    private val contentResolver: ContentResolver
) {

    /**
     * Abre un OutputStream para escribir en el archivo especificado por la Uri.
     *
     * @param uri Uri del archivo donde escribir (obtenida de SAF)
     * @return OutputStream abierto
     * @throws BackupFileException si no se puede abrir el archivo
     */
    fun openOutputStream(uri: Uri): OutputStream {
        return try {
            Log.d("Backup", "Abriendo OutputStream para Uri: $uri")
            contentResolver.openOutputStream(uri)
                ?: throw BackupFileException("No se pudo abrir el archivo para escritura")
        } catch (e: FileNotFoundException) {
            throw BackupFileException("Archivo no encontrado", e)
        } catch (e: SecurityException) {
            throw BackupFileException("No tiene permiso para acceder al archivo", e)
        } catch (e: Exception) {
            throw BackupFileException("Error al abrir el archivo para escritura", e)
        }
    }

    /**
     * Abre un InputStream para leer del archivo especificado por la Uri.
     *
     * @param uri Uri del archivo a leer (obtenida de SAF)
     * @return InputStream abierto
     * @throws BackupFileException si no se puede abrir el archivo
     */
    fun openInputStream(uri: Uri): InputStream {
        return try {
            Log.d("Backup", "Abriendo InputStream para Uri: $uri")
            contentResolver.openInputStream(uri)
                ?: throw BackupFileException("No se pudo abrir el archivo para lectura")
        } catch (e: FileNotFoundException) {
            throw BackupFileException("Archivo no encontrado", e)
        } catch (e: SecurityException) {
            throw BackupFileException("No tiene permiso para acceder al archivo", e)
        } catch (e: Exception) {
            throw BackupFileException("Error al abrir el archivo para lectura", e)
        }
    }
}
