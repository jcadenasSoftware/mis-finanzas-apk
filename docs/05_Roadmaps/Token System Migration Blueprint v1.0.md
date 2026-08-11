# Token System Migration Blueprint v1.0

**Fecha:** 11 de agosto de 2026  
**Versión:** 1.0  
**Tipo:** Blueprint técnico de migración  
**Estado:** Activo  
**Documentos base:** [Visual Audit v1.0](../01_Audits/Visual/Visual%20Audit%20v1.0.md) · [Xpendz Design System v1.0](../02_DesignSystem/Xpendz%20Design%20System%20v1.0.md) · [Design Tokens Specification v1.0](../02_DesignSystem/Design%20Tokens%20Specification%20v1.0.md)

---

## 1. Objetivo

### 1.1 Qué pretende lograr la migración
La migración pretende transformar el sistema visual actual de Xpendz Android en un sistema gobernado por Design Tokens, sin romper el comportamiento del producto ni introducir una reescritura global.

El objetivo técnico es que la interfaz deje de depender de decisiones visuales dispersas y pase a depender de una arquitectura de tokens centralizada, trazable y escalable.

### 1.2 Resultados esperados
Al finalizar la migración, el proyecto deberá tener:
- una fuente de verdad visual claramente definida;
- roles semánticos consistentes para color, superficies, elevación, shape, spacing, tipografía, motion y estados;
- componentes compartidos alineados con el sistema de tokens;
- pantallas de alto impacto desacopladas de valores locales repetidos;
- un comportamiento coherente entre modo claro, modo oscuro y estados del sistema.

### 1.3 Qué queda fuera
Esta migración no pretende:
- rediseñar la identidad visual del producto;
- cambiar decisiones de UX ya aprobadas;
- rehacer la navegación;
- introducir nuevas features;
- rediseñar componentes por preferencia estética;
- reemplazar Material 3;
- resolver toda la deuda técnica no relacionada con el sistema visual.

---

## 2. Estado actual

### 2.1 Resumen del sistema existente
El proyecto ya dispone de una base funcional importante:
- un punto de entrada único del theme en `MainActivity`;
- un `XpendzTheme` central que conecta color, shapes y typography;
- una adopción extensa de `MaterialTheme` y componentes de Material 3;
- varios componentes compartidos reutilizables en `ui/components`;
- una identidad visual reconocible y relativamente consistente.

### 2.2 Activos que se reutilizarán
Las siguientes piezas deben considerarse base de reutilización y no punto de destrucción:

| Activo actual | Rol actual | Rol futuro dentro de la migración |
|---|---|---|
| `MainActivity.kt` | Punto de entrada Compose | Ancla estable del sistema de theme |
| `ui/theme/Theme.kt` | Composición actual del theme | Orquestador de mapeo entre tokens y Material 3 |
| `ui/theme/Color.kt` | Tokens visuales parciales | Punto inicial de transición hacia color tokens estructurados |
| `ui/theme/Shape.kt` | Escala actual de shapes | Base para shape tokens oficiales |
| `ui/theme/Type.kt` | Jerarquía tipográfica actual | Base para typography tokens oficiales |
| `ui/components/` | Componentes compartidos existentes | Primer nivel de consumo controlado de tokens |
| `AppNavHost.kt` | Shell de navegación y barra inferior | Punto crítico de validación transversal |
| Material 3 | Framework base | Capa de soporte, no objetivo de reemplazo |

### 2.3 Partes que deberán desaparecer
No se trata principalmente de eliminar archivos, sino de eliminar patrones de deuda.

Deberán desaparecer progresivamente:
- valores visuales hardcodeados en pantallas y componentes;
- decisiones locales de spacing, elevación, shape y tipografía fuera del sistema;
- duplicación de reglas visuales equivalentes entre pantallas;
- paletas paralelas de color con semántica no controlada;
- responsabilidad visual distribuida en screens cuando debe vivir en capas compartidas;
- uso de `colors.xml` heredado como fuente principal de verdad visual del producto;
- variantes ad hoc creadas para resolver un caso puntual sin pasar por el sistema.

