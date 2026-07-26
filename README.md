 # Xpendz
 
 App Android de finanzas personales hecha con Kotlin y Jetpack Compose: cuentas, transacciones (ingresos/gastos), transferencias y reportes, con persistencia local en Room y sincronización bidireccional con Firebase Firestore. Incluye autenticación con Google Sign-In.
 
 Aplicación Android para **gestión de finanzas personales**: cuentas, categorías, ingresos/gastos y transferencias, con **UI en Jetpack Compose**, **persistencia local con Room** y **sincronización bidireccional con Firebase Firestore**.

## Funcionalidades
- **Autenticación**: Google Sign-In (Firebase Authentication).
- **Cuentas**: crear, renombrar, eliminar, y ver saldos.
- **Transacciones**: registro de ingresos y gastos con categorías.
- **Transferencias**: movimientos entre cuentas.
- **Dashboard**: balance total y acceso rápido a módulos.
- **Reportes/consultas**: agregados por mes/categoría (base para gráficas).
- **Préstamos**: registro y gestión básica de préstamos.
- **Presupuesto**: presupuesto mensual por categoría y subcategoría, con detalle por subcategoría.
- **Metas**: creación de metas y movimientos entre cuentas y metas.
- **Sync**: sincronización **bidireccional** con Firestore (trabajo offline + reconciliación al volver a conectar).

## Stack
- **Kotlin**
- **Jetpack Compose** + Navigation Compose
- **Room** (DAO/Entities/Migrations)
- **Coroutines / Flow**
- **Firebase**: Auth + Firestore
- **Dagger Hilt** (DI)

## Arquitectura (alto nivel)
- **Single-Activity** (Compose)
- **MVVM**: `ViewModel` + `StateFlow`
- **Repositorios** para separar lógica de datos (Room/Firestore/Auth)
- **DI con Hilt** para proveer DB, DAOs y servicios

## Requisitos
- **Android Studio** (recomendado: última versión estable)
- **JDK 21**
- SDK Android instalado (el proyecto compila con `compileSdk = 35`)

## Configuración de Firebase
Este proyecto requiere configuración propia de Firebase:

1. Crear un proyecto en **Firebase Console**.
2. Registrar una app Android con el `applicationId`:

   - `com.myfinances`

3. Descargar `google-services.json` y colocarlo en:

   - `app/google-services.json`

4. En Firebase Authentication:
   - Habilitar **Google** como proveedor de inicio de sesión.

5. En Firestore:
   - Crear la base de datos y ajustar reglas/índices según tus necesidades.

## Ejecutar el proyecto
1. Abrir el proyecto en Android Studio.
2. Sincronizar Gradle (`Sync Project with Gradle Files`).
3. Ejecutar en un emulador o dispositivo físico:
   - `Run 'app'`

## Notas
- La sincronización con Firestore está diseñada para permitir uso offline y sincronizar cambios al reconectar.
- Los presupuestos se guardan por mes, moneda y categoría (incluye subcategorías).

## Licencia
Define aquí la licencia que prefieras (MIT, Apache-2.0, etc.).
