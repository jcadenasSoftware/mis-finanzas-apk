# Xpendz Android - Documentación del Proyecto

Esta carpeta contiene la documentación oficial del proyecto Android Xpendz. Su objetivo es mantener un registro completo y organizado de todas las decisiones técnicas, arquitectónicas y de diseño que afectan la aplicación a lo largo de su ciclo de vida.

## Estructura de carpetas

### `00_Project/`
Documentación general del proyecto, visión, objetivos y contexto general.
- [README.md](00_Project/README.md) - Visión, stack técnico, audiencia y success criteria

### `01_Audits/`
Registros de auditorías técnicas realizadas al proyecto:
- `Visual/` - Auditorías del sistema visual y diseño
- `UX/` - Auditorías de experiencia de usuario
- `Architecture/` - Auditorías de arquitectura técnica
- `Performance/` - Auditorías de rendimiento y optimización
- `Accessibility/` - Auditorías de accesibilidad

### `02_DesignSystem/`
Documentación del sistema de diseño, tokens, componentes y patrones de UI reutilizables.

### `03_Architecture/`
Documentación de la arquitectura técnica, patrones de arquitectura, diagramas y decisiones estructurales.
- [Master Architecture v1.0](03_Architecture/Master%20Architecture%20v1.0.md) - Arquitectura maestra del proyecto
- [Module Dashboard.md](03_Architecture/Module%20Dashboard.md) - Documentación del módulo Dashboard
- [Module Loans.md](03_Architecture/Module%20Loans.md) - Documentación del módulo Loans
- [Module Backup.md](03_Architecture/Module%20Backup.md) - Documentación del módulo Backup

### `04_Decisions/`
Registro de decisiones arquitectónicas y técnicas importantes (Architecture Decision Records).
- [README.md](04_Decisions/README.md) - Índice de ADRs
- [ADR-001 MVVM and Jetpack Compose](04_Decisions/ADR-001%20MVVM%20and%20Jetpack%20Compose.md)
- [ADR-002 Room and Firebase Offline-First](04_Decisions/ADR-002%20Room%20and%20Firebase%20Offline-First.md)
- [ADR-003 Hilt Dependency Injection](04_Decisions/ADR-003%20Hilt%20Dependency%20Injection.md)
- [ADR-004 Design Token System](04_Decisions/ADR-004%20Design%20Token%20System.md)
- [ADR-005 Manual Pull-Based Sync](04_Decisions/ADR-005%20Manual%20Pull-Based%20Sync.md)
- [ADR-006 Backup Encryption](04_Decisions/ADR-006%20Backup%20Encryption.md)

### `05_Roadmaps/`
Planes de trabajo, roadmaps y cronogramas de desarrollo.

### `06_Sprints/`
Documentación específica de sprints, objetivos, entregables y retrospectivas.

### `07_Quality/`
Documentación de aseguramiento de calidad, mantenimiento técnico y estabilidad:
- `Technical Maintenance/` - Sprints de mantenimiento técnico, corrección de APIs y reducción de deuda
- `Visual QA/` - Control de calidad visual y validación de temas (futura)
- `Accessibility/` - Auditorías de accesibilidad y cumplimiento WCAG (futura)
- `Regression Reports/` - Pruebas de regresión y validación funcional (futura)
- `Release Readiness/` - Checklists de pre-release y métricas de calidad (futura)

## Principio fundamental

**Toda decisión importante del proyecto debe quedar documentada en la carpeta correspondiente.**

Esto incluye pero no se limita a:
- Cambios arquitectónicos significativos
- Decisiones de diseño de sistema
- Actualizaciones de dependencias mayores
- Cambios en el stack tecnológico
- Decisiones de refactorización
- Elecciones de patrones de diseño

## Mantenimiento

La documentación debe mantenerse actualizada en sincronía con el desarrollo del proyecto. Cada auditoría, decisión o cambio relevante debe reflejarse en esta estructura.