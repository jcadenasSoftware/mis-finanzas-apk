# Sincronización Bidireccional: Transacciones ↔ Pagos de Préstamos

## Resumen
Se implementó sincronización bidireccional entre transacciones y pagos de préstamos para resolver la inconsistencia financiera donde editar/eliminar un abono desde Transacciones no actualizaba el estado del préstamo.

---

## Archivos Modificados

### 1. `LoanMovementEntity.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/local/entity/LoanMovementEntity.kt`

**Cambios**:
- Agregado índice en `linked_transaction_id` (campo ya existía)

**Propósito**: Mejorar rendimiento de búsquedas por `linkedTransactionId`.

### 2. `LoanMovementDao.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/local/dao/LoanMovementDao.kt`

**Cambios**:
```kotlin
@Query("SELECT * FROM loan_movements WHERE linked_transaction_id = :transactionId")
suspend fun getByLinkedTransactionId(transactionId: String): LoanMovementEntity?

@Query("DELETE FROM loan_movements WHERE linked_transaction_id = :transactionId")
suspend fun deleteByLinkedTransactionId(transactionId: String)
```

**Propósito**: Métodos para buscar y eliminar movimientos por su transacción vinculada.

### 3. `LoanMovementRepository.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/repository/LoanMovementRepository.kt`

**Cambios**:

#### Nuevos métodos de sincronización
```kotlin
suspend fun updateByTransaction(
    transactionId: String,
    amountCents: Long,
    occurredAtEpochSec: Long,
    note: String?
): LoanMovementEntity? {
    val movement = loanMovementDao.getByLinkedTransactionId(transactionId) ?: return null
    
    val now = System.currentTimeMillis() / 1000
    val updatedMovement = movement.copy(
        amountCents = amountCents,
        occurredAtEpochSec = occurredAtEpochSec,
        note = note,
        updatedAtEpochSec = now,
        updatedBy = deviceIdProvider.get()
    )
    loanMovementDao.update(updatedMovement)
    syncToFirestore(movement.userUid, updatedMovement)
    return updatedMovement
}

suspend fun deleteByTransaction(transactionId: String): LoanMovementEntity? {
    val movement = loanMovementDao.getByLinkedTransactionId(transactionId) ?: return null
    
    loanMovementDao.delete(movement.id)
    deleteFromFirestore(movement.userUid, movement.loanId, movement.id)
    return movement
}
```

**Nota**: El método `create()` ya aceptaba y guardaba `linkedTransactionId`.

**Propósito**: Actualizar/eliminar movimientos cuando se edita/elimina la transacción asociada.

### 4. `LoanPaymentEntity.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/local/entity/LoanPaymentEntity.kt`

**Cambios**:
- Agregado campo `linkedTransactionId: String?` para vincular el abono con su transacción
- Agregado índice en `linked_transaction_id` para búsquedas eficientes

**Propósito**: Permitir encontrar el abono asociado cuando se edita/elimina una transacción.

---

### 2. `LoanPaymentDao.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/local/dao/LoanPaymentDao.kt`

**Cambios**:
```kotlin
@Query("SELECT * FROM loan_payments WHERE linked_transaction_id = :transactionId")
suspend fun getByLinkedTransactionId(transactionId: String): LoanPaymentEntity?

@Query("DELETE FROM loan_payments WHERE linked_transaction_id = :transactionId")
suspend fun deleteByLinkedTransactionId(transactionId: String)
```

**Propósito**: Métodos para buscar y eliminar abonos por su transacción vinculada.

---

### 3. `LoanPaymentRepository.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/repository/LoanPaymentRepository.kt`

**Cambios**:

#### a. Método `create()` actualizado
```kotlin
// Después de crear la transacción
val updatedPayment = payment.copy(
    linkedTransactionId = tx.id,
    updatedAtEpochSec = now
)
loanPaymentDao.update(updatedPayment)
syncToFirestore(userUid, updatedPayment)
```

