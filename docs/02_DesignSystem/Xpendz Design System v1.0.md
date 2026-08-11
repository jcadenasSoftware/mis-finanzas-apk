# Xpendz Design System v1.0

**Fecha:** 11 de agosto de 2026  
**Versión:** 1.0  
**Tipo:** Documento rector del sistema de diseño  
**Estado:** Activo  
**Documento base:** [Visual Audit v1.0](../01_Audits/Visual/Visual%20Audit%20v1.0.md)

---

## Objetivo

Definir las reglas oficiales que gobernarán la evolución visual y de interacción de Xpendz Android durante los próximos años, a partir del estado actual auditado del producto.

Este documento no implementa soluciones ni redefine la identidad existente. Su función es establecer criterios de diseño, jerarquía, semántica y coherencia para que futuras decisiones de producto, UX, UI y desarrollo mantengan una dirección común.

---

## Alcance

Este documento regula:
- principios de diseño del producto;
- reglas de uso de color, superficies, elevaciones, shapes, espaciado y tipografía;
- definición conceptual de familias de componentes;
- principios de motion, dark theme, accesibilidad y escalabilidad;
- estrategia de adopción por fases.

Este documento no regula:
- implementaciones concretas en Kotlin;
- valores HEX;
- cambios directos sobre `Color.kt`, `Theme.kt` o recursos existentes;
- decisiones visuales aisladas por pantalla sin validación sistémica.

---

## Tabla de contenidos