### 2.4 Partes que seguirán existiendo después de la migración
Seguirán existiendo, pero con responsabilidad distinta:
- los archivos del theme;
- los recursos XML necesarios para bootstrap, splash o integración del sistema Android;
- componentes compartidos;
- pantallas específicas de negocio.

La diferencia será que dejarán de ser dueños de reglas visuales locales no gobernadas.

---

## 3. Estrategia general

### 3.1 Por qué la migración será incremental
La migración debe ser incremental porque el sistema actual ya funciona, ya tiene adopción amplia de Material 3 y ya posee componentes en producción. Una reescritura total introduciría riesgo innecesario en:
- estabilidad visual;
- regresiones de modo oscuro;
- consistencia entre features;
- capacidad de validación;
- tiempo de recuperación ante fallos.

### 3.2 Principio rector de ejecución
La estrategia oficial es:

> primero estabilizar fundaciones, luego consolidar consumo compartido y por último migrar features de negocio.

### 3.3 Motivos para evitar una reescritura
Una reescritura completa:
- mezclaría demasiadas decisiones en un único cambio;
- dificultaría aislar regresiones;
- impediría validar cada familia de tokens por separado;
- obligaría a resolver diseño, arquitectura y rollout al mismo tiempo;
- aumentaría el costo de rollback.

### 3.4 Modelo de migración
La migración seguirá este modelo:
1. inventario y mapeo;
2. fundaciones del sistema;
3. adopción en capa compartida;
4. migración por shell del producto;
5. migración por features prioritarias;
6. cierre de deuda y endurecimiento.

### 3.5 Regla de seguridad
Ninguna fase debe mezclar simultáneamente:
- nuevos tokens estructurales;
- cambios amplios de componentes compartidos;
- migración masiva de pantallas.

Cada fase debe tener un radio de impacto acotado y validable.

---

## 4. Dependencias entre archivos

### 4.1 Núcleo actual del sistema visual

| Archivo o grupo | Rol actual | Dependencias principales | Impacto en migración |
|---|---|---|---|
| `app/src/main/java/com/myfinances/MainActivity.kt` | Entrada Compose | `XpendzTheme`, `AppNavHost` | Alto como punto de arranque, bajo como volumen de cambio |
| `app/src/main/java/com/myfinances/ui/theme/Theme.kt` | Orquestación de `MaterialTheme` | `Color.kt`, `Shape.kt`, `Type.kt`, sistema Android | Crítico |
| `app/src/main/java/com/myfinances/ui/theme/Color.kt` | Colores actuales parciales | `Theme.kt`, screens, components | Crítico |
| `app/src/main/java/com/myfinances/ui/theme/Shape.kt` | Jerarquía de radios actual | `Theme.kt`, components, screens | Crítico |
| `app/src/main/java/com/myfinances/ui/theme/Type.kt` | Jerarquía tipográfica actual | `Theme.kt`, screens, components | Crítico |
| `app/src/main/res/values/colors.xml` | Paleta XML heredada | `themes.xml`, splash, iconografía del sistema | Medio |
| `app/src/main/res/values/themes.xml` | Theme base Android | `colors.xml`, splash, sistema Android | Alto para compatibilidad, medio para tokens |
| `app/src/main/res/values-night/themes.xml` | Variante nocturna base Android | `colors.xml`, theme base | Alto para validación de dark theme |

### 4.2 Archivos actualmente ausentes pero previsibles en la migración
Los siguientes archivos no existen hoy, pero el blueprint los considera candidatos naturales para futuras fases de implementación:
- `Spacing.kt`
- `Elevation.kt`
- capa de alias o mapping de tokens
- documentación de contratos de consumo por componentes

Estos no deben introducirse al inicio si todavía no existe consenso sobre fundaciones y naming.

### 4.3 Shell y navegación

