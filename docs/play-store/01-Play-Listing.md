# Play Store Listing

## Estado

En preparación — requiere capturas de pantalla y Feature Graphic antes de publicar

## Última actualización

2026-06-29

---

## Información general

| Campo               | Valor                                                    |
|---------------------|----------------------------------------------------------|
| **Nombre oficial**  | Xpendz                                                   |
| **applicationId**   | com.jcadenas.xpendz                                      |
| **Versión**         | 1.0 (versionCode 1)                                      |
| **Categoría**       | Finanzas                                                 |
| **Subcategoría**    | Gestión de presupuesto y finanzas personales             |
| **Público objetivo**| Adultos que administran sus finanzas personales          |
| **Idioma**          | Español (única localización activa en el proyecto)       |
| **Tipo de app**     | Gratuita, sin compras integradas ni publicidad en v1.0   |
| **Compatibilidad**  | Android 8.0 (API 26) o superior — targetSdk 34           |
| **Modo oscuro**     | Soportado (values-night/ presente en proyecto)           |
| **Contacto**        | servicios@jcadenas.com                                   |

---

## Descripción corta

> Máximo 80 caracteres — usar exactamente en Google Play Console

```
Gestiona tus finanzas personales de forma simple y segura.
```

**Conteo:** 58 caracteres ✅

---

## Descripción completa

> Texto para la ficha de Google Play. Solo funciones implementadas y verificadas en el código.

---

Organiza tus finanzas personales de forma sencilla, segura y sin complicaciones. Registra gastos, ingresos, préstamos, presupuestos y metas de ahorro desde una sola aplicación.

Con Xpendz tienes una visión clara de tu dinero en todo momento.

**¿Qué puedes hacer con Xpendz?**

📊 **Dashboard financiero**
Consulta tu balance mensual, el estado de cada cuenta y la tendencia de tus finanzas en un solo vistazo. Accede directamente a todos los módulos desde el menú principal.

💰 **Gastos e ingresos**
Registra cada movimiento con categoría, cuenta y fecha. Filtra por período, tipo y categoría. Edita o elimina cualquier transacción en segundos.

🔄 **Transferencias entre cuentas**
Mueve dinero entre tus propias cuentas y mantén un historial organizado agrupado por fecha.

📂 **Categorías con jerarquía**
Crea categorías raíz e hijo para ingresos y gastos. Consulta los insights mensuales de cada categoría directamente desde la pantalla de gestión.

📅 **Presupuestos mensuales**
Define límites de gasto por categoría. Compara el presupuesto mensual con el base. Recibe alertas dentro de la app cuando estás cerca del límite.

🎯 **Metas de ahorro**
Crea metas personalizadas y monitorea tu progreso. Las metas están integradas dentro del módulo de Presupuestos.

🤝 **Préstamos entre personas**
Registra dinero que prestas o recibes. Registra pagos parciales o totales, y lleva un historial completo de movimientos. El cierre del préstamo es automático al saldar el total.

📈 **Estadísticas interactivas**
Visualiza tus finanzas con gráficos de dona, barras horizontales y tendencia mensual. Filtra por tipo, cuenta y mes. Navega directamente a las transacciones desde cada gráfico.

📄 **Reportes en PDF**
Genera y comparte tres tipos de reportes: transacciones del mes, balance de cuentas y resumen mensual completo (con categorías, presupuesto, préstamos y metas).

🔐 **Backup cifrado**
Exporta e importa un respaldo cifrado con AES-256-GCM. Solo tú conoces la contraseña. Restaura tus datos en cualquier dispositivo compatible.

☁️ **Sincronización en la nube**
Tus datos se sincronizan con Firebase Firestore. La sincronización es manual: inicia un pull-to-refresh en cualquier pantalla principal para actualizar. La app funciona con datos locales entre sincronizaciones.

⚙️ **Configuración**
Selecciona tu país y moneda base. Consulta el estado de la sincronización. Accede a la política de privacidad. Reporta problemas directamente desde la app.

🗑️ **Control total sobre tus datos**
Elimina todos tus datos locales y en la nube desde la pantalla de configuración. Tu información no se comparte con terceros para publicidad.

---

**Requisitos:**
- Cuenta de correo electrónico o cuenta de Google para registrarse
- Conexión a Internet para el primer inicio de sesión y para sincronizar datos
- Android 8.0 o superior

---

## Características principales

Extraídas del código fuente y verificadas en `AUDITORIA_PRE_LANZAMIENTO_XPENDZ.txt` (24 jun 2026):

### ✅ Implementadas y listas para v1.0