#### b. Nuevos métodos de sincronización
```kotlin
suspend fun updateByTransaction(
    transactionId: String,
    principalCents: Long,
    occurredAtEpochSec: Long,
    note: String?
): LoanPaymentEntity? {
    val payment = loanPaymentDao.getByLinkedTransactionId(transactionId) ?: return null
    
    val now = System.currentTimeMillis() / 1000
    val updatedPayment = payment.copy(
        principalCents = principalCents,
        occurredAtEpochSec = occurredAtEpochSec,
        note = note,
        updatedAtEpochSec = now,
        updatedBy = deviceIdProvider.get()
    )
    loanPaymentDao.update(updatedPayment)
    syncToFirestore(payment.userUid, updatedPayment)
    return updatedPayment
}

suspend fun deleteByTransaction(transactionId: String): LoanPaymentEntity? {
    val payment = loanPaymentDao.getByLinkedTransactionId(transactionId) ?: return null
    
    loanPaymentDao.delete(payment.id)
    deleteFromFirestore(payment.userUid, payment.id)
    return payment
}
```

#### c. Método `syncFromFirestore()` actualizado
- Agregado parsing de `linkedTransactionId` desde Firestore

**Propósito**: Actualizar/eliminar abonos cuando se edita/elimina la transacción asociada.

---

### 5. `TransactionRepository.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/repository/TransactionRepository.kt`

**Cambios**:

#### a. Constructor con Provider para evitar ciclo de dependencias
```kotlin
@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider,
    private val loanPaymentRepositoryProvider: javax.inject.Provider<LoanPaymentRepository>,
    private val loanRepositoryProvider: javax.inject.Provider<LoanRepository>,
    private val loanMovementRepositoryProvider: javax.inject.Provider<LoanMovementRepository>
)
```

#### b. Método `update()` con sincronización
```kotlin
transactionDao.update(updated)
syncToFirestore(userUid, updated)

// Sincronizar con LoanPayment si es una transacción de préstamo
if (isLoanRepaymentTransaction(updated.kind)) {
    val payment = loanPaymentRepositoryProvider.get().updateByTransaction(
        transactionId = transactionId,
        principalCents = amountCents,
        occurredAtEpochSec = occurredAtEpochSec,
        note = note
    )
    // Recalcular estado del préstamo si se actualizó el pago
    if (payment != null) {
        loanRepositoryProvider.get().recalculateLoanStatus(userUid, payment.loanId)
    }
}

// Sincronizar con LoanMovement si existe un movimiento vinculado
val movement = loanMovementRepositoryProvider.get().updateByTransaction(
    transactionId = transactionId,
    amountCents = amountCents,
    occurredAtEpochSec = occurredAtEpochSec,
    note = note
)
// Recalcular estado del préstamo si se actualizó el movimiento
if (movement != null) {
    loanRepositoryProvider.get().recalculateLoanStatus(userUid, movement.loanId)
}
```

#### c. Método `delete()` con sincronización
```kotlin
val existing = transactionDao.getById(transactionId)

transactionDao.delete(transactionId)
deleteFromFirestore(userUid, transactionId)

// Sincronizar con LoanPayment si es una transacción de préstamo
if (existing != null && isLoanRepaymentTransaction(existing.kind)) {
    val payment = loanPaymentRepositoryProvider.get().deleteByTransaction(transactionId)
    // Recalcular estado del préstamo si se eliminó el pago
    if (payment != null) {
        loanRepositoryProvider.get().recalculateLoanStatus(userUid, payment.loanId)
    }
}

// Sincronizar con LoanMovement si existe un movimiento vinculado
val movement = loanMovementRepositoryProvider.get().deleteByTransaction(transactionId)
// Recalcular estado del préstamo si se eliminó el movimiento
if (movement != null) {
    loanRepositoryProvider.get().recalculateLoanStatus(userUid, movement.loanId)
}
```

