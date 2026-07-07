# Release Checklist

## Estado

En preparación — pendiente de firma, capturas y configuración de Play Console

## Última actualización

2026-06-29

## Objetivo

Referencia oficial de verificación previa a cada publicación de Xpendz en Google Play Store. Basada exclusivamente en el estado real del proyecto. Debe completarse en orden antes de generar el AAB de producción.

---

## 1. Configuración del proyecto

Verificado en `app/build.gradle.kts`.

| Parámetro            | Valor actual       | Estado          | Observaciones                                                              |
|----------------------|--------------------|-----------------|----------------------------------------------------------------------------|
| `applicationId`      | com.jcadenas.xpendz | ✅ Correcto    | ID definitivo tras migración desde com.myfinances                          |
| `versionCode`        | 1                  | ✅ Correcto     | Incrementar en cada nueva publicación en Play Store                        |
| `versionName`        | 1.0                | ✅ Correcto     | Actualizar junto con versionCode en cada release                           |
| `minSdk`             | 26 (Android 8.0)  | ✅ Correcto     | Compatible con ~98% de dispositivos activos                                |
| `targetSdk`          | 34                 | ✅ Correcto     | Supera el mínimo requerido por Google Play para nuevas apps                |
| `compileSdk`         | 35                 | ✅ Correcto     | Última versión estable                                                     |
| `isMinifyEnabled`    | `true` en release  | ✅ Configurado  | R8 habilitado. Reglas en `proguard-rules.pro`                              |
| `isShrinkResourcesEnabled` | No configurado | ⚠️ Pendiente | No está habilitado. Recomendado añadir `isShrinkResourcesEnabled = true` junto a `isMinifyEnabled` para reducir tamaño del APK/AAB |
| `signingConfig`      | No configurado     | ❌ Pendiente   | No existe bloque `signingConfigs` en `build.gradle.kts`. No se encontró ningún archivo `.jks` o `.keystore` en el proyecto. Requiere configuración antes de generar AAB de producción |
| Compilación Release  | No verificada      | ⏳ Pendiente   | Ejecutar `./gradlew :app:bundleRelease` y confirmar BUILD SUCCESSFUL sin errores ni warnings críticos |

### ProGuard / R8

Verificado en `app/proguard-rules.pro`:

| Regla                              | Estado          | Observaciones                                              |
|------------------------------------|-----------------|------------------------------------------------------------|
| Keep SourceFile + LineNumberTable  | ✅ Configurado  | Facilita debugging de crashes en producción                |
| RenameSourceFileAttribute          | ✅ Configurado  | Oculta nombres reales de archivos en stack traces          |
| Keep Firebase Signature/Annotation | ✅ Configurado  | Necesario para serialización con Firestore                 |
| Keep Room Database y Entities      | ✅ Configurado  | Evita ofuscación de DAOs y entidades Room                  |
| Keep Kotlin Coroutines             | ✅ Configurado  | Evita crashes en dispatcher de corrutinas                  |
| Keep data classes Firestore        | ✅ Configurado  | `com.jcadenas.xpendz.data.local.entity.**`                 |

---

## 2. Recursos gráficos

| Recurso                       | Ruta / Referencia                                    | Estado              | Pendiente / Observaciones                                             |
|-------------------------------|------------------------------------------------------|---------------------|-----------------------------------------------------------------------|
| **Launcher Icon**             | `mipmap-*/xpendz_ico.png`                            | ✅ Disponible       | Presente en hdpi, mdpi, xhdpi, xxhdpi, xxxhdpi                       |
| **Adaptive Icon (API 26+)**   | `mipmap-anydpi-v26/ic_launcher.xml`                  | ✅ Disponible       | `ic_launcher_background.xml` + `ic_launcher_foreground.xml`          |
| **Icono Play Console (512×512)** | No exportado explícitamente                       | ⏳ Pendiente        | Exportar `xpendz_ico.png` a 512×512 PNG para subirlo a Play Console  |
| **Feature Graphic (1024×500)** | feature-graphic.png                                 | ✅ Disponible       | Verificar que esté en la carpeta correcta antes de subir              |
| **Open Graph (1200×630)**     | xpendz-og.webp                                       | ✅ Disponible       | Activo de landing web — no aplica directamente a Play Store           |
| **Splash Screen**             | `Theme.Xpendz.Splash` + `splash_background.xml`     | ✅ Implementado     | Fondo azul #1E6DFF + ícono centrado                                   |
| **Capturas de pantalla**      | No existen en el proyecto                            | ⏳ En preparación   | Mínimo 2 requeridas por Google Play. Ver lista en `01-Play-Listing.md` |

