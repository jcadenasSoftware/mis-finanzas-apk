package com.jcadenas.xpendz.data.backup

import kotlinx.serialization.Serializable

/**
 * Metadatos del archivo de backup.
 *
 * Contiene información sobre la versión del esquema, usuario, timestamp de exportación
 * y versión de la aplicación. Estos metadatos son críticos para validar compatibilidad
 * y prevenir restauración de backups corruptos o de usuarios incorrectos.
 *
 * @property schemaVersion Versión del esquema de backup (actualmente 1)
 * @property exportedAtEpochSec Timestamp Unix en segundos de cuándo se exportó el backup
 * @property userUid UID del usuario de Firebase que creó el backup
 * @property appVersion Versión de la aplicación que creó el backup (opcional)
 */
@Serializable
data class BackupMetadata(
    val schemaVersion: Int,
    val exportedAtEpochSec: Long,
    val userUid: String,
    val appVersion: String?
) {
    companion object {
        /**
         * Versión actual del esquema de backup.
         *
         * Esta versión debe incrementarse cuando se realicen cambios incompatibles
         * en la estructura de BackupData. Permite migraciones futuras y validación
         * de compatibilidad entre versiones.
         */
        const val CURRENT_BACKUP_SCHEMA_VERSION = 1
    }
}