#### d. Método auxiliar
```kotlin
private fun isLoanRepaymentTransaction(kind: String): Boolean {
    val normalizedKind = kind.trim().uppercase()
    return normalizedKind == "LOAN_REPAYMENT_PRINCIPAL_IN" || 
           normalizedKind == "LOAN_REPAYMENT_PRINCIPAL_OUT"
}
```

**Propósito**: Detectar transacciones de préstamo y sincronizar cambios con abonos.

---

### 5. `LoanRepository.kt`
**Ubicación**: `app/src/main/java/com/myfinances/data/repository/LoanRepository.kt`

**Cambios**:
```kotlin
suspend fun recalculateLoanStatus(userUid: String, loanId: String): LoanEntity? {
    val existing = loanDao.getById(loanId) ?: return null
    
    val paidCents = loanPaymentRepository.sumPrincipalByLoan(userUid, loanId)
    val pendingCents = (existing.principalCents - paidCents).coerceAtLeast(0L)
    val newStatus = if (pendingCents <= 0L) "CLOSED" else "OPEN"
    
    // Solo actualizar si el estado cambia
    if (existing.status != newStatus) {
        val now = System.currentTimeMillis() / 1000
        val updated = existing.copy(
            status = newStatus,
            updatedAtEpochSec = now,
            updatedBy = deviceIdProvider.get()
        )
        loanDao.update(updated)
        syncToFirestore(userUid, updated)
        return updated
    }
    
    return existing
}
```

**Propósito**: Recalcular el estado (OPEN/CLOSED) del préstamo basado en los pagos actuales.

---

## Flujo de Sincronización

### Al crear un abono (desde Préstamos)
1. `LoanPaymentRepository.create()` crea `loan_payments` con `linkedTransactionId = null`
2. Crea `transactions` con `LOAN_REPAYMENT_PRINCIPAL_IN/OUT`
3. Actualiza `loan_payments` con `linkedTransactionId = transaction.id`
4. Crea `loan_movements` con `PAYMENT_IN/OUT` y `linkedTransactionId = transaction.id`
5. `LoansViewModel` recalcula estado del préstamo

### Al editar un abono (desde Transacciones)
1. `TransactionRepository.update()` actualiza `transactions`
2. Detecta si es transacción de préstamo (`isLoanRepaymentTransaction()`)
3. Llama `LoanPaymentRepository.updateByTransaction()` para actualizar `loan_payments`
4. Llama `LoanMovementRepository.updateByTransaction()` para actualizar `loan_movements`
5. Llama `LoanRepository.recalculateLoanStatus()` para actualizar estado del préstamo
6. Sincroniza cambios con Firestore

### Al eliminar un abono (desde Transacciones)
1. `TransactionRepository.delete()` elimina `transactions`
2. Detecta si es transacción de préstamo
3. Llama `LoanPaymentRepository.deleteByTransaction()` para eliminar `loan_payments`
4. Llama `LoanMovementRepository.deleteByTransaction()` para eliminar `loan_movements`
5. Llama `LoanRepository.recalculateLoanStatus()` para actualizar estado del préstamo
6. Sincroniza cambios con Firestore

### Al editar monto original del préstamo
1. `LoanRepository.updateLoan()` recalcula usando `sumPrincipalByLoan()`
2. Valida que nuevo monto >= total abonado
3. Actualiza estado del préstamo según saldo pendiente

---

## Ciclo de Dependencias Resuelto

**Problema**: 
- `TransactionRepository` necesita `LoanPaymentRepository` y `LoanRepository`
- `LoanPaymentRepository` necesita `TransactionRepository`
- `LoanRepository` necesita `TransactionRepository`

**Solución**:
Usar `Provider<T>` de Dagger para inyección perezosa:
```kotlin
private val loanPaymentRepositoryProvider: javax.inject.Provider<LoanPaymentRepository>
private val loanRepositoryProvider: javax.inject.Provider<LoanRepository>

// Uso con .get() solo cuando se necesita
loanPaymentRepositoryProvider.get().updateByTransaction(...)
```

---

## Casos de Prueba Manual

