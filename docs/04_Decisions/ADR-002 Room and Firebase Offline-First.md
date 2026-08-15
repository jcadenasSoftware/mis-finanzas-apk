# ADR-002: Uso de Room + Firebase en arquitectura offline-first

**Fecha:** 15 de agosto de 2026  
**Estado:** Aceptado  
**Decisores:** Equipo de arquitectura Xpendz

---

## Contexto

La aplicación debe funcionar sin conexión y sincronizarse entre dispositivos. Era necesario elegir una estrategia de almacenamiento local y remoto.

## Decisión

Adoptar una arquitectura **offline-first**:
- **Room** como base de datos local y fuente de verdad inmediata.
- **Firebase Firestore** como almacenamiento remoto para sincronización entre dispositivos.
- **Firebase Auth** para autenticación.
- Cada `Repository` escribe primero en Room y luego sincroniza con Firestore.

## Consecuencias

- **Positivas:**
  - Funcionamiento offline completo.
  - Respuesta inmediata de la UI.
  - Multi-dispositivo mediante sincronización.
  - Sin backend propio que mantener.

- **Negativas:**
  - Lógica de sincronización acumulada en cada Repository.
  - Riesgo de conflictos si dos dispositivos editan simultáneamente.
  - Dependencia de Firebase.

## Alternativas consideradas

- **Firestore como fuente de verdad:** Rechazado por latencia y dependencia de red.
- **Backend propio REST + SQLite:** Rechazado por mayor costo de operación.