| Archivo | Dependencias visuales | Riesgo |
|---|---|---|
| `ui/navigation/AppNavHost.kt` | `MaterialTheme`, bottom navigation, shape local, typography local, colores de selección | Alto, porque atraviesa toda la aplicación |
| `ui/components/CompactHeader.kt` | `MaterialTheme`, spacing y layout compartido | Medio |
| `ui/components/HamburgerMenu.kt` | `MaterialTheme`, shapes locales, estados activos | Alto |
| `ui/components/HamburgerMenuButton.kt` | `MaterialTheme` | Bajo |
| `ui/components/SyncSwipeRefresh.kt` | Wrapper transversal de interacción | Medio |

### 4.4 Componentes compartidos reutilizables

| Archivo | Dependencias visuales | Observación para migración |
|---|---|---|
| `ui/components/SettingsSection.kt` | Shape, surface, divider, spacing | Buen candidato para migración temprana |
| `ui/components/SettingsRow.kt` | Typography, icon color, spacing | Buen candidato para migración temprana |
| `ui/components/SectionHeader.kt` | Typography, color | Bajo riesgo |
| `ui/components/SyncStatusChip.kt` | Surface, label, state semantics | Relevante para state tokens |
| `ui/components/UserAccountHeader.kt` | Shape local, card, avatar, sync chip | Medio-alto |
| `ui/components/AccountCard.kt` | Hardcodes de color, elevation, spacing | Alto |
| `ui/components/AddAccountDialog.kt` | Surface, spacing, input patterns, selección visual | Alto |

### 4.5 Pantallas de alto impacto por volumen de deuda visual

| Pantalla | Motivo de prioridad | Dependencias |
|---|---|---|
| `ui/screens/dashboard/DashboardScreen.kt` | Alta densidad de UI local, módulos privados, mezcla de theme con valores locales | Theme, shared components, navegación, acciones financieras |
| `ui/screens/login/LoginScreen.kt` | Hardcodes visibles de color, spacing y elevación; entry point del usuario | Theme, recursos, formularios |
| `ui/screens/onboarding/OnboardingScreen.kt` | Identidad de entrada con gradientes y shapes locales | Theme parcial, motion, brand semantics |
| `ui/screens/transactions/AddTransactionScreen.kt` | Text styles locales, formularios y selección | Typography, spacing, inputs, state tokens |
| `ui/screens/transfers/*` | Semántica de transferencias, listas y formularios | Information tokens, state tokens, inputs |
| `ui/screens/loans/LoansScreen.kt` | Densidad funcional alta y múltiples estados | Semantic tokens, forms, cards, feedback |

### 4.6 Pantallas de estabilización progresiva
Las siguientes pantallas también participan, pero pueden entrar después del núcleo y del shell:
- `BudgetScreen.kt`
- `CategoriesScreen.kt`
- `ChartsScreen.kt`
- `ReportsScreen.kt`
- `SettingsScreen.kt`
- `BackupSettingsScreen.kt`
- `PrivacyAndDataScreen.kt`
- `PrivacyPolicyScreen.kt`
- `TransactionsScreen.kt`

### 4.7 Dependencia lógica del sistema
La relación funcional actual puede resumirse así:

1. `MainActivity` monta `XpendzTheme`.  
2. `XpendzTheme` compone `MaterialTheme` a partir de `Theme.kt`.  
3. `Theme.kt` depende de `Color.kt`, `Shape.kt` y `Type.kt`.  
4. `AppNavHost.kt` y los componentes compartidos consumen ese theme.  
5. Las screens consumen theme y componentes, pero además introducen deuda local.  
6. La migración debe cortar esa deuda desde las fundaciones hacia arriba, no al revés.

---

## 5. Orden oficial de migración

### Fase 0 — Baseline y freeze operativo
**Objetivo:** fijar un punto de referencia antes de tocar la capa visual compartida.

**Incluye:**
- inventario final de archivos afectados;
- identificación de pantallas y componentes prioritarios;
- baseline de capturas y validación manual de claro/oscuro;
- congelar cambios visuales no relacionados con tokens mientras dure la migración.

**Justificación:** sin baseline no habrá forma confiable de distinguir mejora de regresión.

---

### Fase 1 — Mapeo de deuda visual actual
**Objetivo:** traducir hardcodes y patrones repetidos a grupos semánticos migrables.