---

## 3. Google Play Console

### Ficha de la tienda

- [ ] Título configurado: **Xpendz** (6/30 caracteres ✅)
- [ ] Descripción corta subida (58 caracteres — ver `01-Play-Listing.md`)
- [ ] Descripción completa subida (ver `01-Play-Listing.md`)
- [ ] Categoría seleccionada: **Finanzas**
- [ ] Icono 512×512 PNG subido
- [ ] Feature Graphic 1024×500 PNG subido
- [ ] Mínimo 2 capturas de pantalla subidas

### Información de contacto

- [ ] Correo electrónico configurado: servicios@jcadenas.com
- [ ] Sitio web configurado: Pendiente de definir (¿jcadenas.com/xpendz?)
- [ ] URL de política de privacidad configurada: **Pendiente de publicar URL pública**

### Políticas y cumplimiento

- [ ] **Data Safety** completada en Play Console
  - [ ] Declarar uso de Firebase Authentication (correo + UID)
  - [ ] Declarar uso de Firebase Firestore (datos financieros del usuario)
  - [ ] Indicar que los datos están cifrados en tránsito (HTTPS/TLS)
  - [ ] Indicar que el usuario puede eliminar sus datos desde la app
  - [ ] Confirmar que no se comparten datos con terceros para publicidad
- [ ] **Cuestionario de clasificación de contenido** completado
  - [ ] Categoría de contenido sugerida: **Everyone** (sin contenido ofensivo ni sensible)
- [ ] **Eliminación de cuenta** declarada y enlace configurado en Play Console (ver `legal/Delete-Account.md`)

### Publicación

- [ ] App firmada con keystore de producción (no debug)
- [ ] AAB (Android App Bundle) generado — NO APK
- [ ] AAB subido a pista de pruebas interna o cerrada
- [ ] Testing interno completado sin crashes
- [ ] Promoción a pista de producción aprobada

---

## 4. Seguridad

