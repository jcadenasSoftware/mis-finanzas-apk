# Data Safety

## Estado

En preparación — requiere verificación de reglas Firestore en Firebase Console antes de publicar

## Última actualización

2026-06-29

## Objetivo

Referencia oficial para completar la sección **Data Safety** de Google Play Console de forma precisa y verificable. Toda la información está extraída directamente del código fuente. Las secciones marcadas como "Pendiente de verificar" requieren acceso a Firebase Console y no son visibles desde el proyecto local.

---

## Resumen ejecutivo

### Qué datos recopila Xpendz

Xpendz recopila únicamente los datos que el usuario introduce de forma explícita para el funcionamiento de la aplicación de finanzas personales:

- **Cuenta de usuario**: correo electrónico y UID de Firebase Authentication (obligatorios para operar)
- **Datos financieros**: transacciones (gastos/ingresos), cuentas, categorías, transferencias, presupuestos, metas, préstamos — todos introducidos manualmente por el usuario
- **Configuración**: país y moneda base seleccionados por el usuario
- **Identificador de dispositivo**: UUID generado localmente (no vinculado a identidad real del usuario, usado para control de sincronización)

### Qué datos NO recopila

La aplicación **no recopila** ubicación, contactos, SMS, llamadas, cámara, micrófono, fotos, identificadores publicitarios, datos bancarios reales, credenciales financieras, historial de navegación, salud, ni ningún otro dato sensible. No existen servicios de analytics ni crash reporting integrados.

### Dónde se almacenan los datos

| Destino                    | Qué contiene                                           | Control del usuario |
|----------------------------|--------------------------------------------------------|---------------------|
| **Room (SQLite local)**    | Todos los datos financieros + configuración            | Sí — puede eliminarlos desde la app |
| **Firebase Firestore**     | Copia sincronizada de todos los datos financieros      | Sí — eliminación desde la app |
| **SharedPreferences**      | UUID de dispositivo, preferencias internas             | Se elimina al desinstalar la app |
| **DataStore**              | Booleano `onboarding_completed`                        | Se elimina al desinstalar la app |
| **Android Keystore**       | Clave maestra AES-256 para backup (`xpendz_local_master_key_v1`) | No exportable |
| **Archivo de backup**      | Copia cifrada de datos Room — exportada por el usuario | Totalmente del usuario |

### Qué datos se sincronizan con servidores externos

Solo Firebase Authentication y Firebase Firestore (ambos de Google). No hay ningún otro servidor externo. La sincronización es **manual** (pull-to-refresh iniciado por el usuario).

---

## Inventario completo de datos

Verificado en: `AuthRepository.kt`, `TransactionRepository.kt`, `UserSettingsRepository.kt`, `DeviceIdProvider.kt`, `DatabaseModule.kt`, `BackupServiceImpl.kt`, `FirebaseModule.kt`.

