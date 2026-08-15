# ADR-003: Uso de Hilt para inyección de dependencias

**Fecha:** 15 de agosto de 2026  
**Estado:** Aceptado  
**Decisores:** Equipo de arquitectura Xpendz

---

## Contexto

El proyecto depende de múltiples servicios singletones (Repositories, DAOs, Firebase, servicios de seguridad, backup). Se necesitaba un mecanismo de inyección de dependencias que redujera el acoplamiento y facilitara testing.

## Decisión

Adoptar **Hilt** como framework de inyección de dependencias.

## Consecuencias

- **Positivas:**
  - Integración nativa con Android (`@HiltViewModel`, `@HiltAndroidApp`).
  - Validación en tiempo de compilación del grafo de dependencias.
  - Reducción de boilerplate.
  - Facilidad para testar con implementaciones falsas.

- **Negativas:**
  - Curva de aprendizaje para desarrolladores nuevos.
  - Build kapt/ksp adicionales.

## Alternativas consideradas

- **Dagger manual:** Rechazado por verbosidad.
- **Koin:** Rechazado por menor integración oficial con ViewModel y Compose.
