# ADR-005: Sincronización manual pull-based

**Fecha:** 15 de agosto de 2026  
**Estado:** Aceptado  
**Decisores:** Equipo de arquitectura Xpendz

---

## Contexto

La sincronización de datos entre dispositivos es un requisito clave, pero se deseaba evitar complejidad de conflictos y consumo de batería con listeners en tiempo real.

## Decisión

Implementar sincronización **manual pull-based**:
- El usuario (o el inicio de la app) dispara la sincronización.
- Se descarga datos completos de Firestore hacia Room.
- El conflicto se resuelve por **last-write-wins** usando `updatedAtEpochSec`.

## Consecuencias

- **Positivas:**
  - Control explícito del momento de sincronización.
  - Menor consumo de batería y datos.
  - Implementación más simple que sync bidireccional completa.

- **Negativas:**
  - Datos pueden quedar desactualizados entre dispositivos.
  - Mayor probabilidad de conflictos si no se sincroniza frecuentemente.
  - No hay notificaciones push de cambios.

## Alternativas consideradas

- **Firestore listeners en tiempo real:** Rechazado por costo, batería y complejidad.
- **Sync automática periódica:** Considerado para futuras versiones.