**Incluye:**
- clasificar colores, shapes, spacing, elevations y text styles repetidos;
- detectar duplicaciones por intención;
- separar deuda de fundación, deuda de componentes y deuda de pantallas.

**Justificación:** no se puede migrar bien lo que todavía no está clasificado.

---

### Fase 2 — Fundaciones del sistema
**Objetivo:** consolidar la estructura oficial de familias y niveles de tokens.

**Incluye:**
- formalización de la capa de color semántico;
- formalización de surfaces;
- formalización de elevation;
- formalización de shape;
- formalización de spacing;
- formalización de typography;
- definición de state y motion como familias transversales.

**Justificación:** todas las fases posteriores dependen de esta base.

---

### Fase 3 — Capa de mapping con Material 3
**Objetivo:** definir cómo los tokens del producto abastecen a `MaterialTheme` y a los slots equivalentes del sistema.

**Incluye:**
- mapeo de color roles;
- mapeo de typography;
- mapeo de shapes;
- contrato de convivencia con theme XML heredado cuando siga siendo necesario.

**Justificación:** el sistema de tokens debe entrar por el theme central antes de llegar a features.

---

### Fase 4 — Recursos base y compatibilidad Android
**Objetivo:** alinear la capa XML heredada con el nuevo sistema sin romper bootstrap, splash ni comportamiento del sistema.

**Incluye:**
- revisión de `colors.xml` como capa de compatibilidad;
- revisión de `themes.xml` y `values-night/themes.xml`;
- definición de qué recursos XML permanecen, cuáles pasan a ser derivados y cuáles dejan de ser fuente principal de verdad.

**Justificación:** la app no vive solo dentro de Compose; hay dependencias del sistema Android que deben permanecer estables.

---

### Fase 5 — Componentes shared de baja complejidad
**Objetivo:** migrar primero componentes simples, altamente reutilizados y de bajo riesgo.

**Incluye:**
- encabezados;
- rows;
- headers de sección;
- chips simples;
- wrappers de feedback con bajo acoplamiento visual.

**Justificación:** permiten validar naming y consumo sin tocar todavía flujos complejos.

---

### Fase 6 — Shell del producto
**Objetivo:** migrar navegación y componentes transversales que estructuran la experiencia.

**Incluye:**
- `AppNavHost.kt`;
- barra inferior;
- `CompactHeader.kt`;
- `HamburgerMenu.kt`;
- piezas de navegación asociadas.

**Justificación:** estabilizar el shell primero reduce inconsistencias en todas las features.

---

### Fase 7 — Formularios y estados compartidos
**Objetivo:** migrar patrones de input, overlays y estados que se repiten en múltiples pantallas.

**Incluye:**
- dialogs;
- sheets;
- inputs;
- selección;
- errores inline;
- loading y feedback.

**Justificación:** muchas pantallas complejas dependen antes de esto que de cambios de layout profundo.

---

### Fase 8 — Pantallas de mayor impacto visual
**Objetivo:** atacar los módulos con mayor densidad de deuda y mayor exposición al usuario.

**Orden recomendado dentro de la fase:**
1. `LoginScreen.kt`
2. `OnboardingScreen.kt`
3. `DashboardScreen.kt`
4. `SettingsScreen.kt`

**Justificación:**
- login y onboarding concentran identidad y hardcodes visibles;
- dashboard concentra la mayor complejidad local;
- settings valida componentes compartidos de manera transversal.

---

### Fase 9 — Features financieras de complejidad media y alta
**Objetivo:** consolidar tokens en dominios de trabajo intensivo.

**Orden recomendado dentro de la fase:**
1. transacciones;
2. transferencias;
3. categorías;
4. presupuesto;
5. préstamos;
6. gráficas y reportes.

**Justificación:** estas áreas dependen fuertemente de estados, semántica financiera y patrones de datos complejos.

---

### Fase 10 — Cierre de deuda residual
**Objetivo:** eliminar restos de estilos locales que hayan sobrevivido a las fases anteriores.

**Incluye:**
- hardcodes remanentes;
- variantes duplicadas;
- naming ambiguo;
- inconsistencias entre claro y oscuro;
- residuos de semántica previa al sistema de tokens.