1. [Filosofía del producto](#1-filosofía-del-producto)
2. [Principios de diseño](#2-principios-de-diseño)
3. [Sistema de colores](#3-sistema-de-colores)
4. [Sistema de superficies](#4-sistema-de-superficies)
5. [Elevaciones](#5-elevaciones)
6. [Shapes](#6-shapes)
7. [Espaciado](#7-espaciado)
8. [Tipografía](#8-tipografía)
9. [Componentes](#9-componentes)
10. [Motion](#10-motion)
11. [Dark Theme Philosophy](#11-dark-theme-philosophy)
12. [Accesibilidad](#12-accesibilidad)
13. [Escalabilidad](#13-escalabilidad)
14. [Roadmap](#14-roadmap)

---

## 1. Filosofía del producto

Xpendz es una aplicación de finanzas personales. Su sistema de diseño debe servir primero a la comprensión, a la confianza y a la toma de decisiones serena.

### Xpendz debe transmitir
- **Claridad:** el usuario debe entender qué ve, qué significa y qué puede hacer sin esfuerzo adicional.
- **Confianza:** la interfaz debe sentirse estable, seria y predecible.
- **Estabilidad:** la UI no debe parecer experimental ni cambiante entre contextos similares.
- **Rapidez:** el producto debe sentirse ágil aun cuando la tarea sea compleja.
- **Precisión:** cifras, estados, categorías y acciones deben percibirse exactos y sin ambigüedad.
- **Simplicidad:** la interfaz debe reducir ruido, no competir por atención.

### Declaración rectora
Xpendz no busca impresionar visualmente; busca disminuir la carga cognitiva necesaria para gestionar dinero.

### Implicación de diseño
Toda decisión visual debe responder a una pregunta práctica:

> ¿Esta decisión ayuda al usuario a comprender, decidir o actuar mejor sobre su información financiera?

Si la respuesta es no, la decisión debe reconsiderarse.

---

## 2. Principios de diseño

### 2.1 Jerarquía antes que decoración
La interfaz debe ordenar información y acciones por importancia real, no por impulso estético.

### 2.2 Consistencia antes que novedad
Los mismos problemas deben resolverse con los mismos patrones visuales e interactivos.

### 2.3 Semántica antes que estilo aislado
Color, elevación, shape, motion y tipografía deben comunicar función y estado, no solo apariencia.

### 2.4 Densidad controlada
La aplicación puede mostrar mucha información, pero nunca debe sentirse apretada, caótica o agresiva.

### 2.5 Acciones críticas con fricción adecuada
Las operaciones destructivas, financieras o irreversibles deben distinguirse por lenguaje, confirmación y jerarquía visual.

### 2.6 Lectura numérica prioritaria
En una aplicación financiera, los importes, balances, tendencias y estados tienen prioridad de comprensión sobre elementos decorativos.

### 2.7 Progresividad
La UI debe revelar complejidad por niveles. Lo esencial debe verse primero; lo secundario debe aparecer cuando el contexto lo justifique.

### 2.8 Sobriedad emocional
Xpendz puede ser cálida, pero no debe ser teatral. La emoción visual debe estar subordinada a la confianza.

---

## 3. Sistema de colores

### 3.1 Principio general
El sistema de color de Xpendz debe comunicar jerarquía, estado, acción y semántica financiera. El color nunca debe usarse como adorno principal ni como única fuente de significado.

### 3.2 Familias semánticas

| Familia | Función | Uso esperado | Debe evitarse |
|---|---|---|---|
| **Brand** | Identidad principal del producto | Acciones primarias, focos controlados, acentos de navegación, elementos de marca | Saturar pantallas completas con protagonismo continuo |
| **Secondary** | Acompañamiento y apoyo | Destacar información complementaria o áreas de apoyo | Competir visualmente con la acción principal |
| **Neutral** | Base de legibilidad y estructura | Fondos, superficies, divisores, texto, estados pasivos | Quedarse tan plana que elimine jerarquía |
| **Semantic Positive** | Resultado favorable o incremento | Ingresos, crecimiento, confirmaciones, estados saludables | Usarla como color de marca general |
| **Semantic Negative** | Riesgo o pérdida | Gastos, errores, advertencias destructivas, deuda crítica | Usarla para texto común o resaltar elementos neutros |
| **Semantic Informational** | Estado informativo o de proceso | Transferencias, sincronización, indicadores de progreso o contexto técnico | Reemplazar con ella acciones primarias sin justificación |
| **Attention** | Llamado puntual y contenido | Alertas no destructivas, límites próximos, información que requiere revisión | Convertirla en un segundo primario persistente |

### 3.3 Reglas de uso
- El color principal debe indicar prioridad, no abundancia.
- Los colores semánticos financieros deben conservar el mismo significado en toda la aplicación.
- Un mismo color no debe significar cosas distintas según la pantalla.
- Las áreas cromáticas extensas deben reservarse para contextos de alto valor jerárquico, no para relleno.
- El color debe acompañarse de iconografía, texto o estructura cuando comunique un estado importante.

### 3.4 Reglas específicas para producto financiero
- **Ingresos** y **gastos** deben mantener consistencia absoluta entre listas, gráficos, métricas y formularios.
- **Transferencias** deben diferenciarse sin parecer un ingreso ni un gasto.
- Los colores de riesgo no deben disparar ansiedad visual permanente.
- Los estados de error deben sentirse serios y claros, no alarmistas.

### 3.5 Qué debe evitarse
- Paletas paralelas por pantalla.
- Colores ad hoc para resolver necesidades locales.
- Uso de color para decorar cards o módulos sin función semántica.
- Bajas diferencias entre texto y fondo en elementos clave.
- Asociar la marca con señales de error o pérdida.

---

## 4. Sistema de superficies

### 4.1 Principio general
Las superficies deben organizar la información por capas de importancia y proximidad interactiva. La jerarquía de superficies es una herramienta de comprensión, no una herramienta ornamental.

### 4.2 Jerarquía oficial de superficies

| Nivel de superficie | Propósito | Uso esperado |
|---|---|---|
| **Canvas** | Plano base de la experiencia | Fondo general de pantalla y marco de lectura |
| **Primary Surface** | Contenedor principal de contenido | Cards estándar, bloques de información, formularios principales |
| **Secondary Surface** | Separación funcional dentro de una superficie primaria | Subsecciones, grupos internos, filtros, resúmenes parciales |
| **Emphasis Surface** | Resalte contextual moderado | Métricas destacadas, paneles de síntesis, estados útiles pero no críticos |
| **Overlay Surface** | Superficies transitorias | Menús, bottom sheets, diálogos, tooltips, capas modales |
| **Critical Surface** | Contextos de riesgo o confirmación importante | Advertencias destructivas, estados críticos, confirmaciones sensibles |

### 4.3 Reglas de uso
- La pantalla debe descansar mayoritariamente sobre pocas superficies, no sobre demasiadas capas.
- Toda nueva superficie debe justificar por qué necesita existir como capa independiente.
- La profundidad debe sentirse racional: cada capa adicional debe corresponder a una diferencia real de función o foco.
- Una superficie no debe depender únicamente del color para separarse; también debe apoyarse en spacing, borde, elevación o composición.

### 4.4 Qué debe evitarse
- Cards dentro de cards sin justificación funcional.
- Superficies con jerarquía visual similar pero propósitos distintos.
- Resaltes permanentes que conviertan toda la pantalla en “importante”.
- Modales que se perciban como una pantalla más en lugar de una capa transitoria.

---

## 5. Elevaciones

### 5.1 Principio general
La elevación oficial de Xpendz debe comunicar estructura, foco y transitividad. Nunca debe usarse para ornamentar de forma gratuita.

### 5.2 Escala oficial

| Nivel | Significado | Uso esperado |
|---|---|---|
| **Elevation 0** | Plano base | Canvas, bloques estructurales estables, layouts de fondo |
| **Elevation 1** | Separación mínima | Cards estándar, agrupaciones principales, contenedores neutros |
| **Elevation 2** | Interacción contextual | Elementos seleccionados, contenedores con énfasis moderado, paneles destacados |
| **Elevation 3** | Interfaz transitoria | Menús, dropdowns, sheets ligeros, paneles flotantes |
| **Elevation 4** | Prioridad de atención alta | Diálogos modales, confirmaciones importantes, capas de interrupción controlada |

### 5.3 Justificación de la escala
- Xpendz necesita una jerarquía legible, no teatral.
- La mayoría de la aplicación debe vivir entre niveles bajos y medios.
- Los niveles altos deben reservarse para eventos transitorios o decisiones sensibles.
- La elevación no debe reemplazar una mala jerarquía de contenido.

### 5.4 Reglas de uso
- A mayor frecuencia de uso, menor dramatización visual.
- Los componentes persistentes deben vivir en niveles estables y previsibles.
- Un componente no debe cambiar de elevación sin motivo de interacción o cambio de estado.
- La elevación debe ser consistente entre componentes equivalentes.

### 5.5 Qué debe evitarse
- Variaciones arbitrarias de profundidad entre pantallas.
- Uso simultáneo de demasiados niveles en una sola vista.
- Interfaces donde todo parece flotar.
- Señalar selección, hover, foco y prioridad con elevación distinta en cada módulo.

---

## 6. Shapes

### 6.1 Principio general
Las formas de Xpendz deben reforzar cercanía, orden y control. La geometría debe ser suave y confiable, pero nunca blanda en exceso.

### 6.2 Escala oficial de radios
La escala oficial debe seguir la lógica ya presente en el sistema actual, organizada por tokens de uso y no por decisiones locales.

| Token | Carácter | Uso esperado |
|---|---|---|
| **extraSmall** | Contención precisa | Indicadores pequeños, etiquetas compactas, elementos secundarios de baja complejidad |
| **small** | Compacto y controlado | Inputs simples, chips, pequeños contenedores, celdas o mini panels |
| **medium** | Equilibrio funcional | Agrupaciones intermedias, módulos de apoyo, tarjetas compactas |
| **large** | Contenedor estándar del producto | Cards principales, filas enriquecidas, bloques de settings, módulos de uso frecuente |
| **extraLarge** | Contenedor protagonista | Resúmenes financieros, paneles principales, headers de bloque, modales de alto nivel |

### 6.3 Regla de excepción
Formas completamente circulares o tipo cápsula deben considerarse excepciones funcionales para:
- avatares;
- indicadores puntuales;
- controles específicos cuyo comportamiento o significado lo requiera.

No deben reemplazar la escala principal de contenedores.

### 6.4 Reglas de uso
- El radio debe crecer con la importancia y el tamaño del contenedor, no por preferencia local.
- Componentes equivalentes deben compartir el mismo nivel de shape.
- Shapes muy agresivos o demasiado rectos deben evitarse si rompen la continuidad del producto.
- La selección, el foco y el estado no deben resolverse cambiando arbitrariamente el radio.

### 6.5 Qué debe evitarse
- Shapes distintos para cards que cumplen la misma función.
- Radios únicos inventados por pantalla.
- Contenedores muy redondeados que resten precisión a una tarea financiera.
- Uso de cápsulas en exceso para componentes que no lo necesitan.

---

## 7. Espaciado

### 7.1 Principio general
El espaciado de Xpendz debe funcionar como un sistema modular único. Debe ordenar lectura, agrupar significado y crear respiración visual estable en toda la app.

### 7.2 Escala oficial

| Token | Función | Uso esperado |
|---|---|---|
| **2XS** | Separación mínima | Ajustes internos muy pequeños, icono-texto compacto, micro gaps |
| **XS** | Separación corta | Pares estrechos dentro de un mismo componente |
| **S** | Relación inmediata | Label y valor, texto e icono, agrupaciones muy cercanas |
| **M** | Espaciado base | Padding estándar, separación típica entre elementos de un mismo bloque |
| **L** | Agrupación amplia | Separación entre subgrupos dentro de una card o sección |
| **XL** | Separación de sección | Distancia entre módulos diferentes dentro de una pantalla |
| **2XL** | Cambio claro de contexto | Separación entre grupos mayores, headers y contenido principal |
| **3XL** | Respiro estructural | Aperturas de layout o secciones de transición |

### 7.3 Reglas de uso
- Todo spacing debe derivarse de esta escala oficial.
- No se deben introducir valores únicos para resolver problemas aislados.
- La proximidad visual debe representar proximidad semántica.
- Un grupo de información no debe depender del borde o del color para percibirse unido; el espaciado debe ayudar a construir esa relación.
- Cuanto mayor la densidad informativa, más estricta debe ser la consistencia del spacing.

### 7.4 Reglas para pantallas financieras
- Las métricas relacionadas deben agruparse con espaciado corto o medio.
- Los cambios de tema o de intención dentro de una pantalla deben reflejarse con espaciado amplio, no solo con títulos.
- Formularios deben priorizar ritmo vertical predecible.
- Tablas, listas y celdas no deben compactarse al punto de sacrificar legibilidad táctil o visual.

### 7.5 Qué debe evitarse
- Espaciados “casi iguales” usados indistintamente.
- Pantallas donde cada módulo maneja un ritmo diferente.
- Separaciones excesivas que fragmenten lectura.
- Densidad tan alta que impida detectar agrupaciones.

---

## 8. Tipografía

### 8.1 Principio general
La tipografía de Xpendz debe servir a la lectura rápida, a la jerarquía de información y a la precisión de los datos. La voz visual debe ser sobria, clara y estable.

### 8.2 Jerarquía oficial

| Nivel tipográfico | Propósito | Uso esperado |
|---|---|---|
| **Display** | Mensajes heroicos excepcionales | Onboarding, vacíos clave, estados muy puntuales de alto valor comunicativo |
| **Headline** | Encabezados de pantalla y resúmenes principales | Títulos de vistas, saldos principales, bloques de alto nivel |
| **Title** | Títulos de componente y sección | Cards, listas, módulos, cabeceras de grupos |
| **Body** | Lectura principal | Texto explicativo, detalle de movimientos, contenido descriptivo |
| **Label** | Soporte y densidad controlada | Etiquetas, chips, captions, metadata, soporte contextual |
| **Numeric Emphasis** | Importes y métricas | Saldos, ingresos, gastos, variaciones, porcentajes, datos críticos |

### 8.3 Reglas de jerarquía
- El texto más importante no es siempre el más grande: es el que el usuario necesita entender primero.
- Las cifras clave deben tener prioridad tipográfica sobre la mayoría del texto narrativo.
- Los labels deben ayudar sin competir con el contenido principal.
- El peso tipográfico debe usarse para estructurar, no para compensar falta de layout.

### 8.4 Reglas para contexto financiero
- Los importes deben ser fáciles de escanear y comparar.
- Ingreso, gasto, balance y tendencia deben poder distinguirse por contexto completo, no solo por color.
- Las fechas, categorías y metadata deben ser secundarias pero nunca ambiguas.
- Las frases de apoyo deben ser breves, legibles y no moralizantes.

### 8.5 Qué debe evitarse
- Múltiples jerarquías diferentes para el mismo tipo de dato.
- Títulos excesivamente expresivos para tareas rutinarias.
- Body text compitiendo con métricas.
- Reducir texto de soporte a un nivel ilegible para “hacer espacio”.

---

## 9. Componentes

### 9.1 Principio general
Los componentes oficiales de Xpendz deben organizarse como una familia coherente de primitives, patterns y componentes de negocio. Ningún feature debería necesitar inventar un lenguaje propio para resolver problemas ya conocidos.

### 9.2 Familia oficial de componentes

| Familia | Rol dentro del sistema | Ejemplos conceptuales |
|---|---|---|
| **App Shell** | Estructura base de navegación y contexto | Top app areas, navegación principal, headers, barras inferiores, contenedores de pantalla |
| **Actions** | Disparo de tareas y decisiones | Botones primarios, secundarios, terciarios, icon buttons, acciones destructivas |
| **Inputs** | Captura y edición de datos | Campos, selectores, toggles, pickers, validaciones, confirmaciones de entrada |
| **Selection** | Elección entre opciones | Chips, tabs, filtros, radios, listas seleccionables, estados activos |
| **Content Blocks** | Agrupación de información | Cards, filas enriquecidas, secciones, resúmenes, módulos de métricas |
| **Financial Data** | Presentación semántica de dinero | Amount rows, balance summaries, transaction items, account items, state metrics |
| **Feedback** | Comunicación de estado del sistema | Chips de sincronización, banners, errores inline, snackbars, estados de carga |
| **Overlay** | Interacciones transitorias | Dropdowns, dialogs, modal sheets, confirmaciones, menús contextuales |
| **Empty / Loading / Error States** | Estados sistémicos | Vacíos, skeletons, progress, recuperación, retry states |
| **Data Visualization** | Apoyo analítico | Resúmenes, gráficas, tendencias, comparativas, leyendas, filtros de vista |

### 9.3 Reglas para componentes
- Cada componente debe tener una responsabilidad clara y limitada.
- Un patrón repetido en más de una feature debe considerarse candidato a componente oficial.
- Los componentes deben definirse por propósito y comportamiento antes que por apariencia aislada.
- Las variantes deben existir por necesidad de producto, no por preferencia visual de una pantalla.

### 9.4 Modelo de capas
El sistema debe evolucionar en tres capas:
1. **Primitives:** tokens, contenedores básicos, acciones base, inputs base.
2. **Patterns:** combinaciones recurrentes con semántica clara.
3. **Feature Components:** composición específica de negocio sobre patrones ya consolidados.

### 9.5 Qué debe evitarse
- Duplicar componentes con pequeñas diferencias locales.
- Mezclar lógica de negocio compleja dentro de primitives visuales.
- Crear variantes especiales por pantalla sin documentar su razón sistémica.
- Resolver inconsistencias del sistema con componentes “temporales” permanentes.

---

## 10. Motion

### 10.1 Principio general
El motion de Xpendz debe orientar, confirmar y suavizar transiciones. Nunca debe distraer ni teatralizar una tarea financiera.

### 10.2 Reglas rectoras
- La animación debe comunicar **causa y efecto**.
- Las transiciones deben sentirse **rápidas, limpias y sobrias**.
- El movimiento debe ayudar al usuario a entender qué cambió, dónde apareció algo y qué estado acaba de ocurrir.
- Las animaciones deben reforzar continuidad espacial y jerárquica.

### 10.3 Casos válidos
- Aparición y desaparición de capas transitorias.
- Confirmación de selección o cambio de estado.
- Expansión o colapso de contenido contextual.
- Indicadores de carga y sincronización.
- Actualización de métricas o filtros cuando mejoren comprensión.

### 10.4 Casos que deben evitarse
- Animaciones decorativas sin valor informativo.
- Rebotes o elasticidades que resten seriedad a operaciones financieras.
- Demoras largas antes de permitir actuar.
- Múltiples elementos animándose a la vez sin una narrativa clara.
- Transiciones que oculten cambios importantes en importes o estados.

### 10.5 Criterios de tono
El motion de Xpendz debe sentirse:
- preciso;
- contenido;
- ágil;
- profesional;
- no juguetón.

---

## 11. Dark Theme Philosophy

### 11.1 Objetivo experiencial
El modo oscuro de Xpendz debe sentirse **calmo, estable y preciso**. Debe permitir revisar información financiera durante periodos prolongados con menor fatiga visual, sin sacrificar jerarquía ni confianza.

### 11.2 Qué debe sentirse
- Menor deslumbramiento.
- Lectura controlada y cómoda.
- Profundidad sobria y bien organizada.
- Continuidad con la identidad del modo claro.
- Seguridad en la lectura de importes, estados y acciones.

### 11.3 Qué debe evitarse
- Interfaces con apariencia luminosa, neón o tecnológica en exceso.
- Contrastes violentos que vuelvan agresiva la experiencia.
- Negros aplastados que borren jerarquías entre superficies.
- Brillos cromáticos que parezcan alertas permanentes.
- Pérdida de claridad en datos financieros por querer “verse elegante”.

### 11.4 Comportamiento esperado en una interfaz financiera
- Las capas deben seguir siendo distinguibles sin depender de brillos intensos.
- Los importes deben conservar prioridad visual clara.
- Los estados positivos, negativos e informativos deben mantener significado estable, sin sobresaturarse.
- Los formularios deben seguir siendo cómodos y no volverse visualmente densos o ambiguos.
- Los gráficos y comparativas deben mantener legibilidad sin generar ruido o fatiga.

### 11.5 Principio rector del dark theme
El modo oscuro no es una inversión cromática del modo claro. Es una reinterpretación funcional de la misma interfaz para un entorno de baja luminosidad.

---

## 12. Accesibilidad

### 12.1 Principio general
La accesibilidad de Xpendz debe ser estructural, no correctiva. Debe formar parte del sistema de diseño desde su base.

### 12.2 Principios oficiales
- **Legibilidad primero:** texto, importes y estados deben mantenerse claros en toda condición de uso.
- **Contraste suficiente:** las relaciones entre foreground y background deben proteger la comprensión real.
- **Tamaño táctil adecuado:** las acciones deben ser fáciles de activar con precisión.
- **Color no exclusivo:** ningún estado importante debe depender solo del color.
- **Orden lógico:** foco, navegación y lectura deben seguir una secuencia comprensible.
- **Texto escalable:** la jerarquía debe resistir cambios de tamaño del sistema.
- **Feedback claro:** errores, validaciones y estados del sistema deben expresarse con lenguaje simple y directo.
- **Movimiento responsable:** la UI debe poder reducir estímulo cuando el usuario o el contexto lo requiera.

### 12.3 Reglas para datos financieros
- Los importes deben leerse sin ambigüedad.
- Los cambios de signo, tendencia o estado no deben depender solo de matices visuales pequeños.
- Las categorías, fechas y cuentas deben conservar diferenciación suficiente.
- Los gráficos deben ofrecer soporte textual o estructural cuando sea necesario.

### 12.4 Qué debe evitarse
- Labels implícitos.
- Controles muy compactos por razones estéticas.
- Estados de error vagos o demasiado técnicos.
- Jerarquías que se rompan al aumentar tamaño tipográfico.

---

## 13. Escalabilidad

### 13.1 Principio general
El sistema de diseño debe poder crecer sin perder coherencia. Escalar no significa agregar más estilos; significa aumentar cobertura con menos ambigüedad.

### 13.2 Reglas de evolución
- Todo nuevo patrón debe intentar resolverse primero con tokens y componentes existentes.
- Cuando una excepción aparece repetidamente, debe evaluarse como candidato sistémico.
- Ninguna feature debe introducir reglas visuales propias sin documentación.
- Las decisiones del sistema deben priorizar estabilidad longitudinal sobre rapidez local.

### 13.3 Modelo de gobernanza

| Capa | Qué evoluciona aquí | Criterio de cambio |
|---|---|---|
| **Tokens** | Semántica base del sistema | Solo cuando la necesidad afecta múltiples áreas del producto |
| **Primitives** | Componentes base y estructura visual | Cuando el comportamiento y uso son comunes y repetibles |
| **Patterns** | Composiciones recurrentes | Cuando varias features resuelven el mismo problema |
| **Feature UI** | Casos específicos de negocio | Cuando la variación es contextual y no sistémica |

### 13.4 Criterios de aceptación para cambios futuros
Un cambio debe considerarse saludable para el sistema si:
- mejora claridad sin aumentar ruido;
- reduce duplicación conceptual;
- respeta la semántica existente;
- puede explicarse en una regla reusable;
- no obliga a reinterpretar significados ya aprendidos por el usuario.

### 13.5 Qué debe evitarse
- Expandir el sistema a base de excepciones.
- Resolver velocidad de entrega con deuda visual no documentada.
- Introducir variantes con diferencias mínimas e innecesarias.
- Cambiar el significado de un patrón sin transición ni documentación.

---

## 14. Roadmap

### Fase 1 — Alineación conceptual
**Objetivo:** convertir la auditoría y este documento en base normativa compartida.

**Entregables esperados:**
- consenso entre producto, diseño y desarrollo sobre principios rectores;
- validación de vocabulario oficial del sistema;
- identificación de deudas que contradicen estas reglas.

### Fase 2 — Formalización de fundaciones
**Objetivo:** consolidar el marco conceptual de tokens y jerarquías.

**Enfoque:**
- roles de color y su semántica;
- jerarquía de superficies;
- escala oficial de elevación;
- escala oficial de shapes;
- escala oficial de spacing;
- jerarquía tipográfica del producto.

### Fase 3 — Consolidación de primitives
**Objetivo:** normalizar las piezas base del sistema.

**Enfoque:**
- contenedores;
- acciones;
- inputs;
- navegación;
- feedback;
- overlays.

### Fase 4 — Consolidación de patterns de negocio
**Objetivo:** unificar patrones financieros recurrentes.

**Enfoque:**
- resumen de balances;
- filas de transacciones;
- tarjetas de cuenta;
- métricas comparativas;
- filtros y selección temporal;
- estados de sincronización y carga.

### Fase 5 — Cobertura transversal
**Objetivo:** asegurar coherencia entre features existentes.

**Enfoque:**
- dashboard;
- transacciones;
- transfers;
- budgets;
- categories;
- loans;
- reports;
- settings.

### Fase 6 — Accesibilidad, dark theme y validación
**Objetivo:** endurecer calidad sistémica.

**Enfoque:**
- validación de accesibilidad;
- consistencia dark theme;
- reducción de excepciones visuales;
- validación de jerarquías en estados vacíos, error y carga.

### Fase 7 — Gobernanza permanente
**Objetivo:** convertir el sistema en una práctica mantenible.

**Enfoque:**
- documentar decisiones futuras en `docs/04_Decisions/`;
- registrar evolución de componentes en `docs/02_DesignSystem/`;
- vincular auditorías futuras con ajustes del sistema;
- revisar periódicamente la coherencia entre reglas y producto real.

---

## Criterio final del sistema

Si Xpendz evoluciona correctamente, el usuario no debería notar “más diseño”; debería notar menos fricción, menos ambigüedad y más confianza.

Ese es el estándar que este sistema debe proteger a largo plazo.