| Dato | Tipo | Origen | Uso | Almacenamiento local | Sincronización | Eliminación |
|------|------|--------|-----|----------------------|----------------|-------------|
| **Correo electrónico** | Identificación personal | Firebase Auth (login/registro) | Autenticar al usuario | No (solo en Firebase Auth) | Firebase Auth (Google) | Al eliminar cuenta Firebase |
| **UID de Firebase** | Identificador único | Firebase Auth | Identificar datos del usuario en Firestore y Room | SharedPreferences implícito vía sesión; Room como FK | Firebase Firestore | Al eliminar cuenta |
| **Nombre o foto (Google)** | Personal | Google Sign-In (`FirebaseUser.displayName`, `photoUrl`) | Mostrar en UI (no almacenado en Room) | No persiste en Room ni SharedPreferences | No | N/A |
| **Contraseña** | Credencial | Introducida por el usuario | Autenticación — nunca almacenada localmente | No almacenada | Firebase Auth (hash en servidores Google) | N/A |
| **UUID de dispositivo** | Identificador técnico | Generado localmente (`DeviceIdProvider`) | Campo `updatedBy` en entidades para control de sincronización | `SharedPreferences` ("myfinances_prefs", clave "device_id") | Sí — se envía a Firestore como campo `updated_by` en cada entidad | Al desinstalar la app |
| **Transacciones** (gastos/ingresos) | Datos financieros | Introducidas por el usuario | Registro de movimientos, balance, estadísticas, reportes, backup | Room (`myfinances.db`, tabla `transactions`) | Firestore (`users/{uid}/transactions/`) | Sí — desde la app o al eliminar cuenta |
| **Cuentas** | Datos financieros | Introducidas por el usuario | Gestión de saldos, filtros, transferencias | Room (tabla `accounts`) | Firestore (`users/{uid}/accounts/`) | Sí |
| **Categorías** | Datos del usuario | Introducidas por el usuario | Clasificación de transacciones, presupuestos, estadísticas | Room (tabla `categories`) | Firestore (`users/{uid}/categories/`) | Sí |
| **Transferencias** | Datos financieros | Introducidas por el usuario | Registro de movimientos entre cuentas | Room (tabla `transfers`) | Firestore (`users/{uid}/transfers/`) | Sí |
| **Presupuestos** | Datos financieros | Introducidos por el usuario | Control de límites de gasto por categoría | Room (tabla `budgets`) | Firestore (`users/{uid}/budgets/`) | Sí |
| **Metas** | Datos financieros | Introducidas por el usuario | Seguimiento de objetivos de ahorro | Room (tabla `goals`) | Firestore (`users/{uid}/goals/`) | Sí |
| **Préstamos** | Datos financieros | Introducidos por el usuario | Registro de préstamos dar/recibir | Room (tablas `loans`, `loan_payments`, `loan_movements`) | Firestore (`users/{uid}/loans/`, `loanPayments/`, `loanMovements/`) | Sí |
| **Tasas de cambio** | Configuración | Pendiente de verificar (sin UI expuesta) | Conversión de moneda (backend completo, sin pantalla) | Room (tabla `exchange_rates`) | Firestore (`users/{uid}/exchangeRates/`) | Sí |
| **Configuración** (país, moneda) | Configuración del usuario | Seleccionada por el usuario | Moneda base, formato de valores | Room (tabla `user_settings`) | Firestore (`users/{uid}/settings/user`) | Sí |
| **Estado del onboarding** | Preferencia interna | Generado por la app | Mostrar onboarding solo una vez | DataStore (`xpendz_prefs`, clave `onboarding_completed`) | No | Al desinstalar |
| **Archivo de backup** | Datos financieros cifrados | Exportado por el usuario | Restaurar datos en otro dispositivo | No — guardado por el usuario mediante SAF | No — el archivo permanece en el dispositivo del usuario | Responsabilidad del usuario |
| **Clave maestra de backup** | Clave criptográfica | Android Keystore | Cifrar/descifrar archivos de backup | Android Keystore (`xpendz_local_master_key_v1`, AES-256-GCM) | No | Al desinstalar |
| **PDF de reportes** | Datos financieros | Generados por la app | Exportar y compartir resúmenes financieros | No — generados temporalmente y compartidos via Intent | No | El archivo temporal es gestionado por Android |
| **Logs de depuración** | Técnico | Generados por la app (`Log.d/e`) | Debugging durante desarrollo | Solo en logcat del dispositivo (no persistido) | No | No aplica |
| **Crash reports** | Técnico | No hay servicio integrado | N/A | No | No | N/A |
| **Analytics** | Comportamiento del usuario | No hay servicio integrado | N/A | No | No | N/A |
| **Publicidad** | Identificadores publicitarios | No hay AdMob ni publicidad | N/A | No | No | N/A |

---

## Datos enviados a terceros

### Firebase Authentication (Google)