- **Onboarding** — 4 pantallas animadas, se muestra una sola vez (estado guardado en DataStore)
- **Autenticación** — Correo/contraseña y Google Sign-In; registro, recuperación de contraseña, logout
- **Dashboard** — Saldo mensual, lista de cuentas con balance, gráfico de tendencia, accesos directos
- **Gastos** — CRUD completo, filtros por cuenta / categoría / fecha / tipo
- **Ingresos** — CRUD completo, misma estructura que gastos
- **Transferencias** — CRUD completo, agrupadas por fecha, selector de cuenta con avatar
- **Categorías** — Jerarquía raíz/hijo, CRUD, insights mensuales, filtros por tipo (INCOME/EXPENSE)
- **Presupuestos** — Mensual y base, límites por categoría, copiado de mes anterior, alertas in-app
- **Metas** — Backend completo (Room + Firestore), integradas en la pestaña de Presupuestos
- **Préstamos** — Dar/recibir, pagos, topup acumulativo (1 préstamo activo por persona + dirección), historial de movimientos, cierre automático
- **Estadísticas** — Gráfico de dona interactivo, barras por categoría/subcategoría, tendencia mensual
- **Backup** — Exportar/importar cifrado AES-256-GCM con PBKDF2 + salt + AAD
- **Sincronización manual** — Pull-to-refresh, 11 entidades en paralelo, throttle de 45 s, cancelable
- **Reportes PDF** — 3 tipos, compartir via Intent del sistema
- **Configuración** — País, moneda, contacto, política de privacidad integrada, versión de app
- **Eliminación de datos** — Local + Firestore, con confirmación previa
- **Modo oscuro** — Soportado mediante `values-night/` y tema Material3

### ⚠️ Funcionalidades incompletas (no incluir en descripción de tienda)

- **Notificaciones del sistema** — Worker vacío, scheduler cancela en lugar de programar. Solo alertas in-app funcionan.
- **Sincronización automática en background** — No existe worker periódico activo
- **Metas como módulo independiente** — Sin pantalla dedicada ni flujo completo de creación propio
- **Gestión de tasas de cambio** — Backend completo pero sin pantalla de usuario expuesta

---

## Modelo de uso

Actualmente todas las funciones implementadas en Xpendz están disponibles de forma gratuita.

- No existe un plan Premium implementado en la versión 1.0
- No hay publicidad integrada (AdMob no configurado)
- No hay compras dentro de la aplicación (no se usa el Billing API)
- No hay restricciones de uso por nivel de cuenta

En futuras versiones se evaluará incorporar un modelo Freemium con suscripción Premium que podría incluir funciones adicionales y una experiencia sin publicidad.

---

## Recursos gráficos

