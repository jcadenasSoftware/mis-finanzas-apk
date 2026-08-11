# Xpendz Android Visual Audit v1.0

**Fecha:** 11 de agosto de 2026  
**Versión:** 1.0  
**Tipo:** Auditoría técnica del sistema visual  
**Alcance:** Sistema Theme de Jetpack Compose, componentes reutilizables, Dashboard, recursos visuales y configuración Gradle

---

## Objetivo

Realizar una auditoría completa del sistema visual de la aplicación Android Xpendz para evaluar el estado actual del sistema de diseño, identificar riesgos técnicos, oportunidades de mejora y establecer una base sólida para la evolución del producto durante los próximos años.

**Importante:** Esta auditoría es exclusivamente de análisis. No se ha modificado ningún código, no se han realizado commits ni refactorizaciones.

---

## Tabla de Contenidos

1. [Estado del Theme](#1-estado-del-theme)
2. [Inventario de colores](#2-inventario-de-colores)
3. [Inventario tipográfico](#3-inventario-tipográfico)
4. [Inventario de Shapes](#4-inventario-de-shapes)
5. [Elevaciones](#5-elevaciones)
6. [Componentes reutilizables](#6-componentes-reutilizables)
7. [Nivel de adopción de Material 3](#7-nivel-de-adopción-de-material-3)
8. [Consistencia visual](#8-consistencia-visual)
9. [Riesgos técnicos](#9-riesgos-técnicos)
10. [Oportunidades de mejora](#10-oportunidades-de-mejora)
11. [Roadmap priorizado](#11-roadmap-priorizado)

---

## 1. Estado del Theme

### Estado actual
Existe un theme Compose aplicado globalmente desde `MainActivity` mediante `XpendzTheme`, con `MaterialTheme(colorScheme, shapes, typography)` y `Surface` de fondo.

**Referencias:**
- [MainActivity - Aplicación del theme](../../app/src/main/java/com/myfinances/MainActivity.kt)
- [Theme.kt - Definición del theme](../../app/src/main/java/com/myfinances/ui/theme/Theme.kt)

### Fortalezas
- Hay una entrada única al sistema visual
- Hay soporte light/dark
- El theme ya controla status bar
- `Typography` y `Shapes` están conectados globalmente

### Debilidades estructurales
El sistema de tokens está limitado a:
- `Color.kt`
- `Theme.kt`
- `Type.kt`
- `Shape.kt`

No existen `Dimens.kt`, `Spacing.kt`, `Elevation.kt`, `dimens.xml` ni recursos de fuente. Esto deja fuera del sistema varias decisiones visuales que hoy viven dispersas en pantalla.

**Archivos del sistema theme:**
- [Color.kt - Tokens de color](../../app/src/main/java/com/myfinances/ui/theme/Color.kt)
- [Theme.kt - Configuración del theme](../../app/src/main/java/com/myfinances/ui/theme/Theme.kt)
- [Type.kt - Sistema tipográfico](../../app/src/main/java/com/myfinances/ui/theme/Type.kt)
- [Shape.kt - Sistema de formas](../../app/src/main/java/com/myfinances/ui/theme/Shape.kt)

### Hallazgo clave
El `lightColorScheme`/`darkColorScheme` solo personaliza una parte de los roles de M3: `primary`, `secondary`, `background`, `surface`, `surfaceVariant` y algunos `on*`. El resto queda en valores por defecto de Material 3.

**Conclusión:** Hay theme, pero todavía no hay un **design token system completo**.

---

## 2. Inventario de colores

### Compose tokens
Definidos en `Color.kt`:

| Categoría | Tokens | Descripción |
|----------|--------|-------------|
| **Primarios** | `Primary`, `PrimaryDark`, `PrimaryLight` | Escala de colores principales |
| **Secundarios** | `Secondary`, `SecondaryDark`, `SecondaryLight` | Colores secundarios de acento |
| **Background/Surface** | `BackgroundLight`, `BackgroundDark`, `SurfaceLight`, `SurfaceDark` | Fondos y superficies |
| **On colors** | `OnPrimaryLight`, `OnPrimaryDark`, `OnBackgroundLight`, `OnBackgroundDark`, `OnSurfaceLight`, `OnSurfaceDark` | Texto sobre fondos |
| **Semánticos** | `Income`, `IncomeLight`, `Expense`, `ExpenseLight`, `Transfer`, `TransferLight` | Colores de estado financiero |
| **Cards** | `CardLight`, `CardDark` | Colores específicos para tarjetas |
| **Accent extra** | `GoldAccent`, `GoldAccentDark` | Acentos dorados (heredados de desktop) |

### Observaciones de color
- **Duplicación semántica:** `SecondaryLight` repite el mismo valor de `Secondary`; `GoldAccent` repite el mismo valor de `Secondary`
- **Tokens huérfanos:** Algunos tokens como `OnPrimaryDark` no se utilizan en el código
- **Lenguaje semántico parcial:** Parte del lenguaje semántico está bien encaminado (`Income`, `Expense`, `Transfer`), pero no está extendido a todo el sistema

### XML colors
En XML existe otra capa con nomenclatura heredada:
- `purple_200`, `purple_500`, `purple_700`
- `teal_200`, `teal_700`
- `splash_blue`

**Referencia:** [colors.xml](../../app/src/main/res/values/colors.xml)

### Lectura de diseño
Esto indica dos sistemas coexistiendo:
1. **Palette Compose moderna** en Kotlin
2. **Palette XML heredada** para theme base/splash/adaptive icon

**Conclusión:** El color system existe, pero está **fragmentado entre Compose y XML** y todavía no opera como una única fuente de verdad.

---

## 3. Inventario tipográfico

### Base tipográfica
`Type.kt` define los 15 estilos principales de Material 3:

| Estilo | Uso típico |
|--------|-----------|
| `displayLarge/Medium/Small` | Títulos heroicos, cabeceras grandes |
| `headlineLarge/Medium/Small` | Títulos de sección |
| `titleLarge/Medium/Small` | Títulos de componentes |
| `bodyLarge/Medium/Small` | Texto de cuerpo |
| `labelLarge/Medium/Small` | Etiquetas, captions |

**Referencia:** [Type.kt - Sistema tipográfico completo](../../app/src/main/java/com/myfinances/ui/theme/Type.kt)

### Fortalezas
- La jerarquía tipográfica está completa
- El sistema usa pesos razonables (`Bold`, `SemiBold`, `Medium`, `Normal`)
- La app reutiliza `MaterialTheme.typography` en muchas pantallas, incluido Dashboard

**Referencia:** [DashboardScreen - Uso de tipografía](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)

### Debilidades
- Toda la tipografía usa `FontFamily.Default`; no hay una familia tipográfica de marca
- **38 overrides directos de `fontSize`** en UI fuera del theme
- **17 usos de `TextStyle(...)` locales** fuera del theme

**Ejemplos de fugas:**
- [AddTransactionScreen - TextStyle local](../../app/src/main/java/com/myfinances/ui/screens/transactions/AddTransactionScreen.kt)
- [AppNavHost - fontSize directo](../../app/src/main/java/com/myfinances/ui/navigation/AppNavHost.kt)
- [OnboardingScreen - fontSize directo](../../app/src/main/java/com/myfinances/ui/screens/onboarding/OnboardingScreen.kt)

**Conclusión:** La base tipográfica está mejor resuelta que colores/elevaciones, pero todavía hay **fugas de estilos locales**.

---

## 4. Inventario de Shapes

### Shapes globales
`Shapes` define la escala de radios de borde:

| Token | Valor (dp) | Uso típico |
|-------|-----------|-----------|
| `extraSmall` | 8 | Elementos pequeños |
| `small` | 10 | Componentes compactos |
| `medium` | 12 | Contenedores medianos |
| `large` | 14 | Cards y contenedores principales |
| `extraLarge` | 18 | Contenedores grandes |

**Referencia:** [Shape.kt - Sistema de formas](../../app/src/main/java/com/myfinances/ui/theme/Shape.kt)

### Lectura UX/UI
La identidad formal es clara:
- Radios medianos/altos
- Superficies suaves
- Cards y contenedores amigables
- Look financiero moderno

### Debilidad
**16 usos directos** de `RoundedCornerShape(...)` fuera del theme:

| Ubicación | Valor usado | Contexto |
|-----------|-------------|----------|
| `UserAccountHeader` | 20.dp | Header de usuario |
| `AppNavHost` | 16.dp (top corners) | Barra de navegación |
| `Onboarding` | 28.dp | Botones heroicos |
| `HamburgerMenu` | 4.dp / 8.dp | Indicadores de activación |

**Referencias:**
- [UserAccountHeader - Shape custom](../../app/src/main/java/com/myfinances/ui/components/UserAccountHeader.kt)
- [AppNavHost - Shape custom](../../app/src/main/java/com/myfinances/ui/navigation/AppNavHost.kt)
- [OnboardingScreen - Shape custom](../../app/src/main/java/com/myfinances/ui/screens/onboarding/OnboardingScreen.kt)
- [HamburgerMenu - Shapes múltiples](../../app/src/main/java/com/myfinances/ui/components/HamburgerMenu.kt)

**Conclusión:** La dirección de shapes es consistente, pero el sistema aún no gobierna todos los radios.

---

## 5. Elevaciones

### Estado actual
No existe una capa central de elevaciones. La app usa valores directos en componentes y pantallas.

### Patrones encontrados
- Dashboard usa `tonalElevation`, `shadowElevation` y `defaultElevation` manualmente
- `AccountCard`, `SettingsSection`, `UserAccountHeader` fijan elevaciones locales
- Encontré **51 definiciones directas de elevación** en UI

**Ejemplos:**
- [DashboardScreen - Elevaciones sincronización](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)
- [DashboardScreen - Elevaciones cards](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)
- [AccountCard - Elevación card](../../app/src/main/java/com/myfinances/ui/components/AccountCard.kt)
- [SettingsSection - Elevación settings](../../app/src/main/java/com/myfinances/ui/components/SettingsSection.kt)

**Conclusión:** La elevación es hoy un **atributo táctico**, no un token del design system.

---

## 6. Componentes reutilizables

### Inventario completo
Encontré **11 componentes** en `ui/components`:

#### Shell / navegación
| Componente | Propósito | Calidad |
|------------|-----------|---------|
| `CompactHeader` | Header compacto reutilizable | ✅ Sólido |
| `HamburgerMenu` | Menú lateral desplegable | ⚠️ Mezcla tokens/hardcodes |
| `HamburgerMenuButton` | Botón de menú | ✅ Sólido |
| `SyncSwipeRefresh` | Wrapper transversal de pull-to-refresh | ⚠️ Usa Accompanist (no oficial M3) |

#### Settings system
| Componente | Propósito | Calidad |
|------------|-----------|---------|
| `SectionHeader` | Títulos de sección | ✅ Sólido |
| `SettingsRow` | Fila de configuración | ✅ Sólido |
| `SettingsSection` | Sección completa de settings | ✅ Sólido |
| `SyncStatusChip` | Indicador de estado de sincronización | ✅ Sólido |
| `UserAccountHeader` | Header de información de usuario | ⚠️ Mezcla tokens/hardcodes |

#### Finanzas / cuentas
| Componente | Propósito | Calidad |
|------------|-----------|---------|
| `AddAccountDialog` | Diálogo para crear cuentas | ⚠️ Mezcla tokens/hardcodes |
| `AccountCard` | Tarjeta de cuenta | ⚠️ No se usa realmente |

**Referencias:**
- [CompactHeader](../../app/src/main/java/com/myfinances/ui/components/CompactHeader.kt)
- [HamburgerMenu](../../app/src/main/java/com/myfinances/ui/components/HamburgerMenu.kt)
- [HamburgerMenuButton](../../app/src/main/java/com/myfinances/ui/components/HamburgerMenuButton.kt)
- [SyncSwipeRefresh](../../app/src/main/java/com/myfinances/ui/components/SyncSwipeRefresh.kt)
- [SettingsSection](../../app/src/main/java/com/myfinances/ui/components/SettingsSection.kt)
- [SettingsRow](../../app/src/main/java/com/myfinances/ui/components/SettingsRow.kt)
- [SectionHeader](../../app/src/main/java/com/myfinances/ui/components/SectionHeader.kt)
- [SyncStatusChip](../../app/src/main/java/com/myfinances/ui/components/SyncStatusChip.kt)
- [UserAccountHeader](../../app/src/main/java/com/myfinances/ui/components/UserAccountHeader.kt)
- [AddAccountDialog](../../app/src/main/java/com/myfinances/ui/components/AddAccountDialog.kt)
- [AccountCard](../../app/src/main/java/com/myfinances/ui/components/AccountCard.kt)

### Evaluación de calidad

**Más sólidos:**
- `CompactHeader`
- `SettingsSection` + `SettingsRow` + `SectionHeader`
- `SyncStatusChip`

Estos ya operan como auténticos bloques de sistema.

**Más frágiles:**
- `AccountCard`
- `AddAccountDialog`
- `HamburgerMenu`
- `UserAccountHeader`

Porque mezclan tokens del theme con colores/radios hardcodeados.

### Hallazgo importante
`AccountCard` existe, pero **no se usa en pantallas**. Dashboard implementa su propia variante `RankedAccountCard`, duplicando lógica visual y de dominio.

**Referencias:**
- [AccountCard - Definición no usada](../../app/src/main/java/com/myfinances/ui/components/AccountCard.kt)
- [DashboardScreen - RankedAccountCard duplicado](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)
- [DashboardScreen - Helpers duplicados](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)

---

## 7. Nivel de adopción de Material 3

### Nivel: Alto, pero no completo

### Evidencia a favor
- `MaterialTheme` global
- Uso extensivo de componentes M3: `Scaffold`, `Surface`, `NavigationBar`, `NavigationBarItem`, `AlertDialog`, `ModalBottomSheet`, `OutlinedTextField`, `AssistChip`, `ElevatedCard`, etc.

**Referencias:**
- [AppNavHost - NavigationBar M3](../../app/src/main/java/com/myfinances/ui/navigation/AppNavHost.kt)
- [AddAccountDialog - Componentes M3](../../app/src/main/java/com/myfinances/ui/components/AddAccountDialog.kt)
- [DashboardScreen - Componentes M3](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)

### Evidencia de adopción parcial
- El theme XML base sigue siendo `Theme.MaterialComponents.DayNight.DarkActionBar`, no `Theme.Material3`
- `SyncSwipeRefresh` usa **Accompanist SwipeRefresh**, no el patrón actual oficial de pull refresh
- Uso extenso de `androidx.compose.material.icons`, lo cual no es un problema en sí, pero confirma mezcla de capas

**Referencias:**
- [themes.xml - Theme MaterialComponents](../../app/src/main/res/values/themes.xml)
- [SyncSwipeRefresh - Accompanist](../../app/src/main/java/com/myfinances/ui/components/SyncSwipeRefresh.kt)

**Conclusión:** Xpendz ya es una app Compose/M3 en lo visible, pero su infraestructura visual todavía no está 100% alineada con M3 end-to-end.

---

## 8. Consistencia visual

### Lo consistente
- Paleta principal azul + acentos dorados/semánticos
- Predominio de cards, superficies suaves y radios redondeados
- Reutilización clara de `CompactHeader`, `HamburgerMenu` y `SettingsSection`
- Dashboard usa mucho `MaterialTheme`, lo que indica intención sistémica

**Referencias:**
- [DashboardScreen - Uso consistente de MaterialTheme](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)
- [SettingsScreen - Reutilización de componentes](../../app/src/main/java/com/myfinances/ui/screens/settings/SettingsScreen.kt)

### Lo inconsistente
- **348 ocurrencias** de colores hardcodeados en UI
- **38 overrides de `fontSize`** fuera del theme
- **246 strings inline** en `Text(...)`/`contentDescription`, mientras `strings.xml` prácticamente solo contiene `app_name`

**Referencias:**
- [strings.xml - Solo app_name](../../app/src/main/res/values/strings.xml)
- [LoginScreen - Ejemplo de inconsistencia](../../app/src/main/java/com/myfinances/ui/screens/login/LoginScreen.kt)

### Dashboard como patrón general
Dashboard revela bien el patrón actual del producto:
- Usa shell compartido (`CompactHeader`, `HamburgerMenu`, `SyncSwipeRefresh`, `AddAccountDialog`)
- Pero el resto del lenguaje visual está construido con **composables privados dentro de la pantalla**

**Referencias:**
- [DashboardScreen - Shell compartido](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)
- [DashboardScreen - Composables privados](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)

**Conclusión:** La app ya tiene un lenguaje visual reconocible, pero todavía no tiene una **consistencia gobernada por sistema**.

---

## 9. Riesgos técnicos

### 1. Dark theme frágil
Muchas superficies siguen usando `Color.White` y otros hex directos, lo que reduce la fidelidad del modo oscuro.

**Referencias:**
- [DashboardScreen - Color.White hardcodeado](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)
- [LoginScreen - Colores hardcodeados](../../app/src/main/java/com/myfinances/ui/screens/login/LoginScreen.kt)
- [OnboardingScreen - Colores hardcodeados](../../app/src/main/java/com/myfinances/ui/screens/onboarding/OnboardingScreen.kt)

### 2. Color roles incompletos
El color scheme custom no cubre todos los roles de M3; varias decisiones visuales dependen de defaults de librería o hardcodes.

**Referencia:** [Theme.kt - Roles incompletos](../../app/src/main/java/com/myfinances/ui/theme/Theme.kt)

### 3. Duplicación de componentes y lógica visual
Helpers de cuentas y cards están repetidos en Dashboard, Transfers y components.

**Referencias:**
- [AddAccountDialog - Helpers](../../app/src/main/java/com/myfinances/ui/components/AddAccountDialog.kt)
- [DashboardScreen - Helpers duplicados](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)

### 4. Localización y escalabilidad de copy débiles
Muy pocas strings viven en recursos; la mayoría está inline en código Kotlin.

**Referencia:** [strings.xml - Solo app_name](../../app/src/main/res/values/strings.xml)

### 5. Sistema de assets inconsistente
Manifest/splash usan `xpendz_ico`, pero existen recursos `ic_launcher`/adaptive icon y algunos flujos UI usan `R.drawable.ic_launcher`.

**Referencias:**
- [AndroidManifest - xpendz_ico](../../app/src/main/AndroidManifest.xml)
- [splash_background.xml - xpendz_ico](../../app/src/main/res/drawable/splash_background.xml)
- [ic_launcher.xml - Adaptive icon](../../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
- [DashboardScreen - Uso de ic_launcher](../../app/src/main/java/com/myfinances/ui/screens/dashboard/DashboardScreen.kt)

### 6. Deriva de naming/legacy
`rootProject.name = "MyFinances"` convive con `namespace/applicationId = "com.jcadenas.xpendz"` y fuentes ubicadas bajo carpeta `com\myfinances`.

**Referencias:**
- [settings.gradle.kts - MyFinances](../../settings.gradle.kts)
- [build.gradle.kts - Xpendz namespace](../../app/build.gradle.kts)

---

## 10. Oportunidades de mejora

Sin proponer rediseños, las oportunidades más claras son:

### 1. Convertir el theme actual en un token system completo
- Colores
- Spacing
- Elevación
- Tamaños
- Estados
- Iconografía

### 2. Reducir deuda visual hardcodeada
- Colores inline
- Shapes locales
- Tipografía local
- Elevaciones por pantalla

### 3. Separar mejor primitives, patterns y feature UI
- Hoy el Dashboard concentra demasiada UI privada
- Settings está más cerca de un patrón reusable que Dashboard

### 4. Fortalecer dark mode real
- No solo soporte de theme, sino adopción consistente de tokens

### 5. Externalizar copy UI
- Internacionalización
- Consistencia editorial
- Accesibilidad

### 6. Añadir mecanismos de validación visual
- No encontré `@Preview` en el código
- Esto limita evolución segura del sistema

---

## 11. Roadmap priorizado

### Prioridad 1 — Fundaciones del sistema
- Completar la capa de tokens faltante
- Formalizar roles de color M3 que hoy están implícitos o hardcodeados
- Definir convención única entre Compose tokens y XML resources
- Estabilizar dark/light desde tokens

### Prioridad 2 — Normalización de componentes
- Consolidar componentes reutilizables ya existentes
- Eliminar duplicación entre componentes compartidos y variantes locales por pantalla
- Convertir patrones repetidos de cards, headers, rows, dialogs y chips en primitives/patterns claros

### Prioridad 3 — Consistencia transversal
- Reducir colores y tipografías inline
- Mover strings UI a recursos
- Unificar iconografía y assets de marca

### Prioridad 4 — Gobierno del design system
- Añadir previews
- Añadir validación visual por estados clave
- Documentar reglas de uso para theme, tokens y componentes

### Prioridad 5 — Higiene técnica de largo plazo
- Resolver naming legacy (`MyFinances` vs `Xpendz`) donde afecte mantenibilidad
- Revisar dependencias puente heredadas del stack visual
- Alinear launcher/splash/assets a una sola estrategia

---

## Veredicto final

**Xpendz no parte de cero.** Ya tiene una base útil de sistema visual en Compose y una adopción real de Material 3.

Pero hoy esa base está en un punto intermedio: **hay theme y componentes, pero todavía no hay un design system completamente gobernado, centralizado y escalable**.

### Resumen técnico
- **Stack relevante:** AGP 9.0.1, Kotlin 2.2.10, Compose BOM 2024.10.01, Material3 presente
- **Theme foundation:** sólida pero parcial
- **Adopción Material 3:** alta en Compose, media en sistema de tokens, baja en centralización de recursos
- **Consistencia visual:** media
- **Escalabilidad a largo plazo:** limitada si no se consolida el design system

### Si tuviera que resumirlo en una frase:

**La app ya posee identidad visual y estructura técnica suficiente para evolucionar, pero necesita pasar de "UI bien construida por features" a "sistema visual gobernado por tokens y patrones compartidos".**

---

**Próximos pasos sugeridos:**

1. Validar este informe con el equipo de diseño y arquitectura
2. Priorizar las oportunidades de mejora según los objetivos del negocio
3. Crear un plan de trabajo específico para Sprint 2 basado en este roadmap
4. Documentar cada decisión arquitectónica y de diseño en las carpetas correspondientes