- **Qué se envía**: correo electrónico y contraseña (para registro/login con email), o token de Google (para Google Sign-In)
- **Propósito**: autenticación del usuario
- **Proveedor**: Google LLC
- **Cifrado**: HTTPS/TLS — gestionado por el SDK de Firebase
- **Fuente**: `AuthRepository.kt` — `signInWithEmail()`, `createUserWithEmail()`, `signInWithGoogle()`
- **Control del usuario**: el usuario puede eliminar su cuenta de Firebase desde la pantalla de Privacidad y datos

### Firebase Firestore (Google)

- **Qué se envía**: todos los datos financieros del usuario (transacciones, cuentas, categorías, transferencias, presupuestos, metas, préstamos, configuración), más el campo `updated_by` con el UUID de dispositivo
- **Propósito**: sincronización entre dispositivos y respaldo en la nube
- **Proveedor**: Google LLC
- **Cifrado**: HTTPS/TLS en tránsito; Firestore cifra los datos en reposo
- **Cuándo se envía**: en cada operación de creación/edición/eliminación, y al ejecutar un pull-to-refresh manual
- **Fuente**: todos los repositorios (`TransactionRepository.kt`, `UserSettingsRepository.kt`, etc.)
- **Control del usuario**: puede eliminar todos sus datos de Firestore desde la app (`PrivacyAndDataScreen.kt`)

### Google Sign-In (Google Play Services)

- **Qué se envía**: solicitud de autenticación — el usuario autentica con su cuenta Google
- **Propósito**: autenticación con Google como proveedor alternativo
- **Proveedor**: Google LLC
- **Control del usuario**: puede desconectar su cuenta Google desde ajustes de su dispositivo

### Compartición de PDF via Intent (sistema Android)

- **Qué se envía**: archivo PDF temporal con datos financieros del período seleccionado
- **Propósito**: exportar y compartir un reporte — acción explícita del usuario
- **Destino**: la app que el usuario seleccione en el selector de Intent (correo, WhatsApp, Drive, etc.)
- **Control del usuario**: el usuario decide a qué app compartir en cada ocasión
- **Fuente**: `ui/pdf/` — compartir via `Intent.ACTION_SEND`
- **Xpendz no envía el PDF a ningún servidor propio**

---

## Datos NO recopilados

Los siguientes datos **no están presentes** en el código fuente ni en las dependencias del proyecto:

| Dato / Categoría                    | Estado      | Justificación                                                   |
|-------------------------------------|-------------|-----------------------------------------------------------------|
| Ubicación GPS / red                 | ❌ No        | Sin permiso `ACCESS_FINE_LOCATION` ni `ACCESS_COARSE_LOCATION`. Sin API de ubicación en dependencias |
| Contactos                           | ❌ No        | Sin permiso `READ_CONTACTS`. Los nombres de contrapartes en préstamos los introduce el usuario manualmente |
| SMS / llamadas                      | ❌ No        | Sin permiso `READ_SMS` ni `READ_CALL_LOG`                        |
| Micrófono                          | ❌ No        | Sin permiso `RECORD_AUDIO`                                       |
| Cámara                             | ❌ No        | Sin permiso `CAMERA`                                             |
| Fotos / galería                    | ❌ No        | Sin permiso `READ_MEDIA_IMAGES`. Coil se usa para cargar imágenes de perfil de Google en memoria, no se almacenan |
| Archivos del dispositivo           | ❌ No        | Sin `READ_EXTERNAL_STORAGE` ni `WRITE_EXTERNAL_STORAGE`. El backup usa SAF (acceso delegado por el usuario) |
| Historial de navegación web        | ❌ No        | Sin WebView ni SDK de navegación                                 |
| Credenciales bancarias             | ❌ No        | Registro manual de transacciones — Xpendz no conecta con bancos  |
| Datos de tarjetas                  | ❌ No        | Sin Billing API ni integración con sistemas de pago              |
| Identificadores publicitarios (AAID) | ❌ No      | Sin AdMob ni ningún SDK de publicidad en dependencias            |
| Datos de salud                     | ❌ No        | Sin Health Connect ni API de salud                               |
| Actividad en otras apps            | ❌ No        | Sin permiso `QUERY_ALL_PACKAGES` ni acceso a accesibilidad        |
| Mensajes (WhatsApp, SMS, email)    | ❌ No        | Sin acceso a apps de mensajería salvo Intent del usuario         |
| Analytics de comportamiento        | ❌ No        | Sin Firebase Analytics, Mixpanel, Amplitude ni similar           |
| Crash reporting automático         | ❌ No        | Sin Firebase Crashlytics ni Sentry. Solo `Log.d/e` en logcat local |
| Notificaciones push                | ❌ No        | Sin `firebase-messaging`. Sin permiso `POST_NOTIFICATIONS`       |