**Justificación:** la migración no se considera completa mientras el sistema conviva con excepciones relevantes no justificadas.

---

### Fase 11 — Endurecimiento final
**Objetivo:** declarar el sistema listo para mantenimiento regular.

**Incluye:**
- validación transversal;
- cierre documental;
- checklist final;
- decisión de cierre de migración.

**Justificación:** el final de la migración debe ser una decisión explícita, no una sensación subjetiva.

---

## 6. Riesgos técnicos

### 6.1 Matriz por fase

| Fase | Qué puede romperse | Impacto | Probabilidad | Estrategia de mitigación |
|---|---|---|---|---|
| **Fase 0** | Baseline incompleto | Alto | Medio | Capturas y checklist previos antes de cambiar cualquier fundación |
| **Fase 1** | Clasificación semántica incorrecta | Alto | Medio | Revisar con documentos oficiales y validar grupos antes de tocar theme |
| **Fase 2** | Tokens demasiado abstractos o demasiado específicos | Alto | Alto | Revisiones cruzadas de naming y responsabilidad antes de adopción |
| **Fase 3** | Mapeo incorrecto a Material 3 | Alto | Medio | Probar theme central antes de migrar components y screens |
| **Fase 4** | Rupturas en splash, status bar o modo oscuro base | Medio-Alto | Medio | Validar recursos XML y arranque nativo por separado |
| **Fase 5** | Variantes de componentes simples no cubiertas | Medio | Medio | Migrar primero piezas con comportamiento acotado y validar reuso |
| **Fase 6** | Regresiones globales de navegación o shell | Muy alto | Medio | Desplegar shell en fase propia y validar toda navegación top-level |
| **Fase 7** | Inconsistencia en formularios y overlays | Alto | Alto | Validar flujos de entrada completos antes de tocar screens críticas |
| **Fase 8** | Pérdida de consistencia visual o branding percibido | Alto | Medio | Migrar una pantalla crítica por vez y comparar contra baseline |
| **Fase 9** | Errores de semántica financiera en estados complejos | Muy alto | Medio | Revisar dominio por dominio y validar estados con datos reales |
| **Fase 10** | Dejar residuos difíciles de rastrear | Medio | Alto | Ejecutar búsquedas sistemáticas de deuda residual y documentar excepciones |
| **Fase 11** | Cierre prematuro con deuda aún activa | Alto | Medio | Exigir checklist completo y aprobación explícita de cierre |

### 6.2 Riesgos transversales
- **Drift entre documentos y código:** la implementación puede desviarse del diseño aprobado.
- **Acumulación de alias innecesarios:** un sistema de tokens mal contenido puede crecer en complejidad sin resolver deuda real.
- **Regresiones de dark theme:** especialmente en pantallas con muchos hardcodes previos.
- **Estados ambiguos:** selected, active, focused, warning y error pueden cruzarse mal si no se modelan transversalmente.
- **Shell inconsistente:** cualquier fallo en navegación o componentes globales impacta toda la percepción del producto.

### 6.3 Riesgos de proceso
- ejecutar varias fases en paralelo sin que la fundación esté estable;
- mezclar trabajo de tokens con cambios funcionales;
- aceptar excepciones temporales sin fecha ni criterio de retiro;
- avanzar de fase sin criterios cerrados.

---

## 7. Estrategia de validación

### 7.1 Principio general
Toda fase debe validarse en seis dimensiones mínimas:
- compilación;
- tema claro;
- tema oscuro;
- accesibilidad;
- consistencia visual;
- comportamiento funcional.

### 7.2 Validación de compilación
Debe comprobarse que:
- el proyecto compila sin errores;
- no aparecen referencias rotas de theme;
- los componentes compartidos continúan resolviendo sus dependencias;
- no quedan imports o rutas de consumo a capas obsoletas si la fase declaraba su retiro.

### 7.3 Validación de modo claro
Debe comprobarse que:
- la jerarquía entre fondos, superficies y contenido sigue siendo clara;
- acciones primarias y secundarias conservan su prioridad;
- los importes y métricas siguen siendo legibles;
- no aparecen superficies “planas” o jerarquías colapsadas.

