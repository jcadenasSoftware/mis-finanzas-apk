# Platform Parity
## Xpendz Desktop ↔ Android

**Versión:** 1.1  
**Última actualización:** Agosto 2026

---

# Objetivo

Este documento mantiene el estado oficial de la paridad funcional entre las plataformas Desktop y Android de Xpendz.

Su propósito es identificar:

- funcionalidades equivalentes;
- diferencias entre plataformas;
- ventajas temporales de una plataforma sobre otra;
- oportunidades de convergencia;
- prioridades de evolución.

Este documento evalúa únicamente comportamiento funcional.

No evalúa diseño visual, UX ni calidad gráfica.

---

# Estado general

Capacidades auditadas: **62**

| Estado | Cantidad |
|---------|---------:|
| 🟢 Paridad completa | 31 |
| 🟡 Paridad parcial | 17 |
| 🔴 Exclusivas Desktop | 8 |
| 🔵 Exclusivas Android | 6 |

La paridad funcional del núcleo financiero puede considerarse **muy alta**.

Las diferencias actuales se concentran principalmente en:

- analítica avanzada;
- herramientas de productividad;
- respaldo;
- administración de cuenta;
- funcionalidades específicas de cada plataforma.

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

- La sesión aún no se restaura automáticamente al iniciar la aplicación.

---

# 🟢 Dashboard

## Paridad

- Balance general.
- Resumen mensual.
- Acciones principales.
- Listado de cuentas.
- Acceso a todos los módulos.
- Actualización mediante sincronización.

Actualmente el Dashboard mantiene una paridad funcional muy alta entre ambas plataformas.

---

# 🟢 Cuentas

## Paridad

- Crear cuenta.
- Editar cuenta.
- Eliminar cuenta.
- Visualizar saldo.
- Abrir movimientos de la cuenta.

---

## ✅ Conciliación Inteligente de Saldos (Android)

**Estado:** IMPLEMENTADO

Android incorpora el nuevo flujo oficial de conciliación de saldos de Xpendz.

La conciliación ya no consiste únicamente en calcular una diferencia.

Ahora guía al usuario hasta registrar el ajuste contable correspondiente.

### Flujo

Cuenta

↓

Menú de acciones

↓

Conciliar saldo

↓

Ingresar saldo real

↓

Cálculo automático de diferencia

↓

Registrar ajuste

↓

Nueva Transacción completamente preparada

---

### Características

La pantalla de Nueva Transacción se abre automáticamente con:

- cuenta preseleccionada;
- monto prellenado;
- tipo (Ingreso o Gasto) seleccionado automáticamente según la diferencia;
- fecha preparada;
- proceso completamente trazable mediante una transacción.

El saldo de la cuenta nunca es modificado directamente.

Toda conciliación queda registrada como un movimiento financiero.

---

### Beneficio para el usuario

Antes:

Banco

↓

Calculadora

↓

Calcular diferencia

↓

Abrir Xpendz

↓

Crear transacción manualmente

Ahora:

Banco

↓

Xpendz

↓

Conciliar saldo

↓

Registrar ajuste

La nueva conciliación elimina completamente la necesidad de utilizar una calculadora externa y reduce significativamente el tiempo requerido para ajustar una cuenta.

---

### Estado de la paridad

Actualmente Android implementa la nueva generación del flujo de conciliación.

Desktop continúa utilizando el flujo clásico:

Saldo real

↓

Calcular diferencia

↓

Copiar diferencia

↓

Registrar transacción manualmente

---

### Evolución prevista

La conciliación inteligente implementada inicialmente en Android será adoptada posteriormente por Desktop.

El objetivo es que ambas plataformas compartan exactamente el mismo flujo operativo.

---

## Diferencias actuales

Desktop mantiene aún algunas ventajas:

- conciliación clásica mediante copia de diferencia;
- interfaz más amplia para operaciones de escritorio.

Android ahora lidera este módulo gracias al nuevo flujo inteligente.

---

# 🟢 Transacciones

Paridad prácticamente completa.

Ambas plataformas permiten:

- crear;
- editar;
- eliminar;
- buscar;
- filtrar;
- resumir ingresos, gastos y balances.

Pendiente de evolución:

- filtrado automático de categorías según tipo de transacción (Ingreso/Gasto).

---

# 🟢 Transferencias

Paridad alta.

Desktop incorpora KPIs adicionales:

- total transferido;
- promedio;
- cuenta más utilizada.

Android cubre completamente el flujo operativo.

---

# 🟢 Categorías

Paridad muy alta.

Ambas plataformas permiten:

- categorías;
- subcategorías;
- edición;
- eliminación;
- clasificación;
- búsqueda.

Desktop posee un selector visual de iconos más amplio.

Android incorpora insights mensuales.

---

# 🟢 Presupuesto

Paridad muy alta.

Desktop posee una tarjeta de resumen más rica.

Android ofrece la misma funcionalidad financiera principal.

---

# 🟢 Metas

Paridad parcial.

Desktop incorpora:

- edición;
- eliminación;
- proyecciones;
- alertas.

Android actualmente se centra en:

- creación;
- depósitos;
- retiros;
- progreso.

---

# 🟢 Préstamos

Paridad muy alta.

Ambas plataformas soportan:

- préstamos otorgados;
- préstamos recibidos;
- pagos;
- historial;
- edición;
- TOP-UP;
- cierre automático.

Desktop mantiene una vista global de actividad del módulo.

---

# 🟢 Resumen financiero

Desktop mantiene ventajas importantes:

- Heatmap financiero.
- Exportaciones contextuales.
- KPIs avanzados.

Android ofrece una experiencia optimizada para dispositivos móviles.

---

# 🟢 Reportes

Paridad alta.

Desktop incorpora:

- CSV.
- Exportaciones contextualizadas.

Android prioriza compartir rápidamente los documentos generados.

---

# 🟢 Sincronización

Paridad alta.

Android incorpora:

- progreso detallado;
- cancelación.

Desktop incorpora:

- sincronización previa al cierre.

---

# 🟢 Backup

Android lidera completamente este módulo.

Incluye:

- respaldo cifrado;
- restauración;
- validación mediante contraseña.

Desktop aún no incorpora estas funcionalidades.

---

# 🟢 Configuración

Android posee una cobertura superior.

Incluye:

- privacidad;
- eliminación de cuenta;
- país;
- moneda;
- recuperación de contraseña.

Desktop mantiene únicamente la configuración básica.

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

1. Conciliación inteligente de saldos.
2. Backup cifrado.
3. Restore.
4. Privacidad y datos.
5. Persistencia automática de sesión.
6. Recuperación de contraseña.
7. Configuración de país y moneda.

---

# Conclusiones

## Fortalezas Desktop

- Analítica avanzada.
- Heatmap.
- KPIs.
- Exportaciones.
- Gestión avanzada de metas.

---

## Fortalezas Android

- Backup cifrado.
- Restore.
- Privacidad.
- Eliminación de cuenta.
- Recuperación de contraseña.
- Conciliación inteligente de saldos.

---

# Estado del proyecto

El núcleo financiero de Xpendz presenta un nivel de paridad funcional elevado entre Desktop y Android.

Las diferencias actuales ya no corresponden a funcionalidades esenciales, sino a herramientas avanzadas propias de la evolución de cada plataforma.

La implementación de la Conciliación Inteligente de Saldos marca el inicio de una nueva filosofía de interacción en Xpendz: reducir pasos, automatizar decisiones repetitivas y convertir los procesos contables en flujos guiados.

Las futuras funcionalidades deberán diseñarse siguiendo este mismo principio para mantener una experiencia consistente entre ambas plataformas.