---

## Cifrado

### Comunicaciones en tránsito

| Conexión                       | Protocolo | Responsable       | Verificación                          |
|-------------------------------|-----------|-------------------|---------------------------------------|
| App ↔ Firebase Auth            | HTTPS/TLS | SDK Firebase      | Declarado en `PrivacyPolicyScreen.kt` |
| App ↔ Firebase Firestore       | HTTPS/TLS | SDK Firebase      | Declarado en `PrivacyPolicyScreen.kt` |
| App ↔ Google Sign-In           | HTTPS/TLS | SDK Google Play   | Gestionado por Google Play Services   |

No hay ninguna comunicación HTTP en el proyecto. Todas las comunicaciones con servidores externos usan TLS.

### Cifrado en reposo

#### Backup de usuario — AES-256-GCM con PBKDF2

Verificado en `BackupServiceImpl.kt`, `KeyStoreKeyProvider.kt`:

| Componente             | Detalle                                                                 |
|------------------------|-------------------------------------------------------------------------|
| **Algoritmo**          | AES-256-GCM (Galois/Counter Mode)                                       |
| **Derivación de clave**| PBKDF2 con contraseña del usuario + salt aleatorio                      |
| **AAD**                | Additional Authenticated Data — previene manipulación del ciphertext    |
| **Salt**               | Generado aleatoriamente por operación — único por cada backup           |
| **Qué protege**        | El archivo de backup exportado por el usuario                           |
| **Nota de seguridad**  | La contraseña se convierte internamente de `CharArray` a `String` antes de pasarse a `BackupEncryptionManager`. Limitación documentada en la auditoría, pendiente de resolver en v1.1 |

#### Android Keystore — Clave maestra local

Verificado en `KeyStoreKeyProvider.kt`:

| Campo        | Valor                          |
|--------------|--------------------------------|
| Alias        | `xpendz_local_master_key_v1`   |
| Algoritmo    | AES-256-GCM / NoPadding        |
| Proveedor    | AndroidKeyStore                |
| Propósito    | ENCRYPT + DECRYPT              |
| Exportable   | No — la clave nunca sale del Keystore |
| Qué protege  | Operaciones criptográficas de backup en el dispositivo |

#### Base de datos Room (SQLite)

- La base de datos `myfinances.db` se almacena en almacenamiento interno de la app (`/data/data/com.jcadenas.xpendz/databases/`)
- **No está cifrada con SQLCipher** — depende del aislamiento de proceso de Android
- El acceso físico al dispositivo sin root no permite leer la base de datos en condiciones normales

#### Firebase Firestore

- Google cifra los datos en reposo en sus servidores de forma automática

---

## Eliminación de datos

### Flujo verificado en código

**Desde la pantalla Privacidad y datos (`PrivacyAndDataScreen.kt`):**

1. El usuario selecciona "Eliminar todos los datos"
2. Se solicita confirmación explícita antes de proceder
3. Se ejecuta `deleteAllByUser(userUid)` en cada repositorio:
   - Room: `transactionDao.deleteAllByUser()`, `accountDao.deleteAllByUser()`, y equivalentes para todas las entidades
   - Firestore: batch delete de todos los documentos bajo `users/{uid}/transactions/`, `users/{uid}/accounts/`, etc.