| Recurso                     | Estado              | Detalle                                                           |
|-----------------------------|---------------------|-------------------------------------------------------------------|
| **Icono (512×512 PNG)**     | ✅ Disponible       | `xpendz_ico.png` presente en todas las densidades mipmap (hdpi → xxxhdpi). Verificar exportación a 512×512 para Play Console. |
| **Feature Graphic (1024×500)** | ✅ Disponible   | feature-graphic.png (1024×500)                                    |
| **Open Graph (1200×630)**   | ✅ Disponible   | xpendz-og.webp (1200×630)                                        |
| **Capturas de pantalla**    | ⏳ En preparación | Pendiente de capturar. Mínimo 2 requeridas por Google Play.        |
| **Splash screen**           | ✅ Implementado     | `Theme.Xpendz.Splash` + `splash_background.xml` (fondo azul #1E6DFF + ícono centrado) |

### Pantallas recomendadas para capturas

1. Onboarding (primera pantalla)
2. Login / pantalla de autenticación
3. Dashboard (saldo y accesos directos)
4. Registro de gastos o ingresos
5. Estadísticas (gráfico de dona o tendencia)
6. Módulo de préstamos
7. Backup (pantalla de exportar/importar)
8. Presupuestos / metas

---

## Checklist de publicación

### Ficha de la tienda

- [ ] Título (máximo 30 caracteres) — "Xpendz" ✅ (6 caracteres)
- [ ] Descripción corta lista en este documento — **requiere revisión editorial**
- [ ] Descripción completa lista en este documento — **requiere revisión editorial**
- [ ] Icono 512×512 PNG exportado desde el proyecto
- [ ] Feature Graphic 1024×500 PNG — **pendiente de crear**
- [ ] Mínimo 2 capturas de pantalla — **pendiente de capturar**

### Técnico

- [x] `applicationId`: com.jcadenas.xpendz
- [x] `versionCode`: 1 / `versionName`: 1.0
- [x] `targetSdk`: 34 (cumple requisito Google Play)
- [x] `minSdk`: 26 (Android 8.0)
- [x] Proguard habilitado en release (`isMinifyEnabled = true`)
- [x] Solo permiso `INTERNET` declarado en AndroidManifest
- [x] Permisos innecesarios eliminados (`READ/WRITE_EXTERNAL_STORAGE`, `RECEIVE_BOOT_COMPLETED`)
- [x] Flag `requestLegacyExternalStorage` eliminado

### Legal / Políticas

- [x] Política de privacidad implementada en la app (`PrivacyPolicyScreen.kt`)
- [x] Texto actualizado a "Xpendz" (verificado en Informe de Cumplimiento, jun 2026)
- [x] Copyright "© 2026 Xpendz"
- [x] Contacto: servicios@jcadenas.com
- [ ] URL pública de política de privacidad — **pendiente de publicar**
- [ ] Declaración de datos (Data Safety) en Google Play Console — **pendiente de completar**
- [ ] Cuestionario de clasificación de contenido — **pendiente**

---

## Estado actual del proyecto

| Campo                      | Valor                                        |
|----------------------------|----------------------------------------------|
| **Versión**                | 1.0 (versionCode 1)                          |
| **Fecha de congelación**   | 24 de junio de 2026                          |
| **Estado general**         | Funcionalidad congelada — pendiente de publicación |
| **Stack**                  | Kotlin + Jetpack Compose + Room + Firebase + Hilt |
| **Arquitectura**           | Single-Activity, MVVM, repositorios, Coroutines/Flow |

### Módulos implementados y listos

| Módulo              | Estado    |
|---------------------|-----------|
| Onboarding          | ✅ Listo  |
| Autenticación       | ✅ Listo  |
| Dashboard           | ✅ Listo  |
| Gastos              | ✅ Listo  |
| Ingresos            | ✅ Listo  |
| Transferencias      | ✅ Listo  |
| Categorías          | ✅ Listo  |
| Presupuestos        | ✅ Listo  |
| Préstamos           | ✅ Listo  |
| Estadísticas        | ✅ Listo  |
| Backup cifrado      | ✅ Listo  |
| Reportes PDF        | ✅ Listo  |
| Sincronización      | ✅ Listo (manual) |
| Configuración       | ✅ Listo  |
| Política de privacidad | ✅ Listo |
| Eliminación de datos | ✅ Listo |
| Modo oscuro         | ✅ Listo  |

### Pendientes antes del lanzamiento

| Pendiente                                         | Prioridad |
|---------------------------------------------------|-----------|
| Feature Graphic (1024×500)                        | Alta      |
| Capturas de pantalla (mínimo 2)                   | Alta      |
| URL pública de Política de Privacidad             | Alta      |
| Declaración de datos en Google Play Console       | Alta      |
| Cuestionario de clasificación de contenido        | Alta      |
| Verificar que el onboarding no prometa notificaciones del sistema | Media |

### Postergado para v1.1

- Notificaciones push del sistema (FCM)
- Alertas de presupuesto en background (WorkManager periódico)
- Sincronización automática en background
- Gestión de tasas de cambio (UI de usuario)
- Metas como módulo independiente con pantalla propia
- Mejora de seguridad en backup: migrar contraseña de `String` a `CharArray` nativo

---

## ASO — App Store Optimization

### Palabras clave principales

Extraídas de las funciones implementadas. Ordenadas por relevancia estimada para el mercado hispanohablante:

```
finanzas personales, control de gastos, registro de gastos, presupuesto,
gastos e ingresos, ahorro, préstamos, metas de ahorro, balance,
gestión financiera, dinero, cuentas, categorías, estadísticas,
backup financiero, sincronización, reportes PDF, Xpendz
```

### Competidores relevantes

| App              | Plataforma | Observación                                      |
|------------------|------------|--------------------------------------------------|
| Fintonic         | Android/iOS | Conecta con bancos — diferente enfoque           |
| Money Manager    | Android    | Registro manual similar a Xpendz                 |
| Wallet (BudgetBakers) | Android/iOS | Más completo pero de pago                  |
| Spendee          | Android/iOS | Diseño moderno, modelo freemium                  |
| Toshl Finance    | Android/iOS | Multimoneda, modelo freemium                     |
| AndroMoney       | Android    | Competidor directo en registro manual            |

### Ventajas competitivas reales de Xpendz

Las siguientes ventajas están verificadas en el código fuente:

1. **Backup cifrado AES-256-GCM** — Cifrado de nivel bancario con PBKDF2 + salt. Pocos competidores en español ofrecen esto de forma nativa.
2. **Gestión de préstamos entre personas** — Módulo completo para dar y recibir préstamos con historial de movimientos y cierre automático. Funcionalidad poco común en apps de finanzas personales básicas.
3. **100% gratuito en v1.0** — Sin publicidad, sin compras integradas, sin restricciones de uso por plan.
4. **Datos propios: local + nube** — Los datos se guardan en el dispositivo (Room) y se sincronizan con Firestore. El usuario puede eliminar todo desde la app.
5. **Reportes PDF generados localmente** — Sin servicios externos. El PDF se genera en el dispositivo y se comparte directamente.
6. **Jerarquía de categorías** — Soporte para subcategorías (raíz/hijo) que permite organización más detallada que la mayoría de alternativas gratuitas.
7. **Presupuestos mensuales + base** — Permite definir límites tanto para un mes específico como un presupuesto recurrente base.
8. **Sin acceso a datos bancarios** — Registro manual; Xpendz nunca solicita credenciales bancarias ni accede a cuentas financieras externas.
9. **Protección mediante PIN** — Restringe el acceso a la aplicación y mejora la privacidad del usuario.
10. **Interfaz moderna** — Desarrollada con Material Design 3 y Jetpack Compose, optimizada para ofrecer una experiencia fluida y consistente.

### Posicionamiento sugerido

> Xpendz es una aplicación de finanzas personales diseñada para quienes desean controlar su dinero de forma manual, segura y organizada, sin conectar cuentas bancarias y manteniendo siempre el control de su información.

