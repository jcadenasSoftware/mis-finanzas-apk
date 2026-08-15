# Xpendz Project Overview v1.0

**Fecha:** 15 de agosto de 2026  
**Versión:** 1.0  
**Estado:** Activo

---

## 1. Vision

Xpendz es una aplicación de finanzas personales multiplataforma que busca ayudar a los usuarios a comprender, controlar y planificar su dinero de manera seria, rápida y confiable.

La aplicación prioriza la claridad, la confianza y la precisión por sobre el impacto visual. No busca impresionar; busca reducir la carga cognitiva de gestionar dinero.

## 2. Objectives

- Proporcionar un registro completo de ingresos, gastos, transferencias, cuentas, presupuestos, metas y préstamos.
- Funcionar sin conexión como primera prioridad, sincronizando con la nube cuando sea posible.
- Proteger la información financiera del usuario con cifrado en reposo y respaldos cifrados.
- Ofrecer una interfaz coherente, accesible y predecible basada en un sistema de diseño propio.
- Soportar múltiples dispositivos mediante sincronización manual controlada.

## 3. Context

Xpendz comenzó como aplicación Android nativa. El modelo de negocio inicial es gratuito, con posibilidad futura de monetización mediante planes premium y publicidad.

La aplicación está construida para ser publicada en Google Play Store, cumpliendo con políticas de privacidad, acceso a datos y eliminación de cuenta.

## 4. Technical Stack

| Capa | Tecnología |
|------|------------|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material3 |
| Arquitectura | MVVM + Repository + partial Clean Architecture |
| DI | Hilt |
| Base de datos local | Room (SQLite) |
| Base de datos remota | Firebase Firestore |
| Autenticación | Firebase Authentication |
| Navegación | Jetpack Navigation Compose |
| Async | Kotlin Coroutines + Flow |
| Cifrado local | Android Keystore + AES-256-GCM |
| Cifrado de respaldos | PBKDF2 + AES-256-GCM |
| PDF | Android PdfDocument |
| Build | Gradle + Android Gradle Plugin |

## 5. Target Audience

- Usuarios hispanohablantes que buscan una app sencilla y confiable para gestionar finanzas personales.
- Personas que operan principalmente en monedas locales (COP, USD, EUR) y necesitan múltiples cuentas y categorías.
- Usuarios que prefieren mantener el control de sus datos con sincronización opcional a la nube.

## 6. Success Criteria

- Un nuevo usuario puede registrarse, crear cuentas, registrar transacciones y ver su balance en menos de 5 minutos.
- Los datos deben persistir sin conexión y sincronizarse correctamente entre dispositivos.
- La app debe sentirse estable, predecible y segura.
- La documentación arquitectónica permite a un nuevo desarrollador comprender el sistema sin leer todo el código.

## 7. Project Structure

```
Mis Finanzas/
├── app/                    # Código fuente Android
├── docs/                   # Documentación oficial
├── build/                  # Archivos de build
├── gradle/                 # Wrapper y configuración Gradle
├── keystore/               # Keystore para release
├── .gradle/                # Cache de Gradle
└── .idea/                  # Configuración de IDE
```

## 8. Documentation Map

```
docs/
├── 00_Project/             # Visión, contexto, stack (este documento)
├── 01_Audits/              # Auditorías técnicas
├── 02_DesignSystem/        # Sistema de diseño y tokens
├── 03_Architecture/        # Arquitectura maestra
├── 04_Decisions/           # Architecture Decision Records
├── 05_Roadmaps/            # Roadmaps y planes
├── 06_Sprints/             # Documentación de sprints
├── 07_Quality/             # Calidad, mantenimiento, reportes
├── branding/               # Guías de marca
├── legal/                  # Documentación legal
├── play-store/             # Recursos de Play Store
├── premium/                # Monetización (futuro)
├── release/                # Lanzamiento
└── README.md               # Índice de documentación
```

## 9. Key Stakeholders

- **Usuario final:** persona que gestiona sus finanzas.
- **Desarrollador Android:** mantiene y evoluciona el código.
- **Google Play:** publicación y cumplimiento.
- **Equipo de diseño/UX:** evolución del producto.

## 10. Maintenance Principle

Toda decisión técnica, arquitectónica o de producto significativa debe reflejarse en `docs/`. La documentación se mantiene sincronizada con el código.