4. La configuración de usuario se elimina de Room y de Firestore (`users/{uid}/settings/user`)

**Cuenta de Firebase Authentication:**

- El repositorio `AuthRepository` llama a `firebaseAuth.signOut()` al hacer logout
- La eliminación de la cuenta de Firebase (Authentication) **debe verificarse**: si `FirebaseUser.delete()` se invoca, la cuenta es eliminada también del servidor de Firebase Auth. Verificar en `PrivacyAndDataScreen.kt`

**Datos que persisten tras eliminar datos de la app:**

| Dato                    | Qué ocurre                                                                          |
|-------------------------|-------------------------------------------------------------------------------------|
| SharedPreferences (`device_id`) | Permanece hasta desinstalar la app                                        |
| DataStore (`onboarding_completed`) | Permanece hasta desinstalar la app                                     |
| Clave Android Keystore  | Permanece hasta desinstalar o restaurar de fábrica                                  |
| Archivos de backup exportados | Responsabilidad del usuario — Xpendz no puede eliminarlos                  |
| PDFs compartidos por el usuario | En poder de la app de destino elegida por el usuario                      |

---

## Información para Google Play

Respuestas directas al formulario Data Safety de Google Play Console.

| Pregunta del formulario                                         | Respuesta | Justificación / Fuente                                        |
|-----------------------------------------------------------------|-----------|----------------------------------------------------------------|
| ¿Su app recopila o comparte datos de usuario?                   | **Sí**    | Recopila email/UID via Firebase Auth; datos financieros via Firestore |
| ¿Se cifran los datos en tránsito?                               | **Sí**    | HTTPS/TLS — SDK Firebase. Todas las comunicaciones usan TLS   |
| ¿Puede el usuario solicitar la eliminación de sus datos?        | **Sí**    | `PrivacyAndDataScreen.kt` — elimina datos locales y en Firestore |
| ¿Se comparten datos con terceros?                               | **Sí**    | Firebase Auth y Firestore (Google). Sin otros terceros        |
| ¿Se usan los datos para publicidad?                             | **No**    | Sin AdMob ni ningún SDK de publicidad                          |
| ¿Se usan los datos para analítica?                              | **No**    | Sin Firebase Analytics ni equivalentes                         |
| ¿Se venden datos a terceros?                                    | **No**    | Los datos solo se usan para proveer el servicio al usuario     |
| ¿Son obligatorios los datos recopilados?                        | **Sí**    | Email/UID son obligatorios (sin autenticación la app no opera). Datos financieros son el propósito de la app |
| ¿Se usan los datos para personalización?                        | **No**    | Sin motor de personalización ni ML                             |
| ¿Hay datos financieros recopilados?                             | **Sí**    | Transacciones, cuentas, presupuestos, préstamos — todos introducidos manualmente |
| ¿Hay información personal recopilada?                           | **Sí**    | Correo electrónico y UID de Firebase                           |
| ¿Hay identificadores del dispositivo?                           | **Sí**    | UUID generado localmente (`DeviceIdProvider`) — no vinculado al hardware del dispositivo |
| ¿Se comparte información con Google?                            | **Sí**    | Firebase Auth y Firestore son servicios de Google              |
| ¿Hay recopilación de datos de ubicación?                        | **No**    | Sin permiso de ubicación ni API de localización                |
| ¿Se accede a contactos, SMS, cámara o micrófono?               | **No**    | Sin permisos de acceso a contactos, SMS, cámara ni micrófono  |

---

## Correspondencia con categorías oficiales de Google Play

