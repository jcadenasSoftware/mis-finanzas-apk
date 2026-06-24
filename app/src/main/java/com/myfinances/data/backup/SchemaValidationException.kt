package com.jcadenas.xpendz.data.backup

/**
 * Excepción lanzada cuando ocurre un error de validación del esquema de backup.
 *
 * Esta excepción encapsula errores de validación de BackupData, incluyendo:
 * - schemaVersion no soportado
 * - userUid no coincide entre metadata y user
 * - Referencias internas inválidas (parentId, accountId, linkedTransactionId)
 * - Listas nulas (deben ser listas vacías si están vacías)
 * - Inconsistencias estructurales en los datos
 *
 * Características:
 * - No expone stack traces en logging (política de hardening)
 * - Mensajes genéricos sin detalles técnicos sensibles
 * - Portable a Android y Desktop JVM
 * - Independiente de Room (valida solo la estructura lógica)
 *
 * @property message Mensaje descriptivo del error (genérico, sin detalles sensibles)
 * @property cause Causa original del error (opcional, no debe loguearse directamente)
 */
class SchemaValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
