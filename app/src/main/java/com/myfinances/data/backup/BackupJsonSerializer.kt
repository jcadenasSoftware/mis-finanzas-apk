package com.jcadenas.xpendz.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializer para convertir BackupData a/desde JSON.
 *
 * Esta clase encapsula la lógica de serialización/deserialización de BackupData
 * usando kotlinx.serialization.
 *
 * Características:
 * - ignoreUnknownKeys = false (rechaza campos desconocidos)
 * - encodeDefaults = true (incluye valores por defecto)
 * - prettyPrint = false (JSON compacto, menor tamaño)
 * - Portable a Android y Desktop JVM
 * - Valida BackupData tras deserialización
 */
@Singleton
class BackupJsonSerializer @Inject constructor(
    private val validator: BackupSchemaValidator
) {
    companion object {
        /**
         * Configuración JSON.
         *
         * - ignoreUnknownKeys = false: Rechaza campos desconocidos en el JSON
         * - encodeDefaults = true: Incluye valores por defecto en la serialización
         * - prettyPrint = false: JSON compacto sin formato (menor tamaño)
         * - isLenient = false: Modo estricto de parsing
         */
        private val jsonConfig = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            prettyPrint = false
            isLenient = false
            coerceInputValues = false
        }
    }

    /**
     * Serializa un BackupData a una cadena JSON.
     *
     * @param backupData Datos de backup a serializar
     * @return Cadena JSON con los datos serializados
     * @throws BackupExportException si ocurre un error durante la serialización
     */
    fun serialize(backupData: BackupData): String {
        return try {
            jsonConfig.encodeToString(backupData)
        } catch (e: Exception) {
            throw BackupExportException("Error al serializar BackupData a JSON", e)
        }
    }

    /**
     * Deserializa una cadena JSON a BackupData.
     *
     * @param jsonString Cadena JSON con los datos serializados
     * @return BackupData deserializado y validado
     * @throws BackupImportException si ocurre un error durante la deserialización
     * @throws SchemaValidationException si el BackupData deserializado no es válido
     */
    fun deserialize(jsonString: String): BackupData {
        return try {
            val backupData = jsonConfig.decodeFromString<BackupData>(jsonString)
            // Validar BackupData tras deserialización
            validator.validate(backupData)
            backupData
        } catch (e: SchemaValidationException) {
            throw e
        } catch (e: Exception) {
            throw BackupImportException("Error al deserializar JSON a BackupData", e)
        }
    }
}
