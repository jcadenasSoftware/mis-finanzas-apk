package com.myfinances.ui.backup

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfinances.data.backup.BackupExportException
import com.myfinances.data.backup.BackupImportException
import com.myfinances.data.backup.BackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * ViewModel para el flujo de backup en la capa de presentación.
 *
 * Responsabilidades:
 * - Orquestar exportación de backup usando BackupService y BackupFileManager
 * - Orquestar importación de backup usando BackupService y BackupFileManager
 * - Manejar estados de la UI (Idle, Exporting, Importing, Success, Error)
 * - Proporcionar mensajes amigables de error
 * - NO exponer excepciones técnicas, passwords o stack traces
 *
 * Flujo de exportación:
 * 1. Recibir Uri destino y password CharArray
 * 2. Actualizar estado a Exporting
 * 3. Abrir OutputStream mediante BackupFileManager
 * 4. Llamar BackupService.exportBackup(userUid, password, outputStream)
 * 5. Actualizar estado a Success o Error
 *
 * Flujo de importación:
 * 1. Recibir Uri origen y password CharArray
 * 2. Actualizar estado a Importing
 * 3. Abrir InputStream mediante BackupFileManager
 * 4. Llamar BackupService.importBackup(password, inputStream)
 * 5. Actualizar estado a Success o Error
 *
 * Características:
 * - Usa viewModelScope para coroutines
 * - Usa Dispatchers.IO para operaciones I/O
 * - StateFlow para estado reactivo
 * - Mensajes de error amigables (no técnicos)
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupService: BackupService,
    private val fileManager: BackupFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /**
     * Exporta los datos del usuario a un archivo de backup cifrado.
     *
     * @param userUid UID del usuario a exportar
     * @param password Contraseña para cifrar el backup (CharArray para seguridad)
     * @param uri Uri del archivo destino (obtenida de SAF)
     */
    fun exportBackup(userUid: String, password: CharArray, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Exporting
            Log.d("Backup", "Iniciando exportación para userUid=$userUid, uri=$uri")

            try {
                withContext(Dispatchers.IO) {
                    val outputStream: OutputStream = fileManager.openOutputStream(uri)
                    backupService.exportBackup(userUid, password, outputStream)
                }
                _uiState.value = BackupUiState.Success("Backup exportado exitosamente")
            } catch (e: BackupFileException) {
                _uiState.value = BackupUiState.Error("No se pudo acceder al archivo: ${e.message}")
            } catch (e: BackupExportException) {
                _uiState.value = BackupUiState.Error("Error al exportar el backup: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error("Ocurrió un error inesperado al exportar: ${e.message}")
            } finally {
                // Resetear estado a Idle después de un momento
                kotlinx.coroutines.delay(2000)
                _uiState.value = BackupUiState.Idle
            }
        }
    }

    /**
     * Importa un backup cifrado a la base de datos.
     *
     * @param userUid UID del usuario actual (para mapear entidades del backup)
     * @param password Contraseña para descifrar el backup (CharArray para seguridad)
     * @param uri Uri del archivo origen (obtenida de SAF)
     */
    fun importBackup(userUid: String, password: CharArray, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Importing
            Log.d("Backup", "Iniciando importación para userUid=$userUid, uri=$uri")

            try {
                withContext(Dispatchers.IO) {
                    val inputStream: InputStream = fileManager.openInputStream(uri)
                    backupService.importBackup(userUid, password, inputStream)
                }
                _uiState.value = BackupUiState.Success("Backup importado exitosamente")
            } catch (e: BackupFileException) {
                _uiState.value = BackupUiState.Error("No se pudo acceder al archivo: ${e.message}")
            } catch (e: BackupImportException) {
                _uiState.value = BackupUiState.Error("Error al importar el backup: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error("Ocurrió un error inesperado al importar: ${e.message}")
            } finally {
                // Resetear estado a Idle después de un momento
                kotlinx.coroutines.delay(2000)
                _uiState.value = BackupUiState.Idle
            }
        }
    }

    /**
     * Restablece el estado de la UI a Idle.
     */
    fun resetState() {
        _uiState.value = BackupUiState.Idle
    }
}