### 7.4 Validación de modo oscuro
Debe comprobarse que:
- la interfaz no se convierte en una simple inversión del modo claro;
- las superficies siguen distinguiéndose;
- los estados financieros conservan significado;
- textos, cifras y acciones mantienen legibilidad real;
- no aparecen brillos, contrastes violentos o zonas sobrecargadas.

### 7.5 Validación de accesibilidad
Debe comprobarse que:
- los estados no dependen solo del color;
- el foco sea distinguible;
- la jerarquía tipográfica siga siendo comprensible;
- los controles mantengan claridad y activación fiable;
- los formularios conserven feedback comprensible.

### 7.6 Validación de consistencia visual
Debe comprobarse que:
- componentes equivalentes se vean y comporten de forma equivalente;
- no reaparezcan variantes locales del mismo patrón;
- la semántica financiera sea estable en todas las features afectadas por la fase;
- la jerarquía del sistema sea reconocible sin leer el código.

### 7.7 Validación de navegación
Debe comprobarse que:
- la barra inferior mantiene estructura y selección correctas;
- headers, menús y overlays siguen funcionando sin regresiones perceptivas;
- las rutas principales siguen siendo navegables en claro y oscuro;
- no se rompen transiciones básicas por cambios en el shell.

### 7.8 Validación de rendimiento
Debe comprobarse que:
- no aparecen recomposiciones o transiciones perceptiblemente más pesadas;
- el shell y las pantallas críticas no ganan complejidad visual gratuita;
- la migración no multiplica capas o efectos innecesarios.

### 7.9 Validación sugerida por tipo de fase

| Tipo de fase | Validación prioritaria |
|---|---|
| Fundaciones | compilación, theme central, claro/oscuro |
| Shell | navegación, consistencia global, estados seleccionados |
| Formularios | estados, accesibilidad, overlays, errores |
| Pantallas críticas | consistencia visual, claro/oscuro, métricas financieras |
| Cierre | búsquedas de deuda residual, checklist final, regresión transversal |

---

## 8. Criterios de aceptación

### 8.1 Regla general
Ninguna fase puede darse por terminada si todavía requiere “interpretación visual” para decidir si quedó bien. La fase debe cerrar con criterios explícitos y comprobables.

### 8.2 Criterios mínimos para cualquier fase
Una fase solo se considera terminada si:
- su alcance quedó completamente cubierto;
- no introduce regresiones visibles relevantes;
- las dependencias declaradas para la fase siguen estables;
- la validación mínima fue ejecutada;
- la deuda residual conocida quedó documentada y acotada;
- el equipo puede explicar qué problema dejó resuelto la fase.

### 8.3 Criterios específicos por fase

| Fase | Criterio de aceptación |
|---|---|
| **Fase 0** | Existe baseline confiable y lista cerrada de zonas de impacto |
| **Fase 1** | La deuda visual está clasificada por familia y prioridad |
| **Fase 2** | Las fundaciones y taxonomías están cerradas y sin ambigüedad relevante |
| **Fase 3** | El mapeo central a Material 3 está definido y validado |
| **Fase 4** | Los recursos base funcionan sin romper arranque ni dark theme base |
| **Fase 5** | Los componentes simples reutilizados consumen el sistema de forma consistente |
| **Fase 6** | El shell completo navega y se ve coherente en todas las rutas top-level |
| **Fase 7** | Formularios, dialogs y sheets responden con semántica consistente |
| **Fase 8** | Login, onboarding, dashboard y settings dejan de depender de deuda visual estructural |
| **Fase 9** | Las features financieras restantes consumen el sistema aprobado sin variantes paralelas |
| **Fase 10** | La deuda residual quedó reducida a excepciones documentadas no bloqueantes |
| **Fase 11** | Se completa el checklist final y se declara cierre formal de la migración |

### 8.4 Regla de avance
Si una fase incumple criterios, la siguiente no debe comenzar salvo que exista una excepción explícita y documentada con justificación técnica.

---

## 9. Estrategia de rollback

