# Glossary v1.0

**Fecha:** 15 de agosto de 2026  
**Versión:** 1.0

---

## A

### Account
Representación de una fuente de dinero del usuario (banco, efectivo, ahorros, etc.). Cada cuenta tiene moneda, saldo, tipo, icono y color.

### ADR (Architecture Decision Record)
Registro de una decisión arquitectónica importante, incluyendo contexto, alternativas y consecuencias.

### Android Keystore
Almacenamiento seguro del sistema Android para claves criptográficas. Xpendz lo usa para proteger los datos locales.

## B

### Backup
Archivo cifrado que contiene una copia completa de los datos del usuario. Permite exportar, migrar o restaurar información.

### Backup Encryption
Cifrado de respaldos con PBKDF2 y AES-256-GCM, protegido por una contraseña elegida por el usuario.

## C

### Category
Clasificación jerárquica de transacciones. Puede ser ingreso, gasto o ambos, y puede tener subcategorías.

### Compose
Jetpack Compose. Framework declarativo de UI de Android usado en Xpendz.

## D

### DAO (Data Access Object)
Interfaz que define las operaciones de base de datos para una entidad Room.

### Dashboard
Pantalla principal de la aplicación. Muestra saldos, cuentas y resumen mensual.

### Design Token
Valor semántico reusable que define color, espaciado, elevación, shape o tipografía en el sistema de diseño.

## E

### Entity
Clase que representa una tabla en la base de datos Room.

### Event
Acción o cambio que ocurre en la UI y que el ViewModel debe procesar.

## F

### Firebase
Plataforma de Google que Xpendz usa para autenticación (Firebase Auth) y sincronización en la nube (Firestore).

### Firestore
Base de datos NoSQL de Firebase donde Xpendz almacena copias de seguridad y sincroniza entre dispositivos.

### Flow
Concepto de Kotlin para programación reactiva. Room y ViewModels usan Flow para propagar cambios.

## H

### Hilt
Librería de inyección de dependencias de Google, integrada con Android y ViewModel.

## L

### Loan
Registro de dinero prestado o recibido por el usuario. Tiene contraparte, monto, moneda y estado.

### Loan Movement
Evento histórico de un préstamo: creación, pago, recargo, ajuste, cierre.

### Loan Payment
Pago realizado o recibido asociado a un préstamo.

## M

### Manual Sync
Sincronización que el usuario dispara explícitamente, descargando datos de Firestore a Room.

### Module
Unidad funcional del sistema compuesta por screen, ViewModel y repositorio asociado.

### MVVM
Patrón Model-View-ViewModel. Xpendz separa UI, estado y lógica usando ViewModel con StateFlow.

## N

### Navigation
Sistema de rutas que permite moverse entre pantallas. Xpendz usa Jetpack Navigation Compose.

## O

### Offline First
Diseño donde la app funciona sin conexión y sincroniza cuando hay red. Room es la fuente de verdad local.

## R

### Repository
Clase que abstrae el acceso a datos. Cada Repository maneja Room y Firestore para una entidad.

### Room
Librería de persistencia local de Android que usa SQLite. Es la base de datos offline de Xpendz.

## S

### Screen
Composable que representa una pantalla completa de la aplicación.

### State
Snapshot inmutable de datos que la UI observa para recomponerse.

### Sync Engine
Conjunto de lógica que orquesta la descarga de datos desde Firestore hacia Room, respetando dependencias.

## T

### Technical Debt
Compromisos acumulados en el código o arquitectura que dificultan el mantenimiento futuro.

### Transaction
Movimiento financiero del usuario. Puede ser ingreso, gasto o vinculado a un préstamo.

### Transfer
Movimiento de dinero entre dos cuentas del mismo usuario.

## U

### Use Case
Clase que encapsula una operación de negocio compleja. Xpendz tiene pocos UseCases actualmente.

## V

### ViewModel
Clase que sobrevive a cambios de configuración y mantiene el estado de una pantalla.

## X

### XpendzThemeTokens
Sistema de tokens de diseño que centraliza colores, espaciado, elevaciones, shapes y tipografía.
