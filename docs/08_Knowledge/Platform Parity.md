# Platform Parity
## Xpendz Desktop ↔ Android

**Versión:** 1.2  
**Última actualización:** Agosto 2026

---

# Objetivo

Este documento mantiene el estado oficial de la paridad funcional entre las plataformas Desktop y Android de Xpendz.

Su propósito es servir como la referencia principal para la evolución conjunta de ambas plataformas.

Permite identificar:

- funcionalidades equivalentes;
- diferencias funcionales;
- ventajas temporales de una plataforma;
- oportunidades de convergencia;
- prioridades de desarrollo.

Este documento evalúa únicamente comportamiento funcional.

No evalúa:

- diseño visual;
- UX;
- temas Light/Dark;
- calidad gráfica.

---

# Filosofía de Paridad

La paridad entre Desktop y Android **no significa que ambas plataformas deban ser idénticas**.

Cada plataforma debe aprovechar sus fortalezas naturales.

Desktop dispone de:

- monitor amplio;
- mouse;
- teclado;
- multitarea.

Android dispone de:

- interacción táctil;
- movilidad;
- cámara;
- compartir información;
- notificaciones.

El objetivo de Xpendz es que el usuario pueda realizar las mismas tareas financieras con un esfuerzo equivalente, aunque la interacción sea distinta.

Cuando una plataforma descubra un flujo claramente superior, éste pasará a convertirse en el nuevo estándar del producto y posteriormente será adoptado por la otra plataforma.

---

# Estado general

Capacidades auditadas: **62**

| Estado | Cantidad |
|---------|---------:|
| 🟢 Paridad completa | 31 |
| 🟡 Paridad parcial | 17 |
| 🔴 Exclusivas Desktop | 8 |
| 🔵 Exclusivas Android | 6 |

El núcleo financiero de Xpendz presenta actualmente un nivel de paridad muy alto.

Las diferencias restantes corresponden principalmente a herramientas avanzadas de productividad, analítica y administración del sistema.

---

# Nivel de madurez por módulo

| Módulo | Nivel |
|---------|:-----:|
| Dashboard | ⭐⭐⭐⭐⭐ |
| Cuentas | ⭐⭐⭐⭐⭐ |
| Transacciones | ⭐⭐⭐⭐⭐ |
| Transferencias | ⭐⭐⭐⭐⭐ |
| Categorías | ⭐⭐⭐⭐⭐ |
| Presupuesto | ⭐⭐⭐⭐⭐ |
| Préstamos | ⭐⭐⭐⭐⭐ |
| Reportes | ⭐⭐⭐⭐☆ |
| Metas | ⭐⭐⭐⭐☆ |
| Sincronización | ⭐⭐⭐⭐☆ |
| Configuración | ⭐⭐⭐⭐☆ |
| Backup | ⭐⭐⭐☆☆ |

---

# Módulos auditados

- Autenticación
- Dashboard
- Cuentas
- Transacciones
- Transferencias
- Categorías
- Presupuesto
- Metas
- Préstamos
- Resumen financiero
- Reportes
- Sincronización
- Backup
- Configuración

---

# Estado por módulo

---

# 🟢 Autenticación

## Paridad

- Login
- Registro
- Google Sign-In
- Logout

## Diferencias

### Android

- Recuperación de contraseña.
- Persistencia automática de sesión mediante Firebase.

### Desktop

- La sesión aún no se restaura automáticamente al iniciar.

---

# 🟢 Dashboard

## Paridad

- Balance general.
- Resumen mensual.
- Acciones principales.
- Listado de cuentas.
- Acceso a todos los módulos.
- Actualización mediante sincronización.

Actualmente el Dashboard posee una paridad funcional prácticamente completa.

---

# 🟢 Cuentas

## Paridad

- Crear cuenta.
- Editar cuenta.
- Eliminar cuenta.
- Visualizar saldo.
- Abrir movimientos.

---

# Conciliación Inteligente de Saldos

**Estado**

✅ Implementado en Android.

---

## Objetivo

Eliminar completamente la necesidad de utilizar una calculadora externa para conciliar los saldos de una cuenta.

---

## Flujo

```text
Cuenta
      │
      ▼
Conciliar saldo
      │
      ▼
Ingresar saldo real
      │
      ▼
Calcular diferencia
      │
 ┌────┴────┐
 │         │
Mayor      Menor
saldo      saldo
 │         │
Ingreso    Gasto
 │         │
Nueva Transacción
preconfigurada
 │
 ▼
Guardar
```

---

## Características

La conciliación:

- calcula automáticamente la diferencia;
- determina automáticamente si corresponde un ingreso o un gasto;
- abre Nueva Transacción;
- selecciona automáticamente la cuenta;
- diligencia automáticamente el monto;
- mantiene trazabilidad completa mediante una transacción.