### 9.1 Principio general
Cada fase debe poder revertirse de forma aislada. El rollback no debe depender de una reconstrucción manual del sistema.

### 9.2 Reglas oficiales de rollback
- cada fase debe vivir en un conjunto acotado de cambios;
- no deben mezclarse cambios funcionales con migración visual;
- no deben encadenarse varias fases en un único bloque difícil de revertir;
- debe existir evidencia del estado previo en baseline y documentación.

### 9.3 Nivel de rollback esperado por fase

| Tipo de fase | Estrategia de rollback recomendada |
|---|---|
| Fundaciones | Revertir solo capa de fundación o mapping sin tocar screens |
| Shell | Revertir componentes de navegación y shared shell en bloque propio |
| Formularios | Revertir patrones compartidos antes de propagar a pantallas |
| Pantallas críticas | Revertir pantalla por pantalla, no por feature completa si no es necesario |
| Cierre residual | Revertir excepciones localizadas, no reabrir fundaciones estables |

### 9.4 Condiciones que disparan rollback
Debe evaluarse rollback cuando:
- falla la compilación de manera estructural;
- el modo oscuro pierde coherencia global;
- el shell de navegación se rompe;
- la legibilidad financiera disminuye claramente;
- la fase no puede cerrarse sin multiplicar excepciones o hotfixes visuales.

### 9.5 Objetivo del rollback
El rollback no es fracaso del blueprint. Es parte del control de calidad de una migración incremental de alto impacto.

---

## 10. Roadmap de implementación

### Sprint A — Baseline y freeze
**Objetivo:** capturar estado actual, alcance y mapa de riesgo.  
**Autonomía:** total.  
**Validación:** baseline completo y zonas de impacto aprobadas.

### Sprint B — Mapeo de deuda visual
**Objetivo:** clasificar deuda por familias y prioridad.  
**Autonomía:** total.  
**Validación:** inventario semántico listo para implementación.

### Sprint C — Fundaciones del token system
**Objetivo:** consolidar familias y niveles de tokens.  
**Autonomía:** alta.  
**Validación:** arquitectura cerrada y sin ambigüedad crítica.

### Sprint D — Mapping central con Material 3
**Objetivo:** diseñar y aplicar la capa central de traducción entre tokens y theme.  
**Autonomía:** alta.  
**Validación:** theme central estable y validado en claro/oscuro.

### Sprint E — Compatibilidad de recursos base
**Objetivo:** alinear recursos XML y comportamiento base Android.  
**Autonomía:** media.  
**Validación:** arranque, splash y temas base sin regresiones.

### Sprint F — Shared components básicos
**Objetivo:** migrar encabezados, rows, headers y chips simples.  
**Autonomía:** alta.  
**Validación:** consistencia compartida y bajo riesgo funcional.

### Sprint G — Shell global
**Objetivo:** migrar navegación, bottom bar y menú global.  
**Autonomía:** alta.  
**Validación:** navegación top-level íntegra.

### Sprint H — Inputs, dialogs y overlays
**Objetivo:** estandarizar patrones de captura y feedback.  
**Autonomía:** alta.  
**Validación:** formularios y overlays consistentes.

### Sprint I — Login y Onboarding
**Objetivo:** migrar entry flows del producto.  
**Autonomía:** alta.  
**Validación:** identidad y coherencia de entrada estables.

### Sprint J — Dashboard y Settings
**Objetivo:** migrar shell financiero principal y vista transversal de configuración.  
**Autonomía:** alta.  
**Validación:** dashboard y settings sin deuda estructural mayor.

### Sprint K — Transacciones y Transferencias
**Objetivo:** migrar flujos financieros recurrentes.  
**Autonomía:** alta.  
**Validación:** listas, formularios y semántica del dominio consistentes.

### Sprint L — Categorías, Presupuesto y Préstamos
**Objetivo:** migrar features de mayor densidad funcional.  
**Autonomía:** media-alta.  
**Validación:** estados complejos resueltos con tokens.

### Sprint M — Gráficas, Reportes y pantallas residuales
**Objetivo:** cerrar cobertura funcional.  
**Autonomía:** media.  
**Validación:** consistencia final entre visualización, lectura y reporting.