| Categoría oficial de Google Play         | ¿Aplica? | Dato específico                                              |
|------------------------------------------|----------|--------------------------------------------------------------|
| **Información personal — Nombre**        | Parcial  | Solo si el usuario usa Google Sign-In y Google proporciona `displayName`. No se almacena en Room ni Firestore |
| **Información personal — Dirección de email** | ✅ Sí | Usada en Firebase Authentication. No almacenada en Room      |
| **Información personal — ID de usuario** | ✅ Sí  | UID de Firebase — clave primaria de todos los datos del usuario |
| **Información financiera — Historial de transacciones** | ✅ Sí | Gastos, ingresos, transferencias introducidos por el usuario |
| **Información financiera — Otros datos financieros** | ✅ Sí | Presupuestos, metas, préstamos, cuentas, saldos             |
| **Actividad en la aplicación**           | No       | Sin Firebase Analytics ni seguimiento de comportamiento       |
| **Identificadores — ID del dispositivo** | ✅ Sí   | UUID generado localmente (`DeviceIdProvider`) — enviado a Firestore como `updated_by` |
| **Identificadores — Otros**              | No       | Sin AAID (Advertising ID) ni IMEI ni similares               |
| **Archivos — Archivos del usuario**      | Parcial  | El usuario exporta e importa el backup. Xpendz usa SAF — no accede a archivos de forma autónoma |
| **Mensajes**                             | No       | Sin acceso a SMS, correo ni mensajería                        |
| **Contactos**                            | No       | Sin permiso READ_CONTACTS. Nombres en préstamos son texto libre del usuario |
| **Ubicación**                            | No       | Sin API de ubicación                                          |
| **Información de salud**                 | No       | Sin Health Connect ni equivalentes                            |
| **Información web y de aplicaciones**    | No       | Sin WebView relevante ni seguimiento de navegación            |
| **Fotos y videos**                       | No       | Sin acceso a galería — Coil carga foto de perfil de Google solo en memoria |

---

## Respuestas recomendadas para Play Console

Tabla de referencia para completar el formulario paso a paso.

| Pregunta en Play Console | Respuesta recomendada | Justificación | Fuente en el código |
|--------------------------|----------------------|---------------|---------------------|
| ¿Recopila datos su app? | **Sí** | La app recopila email/UID y datos financieros del usuario | `AuthRepository.kt`, todos los repositorios |
| Información personal — Dirección de correo | **Marcar** | Firebase Authentication la recibe y usa para identificar al usuario | `AuthRepository.kt` |
| Información personal — ID de usuario | **Marcar** | UID de Firebase — usado como clave en toda la arquitectura | `DatabaseModule.kt`, todos los DAOs |
| Información financiera — Historial financiero | **Marcar** | Transacciones, saldos, presupuestos, préstamos | `TransactionRepository.kt` y demás repositorios |
| ¿Con qué propósito se usa el correo electrónico? | **Funcionalidad de la app** | Solo para autenticación — no para marketing ni analítica | `AuthRepository.kt` |
| ¿Con qué propósito se usan los datos financieros? | **Funcionalidad de la app** | Registro personal — no se analiza ni comparte con fines publicitarios | Todos los repositorios |
| ¿Se comparte el correo con terceros? | **Sí — Firebase Authentication (Google)** | Es el proveedor de autenticación | `FirebaseModule.kt` |
| ¿Se comparten los datos financieros con terceros? | **Sí — Firebase Firestore (Google)** | Sincronización en la nube | `FirebaseModule.kt` |
| ¿Los datos se cifran en tránsito? | **Sí** | HTTPS/TLS via SDK Firebase | `PrivacyPolicyScreen.kt` línea 103 |
| ¿El usuario puede solicitar eliminación? | **Sí** | Desde Configuración → Privacidad y datos | `PrivacyAndDataScreen.kt` |
| ¿Se recopila ubicación? | **No** | Sin permiso ni API de ubicación | `AndroidManifest.xml` |
| ¿Se recopilan datos de dispositivo sensibles? | **No** | Sin cámara, micrófono, contactos, SMS | `AndroidManifest.xml` |
| ¿Se usa para publicidad? | **No** | Sin AdMob ni SDK de publicidad | `app/build.gradle.kts` |
| ¿Se usa para personalización? | **No** | Sin ML Kit ni motor de personalización | `app/build.gradle.kts` |
| ¿Se venden datos? | **No** | Los datos solo se usan para proveer el servicio al usuario | — |
| ¿Se recopila ID de publicidad (AAID)? | **No** | Sin AdMob — AAID no se accede | `app/build.gradle.kts` |
| ¿Datos de salud o fitness? | **No** | Sin Health Connect | `app/build.gradle.kts` |
| ¿Identificadores de dispositivo? | **Sí — ID generado por la app** | UUID local usado como `updated_by` en Firestore | `DeviceIdProvider.kt` |
| ¿Es posible la recopilación opcional? | **No** | Email/UID son obligatorios — la app no funciona sin autenticación | `AuthRepository.kt` |

