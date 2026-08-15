# ADR-004: Adopción de un Design Token System

**Fecha:** 15 de agosto de 2026  
**Estado:** Aceptado  
**Decisores:** Equipo de arquitectura y diseño Xpendz

---

## Contexto

La UI del proyecto contenía valores visuales hardcodeados en múltiples pantallas, generando inconsistencias y dificultando cambios globales o soporte dark/light.

## Decisión

Adoptar un **Design Token System** centralizado a través de `XpendzThemeTokens`, con tokens para colores, espaciado, elevaciones, shapes y tipografía.

## Consecuencias

- **Positivas:**
  - Consistencia visual en toda la app.
  - Soporte sistemático de temas claro/oscuro.
  - Cambios globales centralizados.
  - Reducción gradual de hardcodes visuales.

- **Negativas:**
  - Requiere migración incremental de módulos.
  - Algunos componentes personalizados (gráficos) no encajan fácilmente.

## Alternativas consideradas

- **MaterialTheme directo:** Rechazado por falta de semántica financiera y rigidez.
- **No usar tokens:** Rechazado por inconsistencia y deuda técnica.
