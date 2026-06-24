package com.jcadenas.xpendz.data.backup

/**
 * Excepción lanzada cuando ocurre un error durante la exportación de datos.
 *
 * Esta excepción encapsula errores que pueden ocurrir al exportar datos desde Room
 * hacia BackupData, incluyendo:
 * - Usuario no encontrado
 * - Errores de consulta a la base de datos
 * - Errores de construcción de BackupData
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
class BackupExportException(message: String, cause: Throwable? = null) : Exception(message, cause)