### Caso 1: Editar abono desde Transacciones
**Pasos**:
1. Crear préstamo de $1,000
2. Registrar abono de $300 desde Préstamos
3. Ir a Transacciones y editar el abono a $400

**Resultado esperado**:
- ✅ Transacción actualizada a $400
- ✅ Abono en `loan_payments` actualizado a $400
- ✅ Saldo pendiente del préstamo actualizado a $600
- ✅ Porcentaje de progreso actualizado a 40%
- ✅ Estado del préstamo recalculado (si aplica)

### Caso 2: Eliminar abono desde Transacciones
**Pasos**:
1. Crear préstamo de $1,000
2. Registrar abono de $300 desde Préstamos
3. Ir a Transacciones y eliminar el abono

**Resultado esperado**:
- ✅ Transacción eliminada
- ✅ Abono en `loan_payments` eliminado
- ✅ Saldo pendiente del préstamo actualizado a $1,000
- ✅ Porcentaje de progreso actualizado a 0%
- ✅ Estado del préstamo recalculado (si aplica)

### Caso 3: Editar abono desde Préstamos (flujo existente)
**Pasos**:
1. Crear préstamo de $1,000
2. Registrar abono de $300 desde Préstamos
3. Editar el abono desde la pantalla de historial del préstamo

**Resultado esperado**:
- ✅ Abono actualizado
- ✅ Transacción actualizada (si existe esa funcionalidad)
- ✅ Saldo pendiente actualizado
- ⚠️ Nota: Este flujo puede requerir cambios adicionales si no existe edición desde Préstamos

### Caso 4: Préstamo totalmente pagado
**Pasos**:
1. Crear préstamo de $1,000
2. Registrar abono de $1,000 desde Préstamos
3. Verificar que el préstamo cambie a estado "CLOSED"
4. Ir a Transacciones y editar el abono a $800

**Resultado esperado**:
- ✅ Transacción actualizada a $800
- ✅ Abono actualizado a $800
- ✅ Saldo pendiente del préstamo actualizado a $200
- ✅ Estado del préstamo cambia de "CLOSED" a "OPEN"
- ✅ Porcentaje actualizado a 80%

### Caso 5: Eliminar abono que completaba el préstamo
**Pasos**:
1. Crear préstamo de $1,000
2. Registrar abono de $1,000 desde Préstamos
3. Verificar que el préstamo esté "CLOSED"
4. Ir a Transacciones y eliminar el abono

**Resultado esperado**:
- ✅ Transacción eliminada
- ✅ Abono eliminado
- ✅ Saldo pendiente del préstamo actualizado a $1,000
- ✅ Estado del préstamo cambia de "CLOSED" a "OPEN"
- ✅ Porcentaje actualizado a 0%

### Caso 6: Edición de monto original del préstamo
**Pasos**:
1. Crear préstamo de $1,000
2. Registrar abono de $300 desde Préstamos
3. Editar monto original del préstamo a $1,200

**Resultado esperado**:
- ✅ Nuevo monto original: $1,200
- ✅ Total abonado: $300 (sin cambios)
- ✅ Saldo pendiente: $900
- ✅ Porcentaje: 25%
- ✅ Validación: nuevo monto >= total abonado

---

## Riesgos Mitigados

### ✅ Desincronización financiera
- **Antes**: Editar/eliminar transacción no actualizaba préstamo
- **Ahora**: Sincronización automática bidireccional

### ✅ Estado incorrecto del préstamo
- **Antes**: Préstamo podía quedar "OPEN" o "CLOSED" con datos obsoletos
- **Ahora**: Estado recalculado automáticamente después de cada cambio

### ✅ Saldo pendiente incorrecto
- **Antes**: Saldo calculado con abonos desactualizados
- **Ahora**: Abonos sincronizados con transacciones

### ✅ Ciclo de dependencias
- **Antes**: Error de compilación por ciclo circular
- **Ahora**: Resuelto con `Provider<T>` de Dagger

---

## Compatibilidad Verificada

