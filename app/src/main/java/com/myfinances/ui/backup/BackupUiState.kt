package com.jcadenas.xpendz.ui.backup

/**
 * Estado inmutable de la UI de backups.
 *
 * Representa el estado actual del flujo de backup en la capa de presentación.
 * Este estado es observado por la UI Compose para actualizar la interfaz.
 *
 * Estados:
 * - Idle: Sin operación en progreso
 * - Exporting: Exportación en progreso
 * - Importing: Importación en progreso
 * - Success: Operación completada exitosamente (con mensaje)
 * - Error: Operación falló (con mensaje amigable)
 */
sealed class BackupUiState {
    /**
     * Estado inicial sin operación en progreso.
     */
    data object Idle : BackupUiState()

    /**
     * Exportación en progreso.
     */
    data object Exporting : BackupUiState()

    /**
     * Importación en progreso.
     */
    data object Importing : BackupUiState()

    /**
     * Operación completada exitosamente.
     *
     * @param message Mensaje amigable para mostrar al usuario
     */
    data class Success(val message: String) : BackupUiState()

    /**
     * Operación falló.
     *
     * @param message Mensaje amigable de error (no expone excepciones técnicas)
     */
    data class Error(val message: String) : BackupUiState()
}
