package com.jcadenas.xpendz.data.backup

/**
 * Excepción lanzada cuando ocurre un error durante la importación de datos.
 *
 * Esta excepción encapsula errores que pueden ocurrir al importar datos desde
 * BackupData hacia Room, incluyendo:
 * - Errores de inserción en la base de datos
 * - Violación de Foreign Keys
 * - Violación de Unique Constraints
 * - Errores de transacción
 * - Inconsistencias de datos
 *
 * Características:
 * - No expone stack traces en logging (política de hardening)
 * - Mensajes genéricos sin detalles técnicos sensibles
 * - Portable a Android y Desktop JVM
 *
 * @property message Mensaje descriptivo del error (genérico, sin detalles sensibles)
 * @property cause Causa original del error (opcional, no debe loguearse directamente)
 */
class BackupImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
