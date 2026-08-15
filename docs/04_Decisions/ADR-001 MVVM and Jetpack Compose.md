# ADR-001: Uso de MVVM con Jetpack Compose

**Fecha:** 15 de agosto de 2026  
**Estado:** Aceptado  
**Decisores:** Equipo de arquitectura Xpendz

---

## Contexto

Xpendz requiere una arquitectura de UI que permita:
- Manejar estados complejos de formularios financieros.
- Sobrevivir cambios de configuración (rotación de pantalla).
- Separar lógica de presentación de lógica de negocio.
- Aprovechar la naturaleza declarativa de Jetpack Compose.

## Decisión

Adoptar el patrón **MVVM (Model-View-ViewModel)** con:
- **View:** Jetpack Compose Screens y Components.
- **ViewModel:** Hilt-injected `ViewModel` que expone `StateFlow` con estados inmutables.
- **Model:** Repositories, DAOs, entidades y servicios.

## Consecuencias

- **Positivas:**
  - Testabilidad mejorada.
  - Separación clara de responsabilidades.
  - Integración natural con Compose (`collectAsState()`).
  - Resiliencia a cambios de configuración.

- **Negativas:**
  - ViewModels pueden crecer si no se extraen UseCases.
  - Requiere disciplina para evitar lógica de UI en ViewModels.

## Alternativas consideradas

- **MVI:** Rechazado por mayor complejidad para el equipo.
- **MVP:** Rechazado por verbosidad y poco natural con Compose.