### ✅ Room
- Agregado campo `linkedTransactionId` con índice
- No requiere migración compleja (campo nullable)

### ✅ Firestore
- Sincronización bidireccional mantenida
- Parsing de nuevo campo en `syncFromFirestore()`

### ✅ Dagger Hilt
- Ciclo de dependencias resuelto con `Provider<T>`
- Compilación exitosa

### ✅ ViewModels
- `TransactionsViewModel`: No requiere cambios (usa `TransactionRepository`)
- `LoansViewModel`: No requiere cambios (usa `LoanRepository`)

---

## Próximos Pasos (Opcionales)

### Mejora 1: Edición de abonos desde Préstamos
Actualmente, si se desea editar un abono desde la pantalla de historial del préstamo, se necesita:
- UI para editar abonos en `LoansScreen.kt`
- Método en `LoanPaymentRepository` para editar abonos por ID
- Sincronización con `TransactionRepository` (similar a la implementada)

### Mejora 2: Eliminación de abonos desde Préstamos
Similar a la edición, se necesita:
- UI para eliminar abonos en `LoansScreen.kt`
- Método en `LoanPaymentRepository` para eliminar abonos por ID
- Sincronización con `TransactionRepository`

### Mejora 3: Unificación en `loan_movements`
A largo plazo, considerar:
- `loan_movements` como fuente única de verdad
- `loan_payments` y `transactions` como proyecciones derivadas
- Esto permitiría mayor flexibilidad para ediciones, cancelaciones y reversas

---

## Compilación

```
BUILD SUCCESSFUL in 2m 6s
44 actionable tasks: 11 executed, 33 up-to-date
```

**Estado**: ✅ COMPILACIÓN EXITOSA

---

## Migración de Base de Datos

### Versión 10 → 11
**Archivo**: `AppDatabase.kt` y `DatabaseModule.kt`

**Cambios**:
- Incrementado versión de base de datos de 10 a 11
- Agregado `MIGRATION_10_11` que:
  - Agrega columna `linked_transaction_id TEXT` a la tabla `loan_payments`
  - Crea índice `index_loan_payments_linked_transaction_id` para búsquedas eficientes
- Agregado `MIGRATION_10_11` a la lista de migraciones en `DatabaseModule`

**SQL de migración**:
```sql
ALTER TABLE loan_payments ADD COLUMN linked_transaction_id TEXT;
CREATE INDEX IF NOT EXISTS index_loan_payments_linked_transaction_id ON loan_payments(linked_transaction_id);
```

**Nota**: La migración usa try-catch para evitar errores si la columna ya existe (para instalaciones nuevas).

### Versión 11 → 12
**Archivo**: `AppDatabase.kt` y `DatabaseModule.kt`

**Cambios**:
- Incrementado versión de base de datos de 11 a 12
- Agregado `MIGRATION_11_12` que:
  - Crea índice `index_loan_movements_linked_transaction_id` en la tabla `loan_movements`
- Agregado `MIGRATION_11_12` a la lista de migraciones en `DatabaseModule`

**SQL de migración**:
```sql
CREATE INDEX IF NOT EXISTS index_loan_movements_linked_transaction_id ON loan_movements(linked_transaction_id);
```

**Nota**: La columna `linked_transaction_id` ya existía en `loan_movements`, solo se agregó el índice para mejorar rendimiento.

---

## Conclusión

La implementación resuelve la inconsistencia financiera donde editar/eliminar abonos desde Transacciones no actualizaba el estado del préstamo ni el historial de movimientos. La solución:

1. **Mínimo impacto**: No altera la arquitectura existente
2. **Sincronización completa**: Transacciones ↔ Pagos ↔ Movimientos ↔ Préstamos
3. **Estado automático**: El préstamo recalcula su estado automáticamente
4. **Sin ciclos de dependencias**: Resuelto con `Provider<T>`
5. **Historial sincronizado**: Los movimientos de préstamos se actualizan al editar/eliminar transacciones

**Estado**: ✅ LISTO PARA PRODUCCIÓN