---

## Verificaciones pendientes

Los siguientes puntos solo pueden verificarse desde Firebase Console, no desde el proyecto local:

| Verificación | Dónde comprobar | Importancia |
|---|---|---|
| **Reglas de Firestore Security Rules** | Firebase Console → Firestore → Reglas | Alta — verificar que las reglas restrinjan el acceso a `users/{uid}/**` solo al usuario autenticado con ese UID |
| **Proveedores de autenticación habilitados** | Firebase Console → Authentication → Sign-in method | Alta — verificar que solo estén habilitados Email/Contraseña y Google |
| **OAuth Client ID configurado** | Firebase Console → Authentication → Google Provider | Media — verificar que el `default_web_client_id` en `strings.xml` corresponde al proyecto activo |
| **Ubicación de datos de Firestore** | Firebase Console → Firestore → Ubicación | Media — confirmar región de almacenamiento para declarar en Data Safety |
| **`FirebaseUser.delete()` invocado** | Revisar `PrivacyAndDataScreen.kt` completo | Alta — confirmar si la cuenta de Firebase Authentication también se elimina al borrar datos, o solo se eliminan los datos de Firestore |
| **Retención de datos de Firebase Auth** | Política de Google / Firebase Console | Media — documentar cuánto tiempo Google retiene los registros de auth eliminados |
| **Google Analytics habilitado en Firebase** | Firebase Console → Analytics | Alta — verificar que Analytics esté deshabilitado, dado que no hay dependencia de `firebase-analytics` en Gradle |
| **Crashlytics habilitado** | Firebase Console → Crashlytics | Alta — verificar que Crashlytics esté deshabilitado (sin dependencia en Gradle, pero verificar Console) |

---

## Conclusión

### Estado de cumplimiento para publicación

| Área                             | Estado              | Observaciones                                              |
|----------------------------------|---------------------|------------------------------------------------------------|
| Recopilación declarada           | ✅ Documentada      | Email, UID, datos financieros, UUID de dispositivo         |
| Cifrado en tránsito              | ✅ Cumple           | HTTPS/TLS — Firebase SDK                                   |
| Eliminación de datos             | ✅ Implementado     | Local + Firestore desde la app                             |
| Permisos mínimos                 | ✅ Cumple           | Solo `INTERNET`                                            |
| Sin publicidad                   | ✅ Confirmado       | Sin AdMob ni ningún SDK de ads                             |
| Sin analytics de terceros        | ✅ Confirmado       | Sin Firebase Analytics, Crashlytics ni similares           |
| Política de privacidad en app    | ✅ Implementada     | `PrivacyPolicyScreen.kt` — texto actualizado a Xpendz      |
| URL pública de política          | ❌ Pendiente        | Requerida por Google Play para publicar                    |
| Formulario Data Safety           | ❌ Pendiente        | Completar en Google Play Console usando este documento     |
| Verificación de Firestore Rules  | ⏳ Pendiente        | Solo verificable desde Firebase Console                    |
| Verificación de eliminación de cuenta Firebase Auth | ⏳ Pendiente | Revisar `PrivacyAndDataScreen.kt` en detalle         |