El saldo nunca se modifica directamente.

---

## Beneficio

Antes

Banco

↓

Calculadora

↓

Xpendz

↓

Registrar transacción

Ahora

Banco

↓

Xpendz

↓

Conciliar saldo

↓

Registrar ajuste

El número de pasos disminuye considerablemente y desaparece la dependencia de aplicaciones externas.

---

## Estado de convergencia

Android implementa la primera versión del nuevo flujo de conciliación inteligente.

Desktop mantiene temporalmente el flujo tradicional:

Saldo real

↓

Calcular diferencia

↓

Copiar diferencia

↓

Registrar transacción

---

## Próxima evolución

Desktop migrará al mismo flujo implementado en Android.

El mecanismo "Copiar diferencia" será reemplazado por una apertura automática de Nueva Transacción ya preparada para registrar el ajuste.

De esta forma ambas plataformas compartirán exactamente el mismo comportamiento.

---

# 🟢 Transacciones

Paridad prácticamente completa.

Pendiente:

- filtrado inteligente de categorías según el tipo de transacción (Ingreso / Gasto).

---

# 🟢 Transferencias

Paridad muy alta.

Desktop mantiene KPIs adicionales.

Android cubre completamente el flujo operativo.

---

# 🟢 Categorías

Paridad muy alta.

Desktop ofrece un selector visual más amplio.

Android incorpora insights mensuales.

---

# 🟢 Presupuesto

Paridad muy alta.

Desktop posee una tarjeta resumen más rica.

Android mantiene la misma funcionalidad financiera.

---

# 🟢 Metas

Paridad parcial.

Desktop dispone de:

- edición;
- eliminación;
- proyecciones;
- alertas.

Android cubre:

- creación;
- depósitos;
- retiros;
- seguimiento.

---

# 🟢 Préstamos

Paridad muy alta.

Desktop mantiene una vista global de actividad.

Android alcanza prácticamente la misma cobertura funcional.

---

# 🟢 Resumen financiero

Desktop mantiene ventajas analíticas:

- Heatmap.
- KPIs.
- Exportaciones contextuales.

Android ofrece una experiencia optimizada para dispositivos móviles.

---

# 🟢 Reportes

Paridad alta.

Desktop incorpora:

- CSV.
- Exportaciones avanzadas.

Android prioriza compartir rápidamente los documentos.

---

# 🟢 Sincronización

Paridad alta.

Android:

- progreso;
- cancelación.

Desktop:

- sincronización previa al cierre.

---

# 🟢 Backup

Android lidera este módulo.

Incluye:

- respaldo cifrado;
- restauración;
- validación por contraseña.

Desktop permanece pendiente.

---

# 🟢 Configuración

Android dispone de mayor cobertura:

- privacidad;
- país;
- moneda;
- recuperación de contraseña;
- eliminación de cuenta.

---

# Prioridades Android

1. Filtrado inteligente de categorías.
2. Edición de metas.
3. Eliminación de metas.
4. Heatmap financiero.
5. Exportación CSV.
6. KPIs avanzados.
7. Actividad global de préstamos.

---

# Prioridades Desktop

1. Adoptar el flujo de Conciliación Inteligente desarrollado para Android.
2. Backup cifrado.
3. Restore.
4. Privacidad y datos.
5. Persistencia automática de sesión.
6. Recuperación de contraseña.
7. Configuración de país y moneda.

---

# Historial de convergencia

## Agosto 2026

✔ Dark Mode completado.

✔ Product Polish.

✔ Light Mode Calibration.

✔ Conciliación Inteligente de Saldos (Android).

✔ Préstamos con paridad funcional.

✔ Presupuesto con paridad funcional.

✔ Transacciones con paridad funcional.

---

# Fortalezas por plataforma

## Desktop

- Analítica avanzada.
- Heatmap.
- KPIs.
- Exportaciones.
- Gestión avanzada de metas.

---

## Android

- Backup cifrado.
- Restore.
- Privacidad.
- Recuperación de contraseña.
- Eliminación de cuenta.
- Conciliación Inteligente de Saldos.

---

# Estado del proyecto

La paridad funcional entre Desktop y Android ha alcanzado un nivel de madurez elevado.

Las diferencias restantes ya no corresponden al núcleo financiero del producto, sino principalmente a herramientas avanzadas y capacidades específicas de cada plataforma.

A partir de este punto, la evolución de Xpendz seguirá un nuevo principio:

> **Las nuevas funcionalidades deberán diseñarse primero como una mejora de la experiencia del usuario y, posteriormente, converger hacia ambas plataformas.**

La Conciliación Inteligente de Saldos constituye el primer ejemplo de esta nueva filosofía: Android introdujo un flujo más eficiente que posteriormente será adoptado también por Desktop como estándar del producto.