### Sprint N — Limpieza final y cierre
**Objetivo:** reducir deuda remanente, validar y cerrar migración.  
**Autonomía:** alta.  
**Validación:** checklist final completo y cierre formal.

---

## 11. Riesgo de deuda técnica

### 11.1 Deuda que debería desaparecer con la migración
La migración debe eliminar o reducir fuertemente:
- hardcodes visuales repetidos;
- estilos locales equivalentes con distinto naming;
- decisiones de elevación no gobernadas;
- radios y spacing inventados por pantalla;
- duplicación de patrones visuales en dashboard y otras features;
- dependencia de recursos heredados como fuente principal de semántica del producto;
- inconsistencias entre claro y oscuro causadas por literales locales.

### 11.2 Deuda que probablemente seguirá existiendo
Incluso con la migración completa, puede seguir existiendo:
- deuda funcional no relacionada con UI;
- naming legacy del proyecto;
- componentes que requieran refactorización más allá del sistema visual;
- dependencia de algunas capas XML por compatibilidad con Android;
- complejidad de pantallas densas cuya mejora requiera trabajo arquitectónico adicional.

### 11.3 Riesgo si la migración se detiene a mitad de camino
Si la migración queda incompleta, el peor escenario no es el sistema viejo, sino un sistema híbrido mal gobernado:
- dos semánticas visuales conviviendo;
- componentes nuevos usando tokens y componentes viejos usando hardcodes;
- divergencia entre documentos y código;
- aumento de complejidad para futuras features.

### 11.4 Principio de deuda aceptable
La única deuda aceptable al finalizar cada fase es aquella que:
- está documentada;
- tiene alcance acotado;
- no contradice la arquitectura aprobada;
- no obliga a reinterpretar el sistema.

---

## 12. Checklist final

### 12.1 Checklist de cierre global de migración
- [ ] Existe una fuente de verdad visual central y reconocible.
- [ ] Los documentos oficiales siguen alineados con la implementación real.
- [ ] `MainActivity` y el entry point del theme permanecen estables.
- [ ] `Theme.kt` actúa como capa orquestadora y no como depósito de excepciones.
- [ ] `Color.kt` ya no funciona como colección parcial de literales heredados.
- [ ] `Shape.kt` responde a una jerarquía oficial del sistema.
- [ ] `Type.kt` responde a una jerarquía tipográfica oficial y consistente.
- [ ] La capa equivalente de spacing está formalizada y usada consistentemente.
- [ ] La capa equivalente de elevation está formalizada y usada consistentemente.
- [ ] El shell global del producto es coherente y estable.
- [ ] Los componentes compartidos principales consumen el sistema aprobado.
- [ ] Dashboard dejó de concentrar deuda visual estructural relevante.
- [ ] Login y Onboarding ya no dependen de hardcodes dominantes.
- [ ] Formularios, dialogs y overlays responden a reglas compartidas.
- [ ] La semántica de ingresos, gastos y transferencias es consistente.
- [ ] El modo claro es visualmente coherente en todas las rutas principales.
- [ ] El modo oscuro es coherente, sobrio y legible en todas las rutas principales.
- [ ] Los estados del sistema son consistentes entre componentes y pantallas.
- [ ] No quedan variantes paralelas del mismo patrón sin justificación.
- [ ] Los recursos XML heredados tienen una responsabilidad limitada y explícita.
- [ ] La deuda residual está documentada y no bloquea el sistema.
- [ ] La validación transversal fue ejecutada y aprobada.
- [ ] Existe decisión explícita de cierre de la migración.

### 12.2 Condición de cierre
La migración solo puede cerrarse cuando el proyecto ya no dependa de interpretación individual para mantener consistencia visual. En ese punto, el sistema deja de ser una aspiración documental y pasa a ser una capacidad operativa real del producto.

---

## Criterio final del blueprint

Este blueprint no decide cómo debe verse Xpendz. Eso ya está resuelto por los documentos previos.

Este blueprint decide cómo ejecutar la transición sin romper el producto, sin perder coherencia y sin abrir una nueva generación de deuda visual.