| Elemento                     | Estado              | Observaciones                                                                          |
|------------------------------|---------------------|----------------------------------------------------------------------------------------|
| **Autenticación**            | ✅ Implementado     | Firebase Auth: correo/contraseña + Google Sign-In. Sin auth anónima para datos financieros |
| **HTTPS/TLS**                | ✅ Garantizado      | Firebase usa HTTPS por defecto. No hay llamadas HTTP en el código                      |
| **Backup cifrado AES-256-GCM** | ✅ Implementado   | PBKDF2 + salt + AAD — verificado en `BackupEncryptionManager`                          |
| **Android Keystore**         | ✅ Implementado     | `KeyStoreKeyProvider.kt` presente en `core/security/`                                  |
| **Permisos**                 | ✅ Mínimos          | Solo `INTERNET`. Sin ubicación, contactos, cámara ni almacenamiento externo            |
| **Activities exportadas**    | ✅ Correcto         | Solo `MainActivity` exportada, con `MAIN` intent-filter. `FileProvider` y WorkManager provider con `exported="false"` |
| **ProGuard/R8**              | ✅ Configurado      | Ver Sección 1                                                                          |
| **Firestore Security Rules** | ⏳ Pendiente de verificar | No visible desde el proyecto local. Verificar en Firebase Console que las reglas restrinjan acceso por UID |
| **backup_rules.xml**         | ⚠️ Template         | Archivo de plantilla sin personalizar. La base de datos Room podría incluirse en el backup automático de Android. Evaluar excluir archivos sensibles |
| **data_extraction_rules.xml** | ⚠️ Template        | Archivo de plantilla sin personalizar. El bloque `cloud-backup` contiene un TODO sin implementar |
| **PIN / AppLock**            | ❌ No implementado  | No se encontró PinScreen, PinViewModel ni AppLock en el código fuente. La referencia en `01-Play-Listing.md` (ventaja #9) debe corregirse |
| **AdMob**                    | ❌ No presente      | No hay dependencia de AdMob en `build.gradle.kts`                                      |
| **Google Billing**           | ❌ No presente      | No hay dependencia de Billing API                                                       |
| **Play Integrity API**       | ❌ No presente      | No hay integración de Play Integrity                                                    |

---

## 5. Calidad

| Elemento               | Estado              | Observaciones                                                                 |
|------------------------|---------------------|-------------------------------------------------------------------------------|
| **Crashes conocidos**  | ✅ Sin reportes     | No hay crashlytics integrado. Verificar manualmente antes de publicar         |
| **Lint**               | ⏳ Pendiente        | No existe `lint.xml` de configuración. Ejecutar `./gradlew :app:lint` y revisar warnings de nivel error |
| **TODO críticos**      | ⚠️ Presentes        | `data_extraction_rules.xml` contiene TODO sin resolver. `BudgetNotificationWorker` es stub vacío |
| **Modo oscuro**        | ✅ Implementado     | `values-night/` presente. Tema Material3 con soporte dark/light               |
| **Responsive**         | ⏳ Pendiente        | Testear en dispositivos de diferentes tamaños de pantalla. Sin soporte explícito para tablets |
| **Accesibilidad**      | ✅ Básica           | Material3 con tamaños WCAG AA, escalado de texto del sistema. Sin TalkBack avanzado configurado |
| **Performance**        | ⏳ Pendiente        | Verificar que no haya operaciones en el hilo principal (Room y Firestore usan corrutinas) |
| **shrinkResources**    | ⚠️ No configurado   | Añadir `isShrinkResourcesEnabled = true` en el bloque release para reducir tamaño final |

---

## 6. Funcionalidad

Estado verificado en `AUDITORIA_PRE_LANZAMIENTO_XPENDZ.txt` (24 jun 2026).

| Módulo                  | Pantalla / Ruta                                 | Estado          | Probado | Observaciones                                                   |
|-------------------------|-------------------------------------------------|-----------------|---------|------------------------------------------------------------------|
| Onboarding              | `ui/screens/onboarding/`                        | ✅ Listo        | [ ]     | 4 pantallas, DataStore, muestra una sola vez                     |
| Login / Registro        | `ui/screens/login/`                             | ✅ Listo        | [ ]     | Email + Google Sign-In, recuperación de contraseña              |
| Dashboard               | `ui/screens/dashboard/DashboardScreen.kt`       | ✅ Listo        | [ ]     | Balance mensual, cuentas, tendencia, accesos directos           |
| Transacciones (gastos)  | `ui/screens/transactions/`                      | ✅ Listo        | [ ]     | CRUD completo, filtros por cuenta/categoría/fecha/tipo          |
| Transacciones (ingresos)| `ui/screens/transactions/`                      | ✅ Listo        | [ ]     | Misma estructura que gastos                                      |
| Transferencias          | `ui/screens/transfers/`                         | ✅ Listo        | [ ]     | CRUD completo, agrupadas por fecha                              |
| Categorías              | `ui/screens/categories/`                        | ✅ Listo        | [ ]     | Jerarquía raíz/hijo, CRUD, insights mensuales                   |
| Presupuestos            | `ui/screens/budget/`                            | ✅ Listo        | [ ]     | Mensual + base, alertas in-app (no push)                        |
| Metas                   | Tab dentro de `ui/screens/budget/`              | ⚠️ Parcial     | [ ]     | Backend completo, sin pantalla dedicada ni flujo independiente  |
| Préstamos               | `ui/screens/loans/`                             | ✅ Listo        | [ ]     | Dar/recibir, pagos, topup acumulativo, cierre automático        |
| Estadísticas            | `ui/screens/charts/`                            | ✅ Listo        | [ ]     | Dona, barras, tendencia mensual, filtros interactivos           |
| Reportes PDF            | `ui/pdf/` + `ui/screens/reports/`               | ✅ Listo        | [ ]     | 3 tipos, compartir via Intent                                   |
| Backup                  | `ui/screens/settings/BackupSettingsScreen.kt`   | ✅ Listo        | [ ]     | Exportar/importar AES-256-GCM                                   |
| Configuración           | `ui/screens/settings/SettingsScreen.kt`         | ✅ Listo        | [ ]     | País, moneda, contacto, versión                                 |
| Política de privacidad  | `ui/screens/settings/PrivacyPolicyScreen.kt`    | ✅ Listo        | [ ]     | Texto actualizado a Xpendz                                      |
| Privacidad y datos      | `ui/screens/settings/PrivacyAndDataScreen.kt`   | ✅ Listo        | [ ]     | Eliminación local + Firestore con confirmación                  |
| Sincronización manual   | Pull-to-refresh en pantallas principales        | ✅ Listo        | [ ]     | 11 entidades, throttle 45 s, cancelable                         |
| Notificaciones push     | No implementado                                 | ❌ No publicar  | —       | Worker vacío, scheduler cancela. Mención en onboarding pendiente de revisar |
| Tasas de cambio         | Backend completo, sin UI expuesta               | ❌ No publicar  | —       | `ExchangeRateRepository` oculto de facto al usuario             |
| PIN / AppLock           | No implementado                                 | ❌ No publicar  | —       | No encontrado en código fuente                                  |

---

## 7. Pruebas antes de publicar

Ejecutar en dispositivo físico con build de release (no debug) y verificar cada caso.

### Instalación y primer uso

- [ ] Instalación limpia desde AAB de release — sin errores de instalación
- [ ] Splash screen visible y sin parpadeo
- [ ] Onboarding se muestra correctamente en la primera instalación
- [ ] Onboarding no se muestra en la segunda apertura (estado guardado en DataStore)
- [ ] Pantalla de login carga correctamente

### Autenticación

- [ ] Registro con correo y contraseña — cuenta creada correctamente
- [ ] Login con correo y contraseña — acceso al dashboard
- [ ] Recuperación de contraseña — correo enviado correctamente
- [ ] Login con Google Sign-In — autenticación y acceso al dashboard
- [ ] Logout — sesión cerrada, redirige a login
- [ ] Segundo login — datos previos cargados correctamente

### Dashboard

- [ ] Balance mensual calculado correctamente
- [ ] Lista de cuentas con saldo actualizado
- [ ] Gráfico de tendencia renderiza sin errores
- [ ] Accesos directos navegan a los módulos correctos
- [ ] Pull-to-refresh dispara sincronización

### Gastos e ingresos

- [ ] Crear gasto con categoría, cuenta, monto y fecha
- [ ] Editar gasto existente
- [ ] Eliminar gasto con confirmación
- [ ] Filtros por cuenta / categoría / fecha / tipo funcionan correctamente
- [ ] Crear ingreso con los mismos campos
- [ ] Editar y eliminar ingreso

### Transferencias

- [ ] Crear transferencia entre dos cuentas
- [ ] Historial agrupado por fecha se muestra correctamente
- [ ] Editar transferencia existente
- [ ] Eliminar transferencia con confirmación

### Categorías

- [ ] Crear categoría raíz de tipo INCOME
- [ ] Crear categoría raíz de tipo EXPENSE
- [ ] Crear subcategoría bajo una raíz
- [ ] Editar nombre de categoría
- [ ] Eliminar categoría (verificar comportamiento si tiene transacciones asignadas)
- [ ] Insights mensuales se muestran por categoría

### Presupuestos

- [ ] Crear presupuesto mensual por categoría
- [ ] Crear presupuesto base
- [ ] Alerta in-app al superar el límite de presupuesto
- [ ] Copiar presupuesto del mes anterior
- [ ] Pestaña de metas carga sin errores

### Préstamos

- [ ] Crear préstamo "dinero prestado" (yo presto)
- [ ] Crear préstamo "dinero recibido" (me prestan)
- [ ] Registrar pago parcial sobre préstamo existente
- [ ] Cierre automático al registrar pago total
- [ ] Topup sobre préstamo activo de la misma persona
- [ ] Historial de movimientos del préstamo visible

### Estadísticas

- [ ] Gráfico de dona interactivo renderiza correctamente
- [ ] Gráfico de barras horizontales por categoría
- [ ] Gráfico de tendencia mensual
- [ ] Filtros por tipo, cuenta y mes funcionan
- [ ] Tap en gráfico navega a transacciones correspondientes

### Reportes PDF

- [ ] Generar reporte de transacciones del mes actual
- [ ] Generar reporte de balance de cuentas
- [ ] Generar reporte de resumen mensual
- [ ] Compartir PDF via Intent del sistema — archivo recibido correctamente

### Backup y restauración

- [ ] Exportar backup con contraseña — archivo generado y guardado
- [ ] Importar backup con contraseña correcta — datos restaurados
- [ ] Importar backup con contraseña incorrecta — error claro para el usuario
- [ ] Restauración solicita confirmación antes de reemplazar datos actuales

### Sincronización

- [ ] Pull-to-refresh en dashboard sincroniza todas las entidades
- [ ] Throttle de 45 s: segundo pull-to-refresh inmediato ignorado
- [ ] Cancelar sincronización en curso funciona correctamente
- [ ] App funciona con datos locales sin conexión a internet

### Configuración

- [ ] Seleccionar país y moneda — cambio reflejado en la app
- [ ] Estado de sincronización visible
- [ ] Enlace a política de privacidad abre pantalla correcta
- [ ] Reportar problema abre el medio de contacto configurado
- [ ] Número de versión mostrado correctamente (1.0)

### Eliminación de cuenta

- [ ] Eliminar todos los datos — confirmación previa solicitada
- [ ] Datos locales (Room) eliminados correctamente
- [ ] Datos en Firestore eliminados correctamente
- [ ] Redirige a pantalla de login tras eliminar la cuenta

---

## 8. Pendientes para la versión 1.0

Extraídos de `AUDITORIA_PRE_LANZAMIENTO_XPENDZ.txt` y verificación de código fuente.

| Pendiente                                    | Prioridad | Fuente                                         |
|----------------------------------------------|-----------|------------------------------------------------|
| Configurar `signingConfig` y keystore de producción | 🔴 Bloqueante | `build.gradle.kts` — sin firma release configurada |
| Añadir `isShrinkResourcesEnabled = true`     | 🟡 Recomendado | `build.gradle.kts` — no configurado            |
| Exportar icono 512×512 para Play Console     | 🟡 Recomendado | `mipmap-xxxhdpi/xpendz_ico.png` disponible    |
| Capturar screenshots (mínimo 2)              | 🔴 Bloqueante | Requerido por Google Play para publicar        |
| Publicar URL pública de Política de Privacidad | 🔴 Bloqueante | Requerida por Google Play                      |
| Completar Data Safety en Play Console        | 🔴 Bloqueante | Declaración obligatoria para nuevas apps       |
| Completar cuestionario de clasificación de contenido | 🔴 Bloqueante | Requerido por Google Play                |
| Verificar reglas de Firestore Security en Firebase Console | 🟡 Recomendado | No visible desde proyecto local   |
| Personalizar `backup_rules.xml` para excluir archivos sensibles | 🟡 Recomendado | Template sin configurar |
| Personalizar `data_extraction_rules.xml`     | 🟡 Recomendado | Template con TODO pendiente                    |
| Ejecutar `./gradlew :app:lint` y resolver errores | 🟡 Recomendado | Sin lint.xml de configuración              |
| Ejecutar `./gradlew :app:bundleRelease` y confirmar BUILD SUCCESSFUL | 🔴 Bloqueante | No verificado |
| Revisar mención de notificaciones en pantallas de onboarding | 🟡 Recomendado | Worker de notificaciones es stub vacío |
| Corregir ventaja competitiva #9 en `01-Play-Listing.md` | 🟡 Recomendado | PIN no encontrado en código fuente |

---

## 9. Pendientes para versiones futuras

### v1.1

| Funcionalidad                              | Origen                                                      |
|--------------------------------------------|-------------------------------------------------------------|
| Notificaciones push del sistema (FCM)      | `BudgetNotificationWorker` — stub vacío                     |
| Alertas de presupuesto en background       | `NotificationScheduler` — cancela en lugar de programar    |
| Sincronización automática (WorkManager periódico) | No existe worker periódico activo                    |
| Metas como módulo independiente            | Backend Room + Firestore completo, sin pantalla dedicada    |
| Gestión de tasas de cambio (UI)            | `ExchangeRateRepository` completo, sin pantalla expuesta    |
| Mejora de seguridad en backup              | Migrar contraseña de `String` a `CharArray` nativo en pipeline criptográfico |
| PIN / AppLock                              | No implementado en v1.0                                     |
| Personalización avanzada de backup rules   | `backup_rules.xml` y `data_extraction_rules.xml` sin configurar |

### v1.2 y Roadmap

| Funcionalidad                        | Observaciones                                            |
|--------------------------------------|----------------------------------------------------------|
| Modelo Freemium / Premium            | No implementado en v1.0 — sin Billing API ni restricciones por plan |
| Publicidad con AdMob                 | No presente en dependencias. Requiere diseño de estrategia de monetización |
| Soporte para tablets                 | Sin layout adaptativo declarado                          |
| Internacionalización (i18n)          | Solo `values/strings.xml` en español — sin localización adicional |
| Accesibilidad avanzada (TalkBack)    | No configurada explícitamente                            |
| Exportar a Excel / CSV               | No implementado                                          |
| Widgets de pantalla de inicio        | No implementado                                          |
| Soporte multimoneda con conversión   | `ExchangeRateRepository` presente pero sin UI            |
| Play Integrity API                   | No integrada — evaluar para versiones con Premium        |

---

## 10. Checklist final de publicación

Lista de validación inmediatamente antes de publicar en producción.

- [ ] `versionCode` incrementado respecto a la versión anterior en Play Store
- [ ] `versionName` actualizado
- [ ] `signingConfig` de producción configurado en `build.gradle.kts`
- [ ] `isShrinkResourcesEnabled = true` añadido al bloque release
- [ ] `./gradlew :app:bundleRelease` ejecutado — BUILD SUCCESSFUL sin errores
- [ ] AAB (no APK) generado con firma de producción
- [ ] Todas las pruebas de la **Sección 7** completadas sin fallas
- [ ] Icono 512×512 PNG subido a Play Console
- [ ] Feature Graphic 1024×500 PNG subido a Play Console
- [ ] Mínimo 2 capturas de pantalla subidas a Play Console
- [ ] Descripción corta y completa configuradas (ver `01-Play-Listing.md`)
- [ ] URL pública de Política de Privacidad configurada en Play Console
- [ ] Sección Data Safety completada en Play Console
- [ ] Cuestionario de clasificación de contenido completado
- [ ] Información de eliminación de cuenta declarada en Play Console
- [ ] AAB subido a pista interna y probado correctamente
- [ ] Promoción a producción aprobada
