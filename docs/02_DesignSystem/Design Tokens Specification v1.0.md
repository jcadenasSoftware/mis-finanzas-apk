# Design Tokens Specification v1.0

**Fecha:** 11 de agosto de 2026  
**Versión:** 1.0  
**Tipo:** Especificación técnica de estructura  
**Estado:** Activo  
**Documentos base:** [Visual Audit v1.0](../01_Audits/Visual/Visual%20Audit%20v1.0.md) · [Xpendz Design System v1.0](./Xpendz%20Design%20System%20v1.0.md)

---

## Objetivo

Definir la estructura oficial del sistema de Design Tokens de Xpendz Android para servir como puente entre el Design System conceptual y su futura implementación técnica.

Este documento describe taxonomías, familias, niveles semánticos, responsabilidades y restricciones. No define valores concretos, no implementa código y no sustituye a Material 3.

---

## Alcance

Este documento regula:
- la arquitectura del sistema de tokens;
- las familias oficiales de tokens del producto;
- la semántica y jerarquía de cada familia;
- la relación entre tokens del producto y roles de Material 3;
- el orden recomendado de implementación futura.

Este documento no regula:
- valores específicos de color, tamaño, tiempo o distancia;
- decisiones aisladas por pantalla;
- implementación en Kotlin, XML o cualquier framework;
- diseño visual de componentes concretos.

---

## Tabla de contenidos

1. [Filosofía de los Design Tokens](#1-filosofía-de-los-design-tokens)
2. [Arquitectura del sistema de tokens](#2-arquitectura-del-sistema-de-tokens)
3. [Taxonomía de Color Tokens](#3-taxonomía-de-color-tokens)
4. [Taxonomía de Surface Tokens](#4-taxonomía-de-surface-tokens)
5. [Elevation Tokens](#5-elevation-tokens)
6. [Shape Tokens](#6-shape-tokens)
7. [Spacing Tokens](#7-spacing-tokens)
8. [Typography Tokens](#8-typography-tokens)
9. [Motion Tokens](#9-motion-tokens)
10. [State Tokens](#10-state-tokens)
11. [Relación entre Tokens y Material 3](#11-relación-entre-tokens-y-material-3)
12. [Estrategia de implementación futura](#12-estrategia-de-implementación-futura)

---

## 1. Filosofía de los Design Tokens

### 1.1 Qué son
Los Design Tokens son la unidad oficial de traducción entre intención de diseño y comportamiento visual implementable.

En Xpendz, un token no representa una preferencia local; representa una decisión sistémica reusable.

### 1.2 Qué problema resuelven
Los tokens existen para resolver estos problemas:
- inconsistencia entre pantallas;
- duplicación de reglas visuales;
- crecimiento desordenado del sistema;
- ambigüedad entre intención semántica y apariencia final;
- dependencia de valores locales difíciles de mantener;
- desacoplamiento entre producto, diseño y desarrollo.

### 1.3 Qué deben garantizar
Los tokens de Xpendz deben garantizar:
- coherencia visual longitudinal;
- semántica estable entre contextos financieros;
- capacidad de evolución sin rediseñar todo el sistema;
- interoperabilidad con Material 3;
- claridad entre fundaciones, semántica y uso final.

### 1.4 Qué nunca deben resolver
Los tokens no deben resolver:
- problemas específicos de una sola pantalla;
- preferencias puntuales de estilo sin valor sistémico;
- composición detallada de componentes;
- decisiones de negocio efímeras;
- excepciones temporales no documentadas.

### 1.5 Principio rector
Un token solo debe existir si puede explicar una regla reusable del producto.

---

## 2. Arquitectura del sistema de tokens

### 2.1 Familias oficiales
El sistema oficial de Xpendz se organiza en estas familias:
- **Color Tokens**
- **Surface Tokens**
- **Elevation Tokens**
- **Shape Tokens**
- **Spacing Tokens**
- **Typography Tokens**
- **Motion Tokens**
- **State Tokens**

### 2.2 Capas del sistema
Los tokens deben organizarse en capas lógicas para evitar mezclar intención con valor final.

| Capa | Propósito | Qué describe | Qué no describe |
|---|---|---|---|
| **Foundation Tokens** | Fundaciones abstractas del sistema | Escalas internas y estructuras base | Decisiones de negocio o componentes concretos |
| **Semantic Tokens** | Significado del producto | Roles del sistema y del dominio financiero | Valores visuales aislados |
| **Alias Tokens** | Vinculación a uso específico | Asignación de un token semántico a un slot de uso | Lógica de composición del componente |
| **State Tokens** | Variación por interacción o feedback | Cómo cambia una intención según estado | Reglas arbitrarias por pantalla |

### 2.3 Regla de dependencia
La dependencia debe ser siempre descendente:
- un alias puede depender de un token semántico;
- un token semántico puede depender de foundation tokens;
- un foundation token no debe depender de semántica de producto.

### 2.4 Reglas de arquitectura
- Ninguna feature debe crear una familia paralela de tokens.
- La semántica de negocio debe vivir en la capa semántica, no en la capa foundation.
- Los alias deben explicar uso, no inventar significado nuevo.
- El sistema debe crecer añadiendo cobertura, no excepciones.

### 2.5 Convención de nomenclatura
La nomenclatura oficial debe seguir una estructura semántica predecible:

`familia / dominio / rol / énfasis / estado / contexto`

No todos los tokens necesitan todos los segmentos. La regla es:
- usar solo los segmentos necesarios para describir el comportamiento;
- evitar nombres vagos;
- evitar nombres ligados a una pantalla.

### 2.6 Reglas de nomenclatura
Los nombres de tokens deben:
- describir intención, no apariencia aislada;
- ser independientes de una feature específica;
- ser legibles por diseño y desarrollo;
- mantener consistencia terminológica entre familias.

Los nombres de tokens no deben:
- describir valores concretos;
- referenciar ubicaciones como “dashboard”, “settings” o “home”;
- duplicar conceptos ya presentes en otra familia;
- mezclar semántica financiera con semántica de interacción si pertenecen a capas distintas.

---

## 3. Taxonomía de Color Tokens

### 3.1 Principio general
Los Color Tokens de Xpendz deben comunicar jerarquía, estado, estructura e información financiera sin depender de decisiones cromáticas locales.

### 3.2 Familias oficiales
Las familias oficiales de color son:
- **Brand**
- **Secondary**
- **Neutral**
- **Positive**
- **Negative**
- **Information**
- **Warning**

### 3.3 Arquitectura interna sugerida
Cada familia de color debe poder expresar, cuando aplique:
- presencia base;
- presencia contenida;
- presencia enfatizada;
- color “on” para legibilidad sobre la familia;
- color container para agrupaciones o énfasis suave;
- color de borde/divisor cuando exista necesidad semántica.

### 3.4 Brand

| Aspecto | Definición |
|---|---|
| **Propósito** | Expresar identidad principal y prioridad de acción del producto |
| **Jerarquía** | Alta, pero controlada |
| **Responsabilidades** | Acciones primarias, focos principales, elementos de marca, momentos de conducción visual |
| **Restricciones** | No debe convertirse en color de relleno permanente ni competir con semánticas de riesgo o resultado |

### 3.5 Secondary

| Aspecto | Definición |
|---|---|
| **Propósito** | Complementar la jerarquía de Brand sin reemplazarla |
| **Jerarquía** | Media |
| **Responsabilidades** | Apoyo contextual, diferenciación secundaria, acentos complementarios |
| **Restricciones** | No debe actuar como primario alterno continuo ni introducir una segunda narrativa dominante |

### 3.6 Neutral

| Aspecto | Definición |
|---|---|
| **Propósito** | Sostener legibilidad, estructura y descanso visual |
| **Jerarquía** | Base estructural del sistema |
| **Responsabilidades** | Fondos, superficies, texto, bordes, divisores, estados pasivos, capas de soporte |
| **Restricciones** | No debe colapsar jerarquías ni volver indistinguibles texto, superficie y separación |

### 3.7 Positive

| Aspecto | Definición |
|---|---|
| **Propósito** | Comunicar resultado favorable, ganancia o salud del sistema |
| **Jerarquía** | Alta dentro de contexto semántico |
| **Responsabilidades** | Ingresos, éxito, crecimiento, confirmación positiva, estados saludables |
| **Restricciones** | No debe reemplazar Brand ni usarse para decoración general |

### 3.8 Negative

| Aspecto | Definición |
|---|---|
| **Propósito** | Comunicar pérdida, error, riesgo o destrucción |
| **Jerarquía** | Alta y sensible |
| **Responsabilidades** | Gastos, deuda crítica, errores, acciones destructivas, fallos importantes |
| **Restricciones** | No debe emplearse como acento común ni crear ansiedad visual persistente |

### 3.9 Information

| Aspecto | Definición |
|---|---|
| **Propósito** | Comunicar proceso, transferencia o estado informativo neutral |
| **Jerarquía** | Media a alta según contexto |
| **Responsabilidades** | Transferencias, sincronización, procesos, feedback no positivo ni negativo |
| **Restricciones** | No debe confundirse con Brand ni con Positive |

### 3.10 Warning

| Aspecto | Definición |
|---|---|
| **Propósito** | Señalar atención o revisión necesaria sin implicar destrucción inmediata |
| **Jerarquía** | Media a alta, pero puntual |
| **Responsabilidades** | Umbrales cercanos, alertas preventivas, recordatorios importantes, riesgo moderado |
| **Restricciones** | No debe utilizarse como color protagonista persistente ni como sustituto de Negative |

### 3.11 Reglas transversales de color
- Toda semántica financiera debe ser estable en listas, resúmenes, formularios y visualizaciones.
- Los colores de estado deben poder convivir con dark theme sin cambiar de significado.
- Los alias de color deben mapear roles del sistema, no módulos específicos.
- Ningún color importante debe operar sin soporte de estructura, texto o iconografía cuando el riesgo interpretativo sea alto.

### 3.12 Qué debe evitarse
- tokens duplicados por naming diferente y misma intención;
- familias paralelas como “accent”, “special” o “custom” sin definición sistémica;
- creación de colores por feature;
- usar “brand” para expresar éxito, error o warning.

---

## 4. Taxonomía de Surface Tokens

### 4.1 Principio general
Los Surface Tokens deben modelar la jerarquía espacial y perceptiva del producto. La superficie es la capa donde vive el contenido; no debe depender solo de color ni solo de elevación.

### 4.2 Niveles oficiales de superficie
- **Canvas Surface**
- **Primary Surface**
- **Secondary Surface**
- **Emphasis Surface**
- **Overlay Surface**
- **Critical Surface**

### 4.3 Definición de niveles

| Nivel | Propósito | Responsabilidad principal | Restricción |
|---|---|---|---|
| **Canvas Surface** | Plano base | Sostener el contexto general de la pantalla | No debe competir con el contenido |
| **Primary Surface** | Contenedor principal | Alojar módulos primarios de lectura e interacción | No debe multiplicarse sin criterio |
| **Secondary Surface** | Subestructura | Separar grupos funcionales dentro de otra superficie | No debe parecer otra capa equivalente al nivel principal |
| **Emphasis Surface** | Énfasis moderado | Destacar síntesis, métricas o bloques de valor | No debe convertir todo en protagonista |
| **Overlay Surface** | Transitoriedad | Menús, sheets, diálogos y capas flotantes | No debe sentirse como fondo estable |
| **Critical Surface** | Sensibilidad | Alojar riesgos, destrucción o advertencias serias | No debe usarse para contenido cotidiano |

### 4.4 Reglas de relación
- Una superficie puede heredar semántica de color, pero no debe depender exclusivamente de ella.
- La diferencia entre superficies hermanas debe ser consistente en toda la app.
- Una overlay debe conservar prioridad perceptiva sin romper sobriedad.
- Una critical surface debe sentirse inequívoca sin teatralidad.

### 4.5 Qué debe evitarse
- crear niveles intermedios no documentados;
- usar superficie de énfasis para módulos rutinarios;
- usar overlay semantics en contenedores persistentes;
- usar critical surface para llamar atención de marketing o decoración.

---

## 5. Elevation Tokens

### 5.1 Principio general
Los Elevation Tokens deben representar profundidad funcional, no estilo ornamental.

### 5.2 Niveles oficiales
- **Level 0**
- **Level 1**
- **Level 2**
- **Level 3**
- **Level 4**

### 5.3 Significado de niveles

| Nivel | Significado sistémico | Uso esperado |
|---|---|---|
| **Level 0** | Plano estructural | Base, canvas, fondo, estructura estable |
| **Level 1** | Separación mínima | Cards y contenedores comunes |
| **Level 2** | Interacción o énfasis moderado | Selección, foco contextual, panel destacado |
| **Level 3** | Transitoriedad clara | Menús, dropdowns, panels flotantes |
| **Level 4** | Interrupción o prioridad alta | Diálogos, confirmaciones críticas, capas modales relevantes |

### 5.4 Reglas de uso
- Los niveles deben ser pocos, legibles y consistentes.
- Un cambio de elevación debe comunicar un cambio de estado o capa.
- La mayoría del producto debe concentrarse en niveles bajos.
- Elevación y superficie deben colaborar; ninguna debe reemplazar totalmente a la otra.

### 5.5 Qué debe evitarse
- niveles especiales por pantalla;
- elevaciones únicas para resolver detalle visual local;
- saltos de profundidad sin narrativa de interacción;
- equivalentes semánticos con niveles distintos según la feature.

---

## 6. Shape Tokens

### 6.1 Principio general
Los Shape Tokens deben regular la identidad geométrica del producto y evitar que cada módulo defina su propio radio o lenguaje formal.

### 6.2 Niveles oficiales
- **extraSmall**
- **small**
- **medium**
- **large**
- **extraLarge**
- **full** como excepción funcional

### 6.3 Significado de la jerarquía

| Nivel | Carácter | Uso esperado |
|---|---|---|
| **extraSmall** | Preciso | Microcontenedores, indicadores pequeños, etiquetas compactas |
| **small** | Compacto | Inputs simples, chips, controles pequeños |
| **medium** | Equilibrado | Bloques de apoyo y módulos intermedios |
| **large** | Estándar del producto | Cards y contenedores frecuentes |
| **extraLarge** | Protagónico | Bloques principales, resúmenes, overlays de alto nivel |
| **full** | Excepción funcional | Avatares, pills, formas circulares o cápsula justificadas |

### 6.4 Reglas de uso
- El shape debe escalar con rol y jerarquía, no con gusto local.
- “full” debe ser excepcional y funcional, no dominante.
- Componentes equivalentes deben compartir nivel de shape.
- La jerarquía de shapes debe sentirse continua entre modos y plataformas.

### 6.5 Qué debe evitarse
- radios no pertenecientes a la escala oficial;
- componentes hermanos con shapes incompatibles;
- usar shape como única señal de estado;
- proliferación de cápsulas o círculos sin razón funcional.

---

## 7. Spacing Tokens

### 7.1 Principio general
Los Spacing Tokens deben definir ritmo, proximidad y respiración visual con una escala única y transversal.

### 7.2 Escala conceptual oficial
- **2XS**
- **XS**
- **S**
- **M**
- **L**
- **XL**
- **2XL**
- **3XL**

### 7.3 Semántica de la escala

| Token | Función principal | Tipo de relación |
|---|---|---|
| **2XS** | Microajuste | Relación casi inmediata |
| **XS** | Compactación controlada | Elementos muy relacionados |
| **S** | Proximidad primaria | Label y dato, icono y texto |
| **M** | Ritmo base | Padding y separación estándar |
| **L** | Agrupación amplia | Subgrupos dentro de un bloque |
| **XL** | Separación de sección | Cambio de módulo dentro de pantalla |
| **2XL** | Cambio fuerte de contexto | Transición entre zonas importantes |
| **3XL** | Apertura estructural | Respiro mayor del layout |

### 7.4 Reglas de uso
- El spacing debe mapear proximidad semántica.
- No deben existir gaps “intermedios inventados” fuera de la escala.
- El ritmo vertical y horizontal debe ser compatible entre features.
- Formularios, listas y paneles deben apoyarse en la misma gramática espacial.

### 7.5 Qué debe evitarse
- escalas locales por componente;
- valores apenas distintos para intenciones iguales;
- usar spacing para compensar jerarquías mal resueltas;
- densidad tan extrema que pierda agrupación semántica.

---

## 8. Typography Tokens

### 8.1 Principio general
Los Typography Tokens deben traducir jerarquía de información, tono del producto y prioridad financiera en una estructura tipográfica estable.

### 8.2 Familias oficiales de jerarquía
- **Display**
- **Headline**
- **Title**
- **Body**
- **Label**
- **Numeric Emphasis**

### 8.3 Responsabilidad por familia

| Familia | Propósito | Uso esperado | Restricción |
|---|---|---|---|
| **Display** | Comunicación excepcional | Mensajes heroicos puntuales | No debe invadir flujos rutinarios |
| **Headline** | Encabezado principal | Pantallas, bloques de síntesis, saldos protagonistas | No debe usarse para texto secundario |
| **Title** | Título funcional | Cards, secciones, módulos, items relevantes | No debe competir con Headline sin justificación |
| **Body** | Lectura principal | Explicaciones, textos de apoyo, contenido descriptivo | No debe cargar jerarquía excesiva |
| **Label** | Soporte contextual | Metadata, captions, controles compactos, chips | No debe sustituir texto principal |
| **Numeric Emphasis** | Prioridad financiera | Importes, balances, porcentajes, variaciones | No debe usarse para texto narrativo |

### 8.4 Subniveles
Cada familia tipográfica debe poder admitir, cuando aplique, subniveles internos como:
- strong;
- default;
- compact;
- supportive.

La existencia de subniveles no debe romper la jerarquía principal.

### 8.5 Reglas de uso
- Las cifras financieras deben tener legibilidad prioritaria.
- La tipografía debe separar claramente lectura narrativa y lectura numérica.
- Los aliases tipográficos deben describir rol, no pantalla.
- La accesibilidad tipográfica debe considerarse parte del contrato del token.

### 8.6 Qué debe evitarse
- crear estilos tipográficos únicos por feature;
- resolver jerarquía mediante tamaño aislado sin semántica;
- mezclar styles numéricos con cuerpos de texto generales;
- introducir familias paralelas sin gobierno del sistema.

---

## 9. Motion Tokens

### 9.1 Principio general
Los Motion Tokens deben gobernar el tono, la velocidad relativa y la función del movimiento sin convertirlo en un sistema ornamental.

### 9.2 Categorías oficiales
- **Enter**
- **Exit**
- **Emphasis**
- **Feedback**
- **Transition**
- **Progress**
- **Reduced Motion**

### 9.3 Responsabilidad por categoría

| Categoría | Qué regula | Uso esperado |
|---|---|---|
| **Enter** | Aparición de contenido | Menús, overlays, bloques contextuales |
| **Exit** | Desaparición de contenido | Cierre de capas, dismiss, retirada de feedback |
| **Emphasis** | Señalización puntual | Selección, foco, highlight moderado |
| **Feedback** | Respuesta del sistema | Confirmaciones, errores, cambios de estado |
| **Transition** | Continuidad espacial o de contexto | Navegación, cambio de layout, expansión o colapso |
| **Progress** | Estados de proceso | Carga, sincronización, trabajo en curso |
| **Reduced Motion** | Adaptación accesible | Alternativas sobrias para contextos sensibles |

### 9.4 Reglas de motion
- El movimiento debe explicar cambio, no decorarlo.
- La jerarquía del movimiento debe ser consistente con la jerarquía del contenido.
- Las animaciones críticas deben ser claras y discretas.
- Reduced Motion debe formar parte de la arquitectura, no ser un parche posterior.

### 9.5 Qué debe evitarse
- microanimaciones sin significado;
- categorías distintas para resolver el mismo patrón;
- motion diferente por feature para el mismo tipo de transición;
- usar motion para llamar atención cuando la jerarquía visual debería resolverlo.

---

## 10. State Tokens

### 10.1 Principio general
Los State Tokens deben estandarizar cómo varía el sistema cuando un elemento cambia de disponibilidad, selección, proceso o resultado.

### 10.2 Estados oficiales del sistema
- **Enabled**
- **Disabled**
- **Focused**
- **Hovered** cuando aplique a contexto multiplataforma
- **Pressed**
- **Selected**
- **Error**
- **Loading**
- **Success**
- **Warning**
- **ReadOnly**
- **Active**
- **Inactive**

### 10.3 Responsabilidad de los estados

| Estado | Qué comunica | Restricción |
|---|---|---|
| **Enabled** | Disponibilidad normal | No debe confundirse con protagonismo |
| **Disabled** | No interactuable | No debe perder legibilidad esencial |
| **Focused** | Atención y navegación | Debe ser claro y accesible |
| **Hovered** | Anticipación de interacción | Solo cuando el entorno lo justifique |
| **Pressed** | Acción en curso inmediata | Debe sentirse breve y precisa |
| **Selected** | Elección persistente | Debe diferenciarse de foco o press |
| **Error** | Fallo o invalidación | Debe ser inequívoco y sobrio |
| **Loading** | Proceso activo | Debe indicar espera sin ansiedad excesiva |
| **Success** | Resultado correcto | Debe confirmar sin teatralidad |
| **Warning** | Atención preventiva | No debe escalar automáticamente a error |
| **ReadOnly** | Visible pero no editable | Debe preservar comprensión sin parecer deshabilitado |
| **Active** | Estado actualmente operativo | No debe sustituir selected cuando el patrón requiere selección |
| **Inactive** | Estado no operativo o pasivo | Debe conservar contexto suficiente |

### 10.4 Reglas de modelado
- Los State Tokens no deben definirse como colores aislados; deben poder impactar múltiples familias.
- Un mismo estado puede modificar color, superficie, elevación, borde, motion o tipografía según contexto.
- La semántica del estado debe ser común a todo el sistema.
- El componente decide cómo consume el estado; el token define qué significa.

### 10.5 Qué debe evitarse
- estados inventados por feature;
- usar selected para expresar active o viceversa sin criterio;
- modelar loading y disabled como equivalentes;
- esconder error o success únicamente en color.

---

## 11. Relación entre Tokens y Material 3

### 11.1 Principio general
Xpendz debe convivir con Material 3, no reemplazarlo. Material 3 provee una base estructural robusta; los tokens de Xpendz agregan semántica de producto, coherencia financiera y gobierno a largo plazo.

### 11.2 Modelo de convivencia

| Capa | Rol de Material 3 | Rol de Xpendz |
|---|---|---|
| **Fundación de framework** | Provee primitives, roles base y contratos visuales | Adopta y adapta sin romper compatibilidad |
| **Semántica de producto** | No define significado específico del dominio financiero | Define intención de negocio y tono del producto |
| **Asignación a componentes** | Ofrece slots y roles estándar | Mapea tokens del producto a los slots adecuados |
| **Gobernanza evolutiva** | Provee lineamientos generales | Define reglas internas de coherencia y migración |

### 11.3 Reglas de convivencia
- Cuando Material 3 ya ofrece un rol suficiente, Xpendz debe mapearse sobre él antes de inventar otro.
- Cuando el dominio financiero requiera semántica adicional, esta debe vivir en tokens del producto, no forzarse dentro de un rol equivocado de Material 3.
- Los tokens de Xpendz deben actuar como capa de intención; Material 3, como capa de soporte e implementación visual estándar.
- El sistema no debe forkear Material 3 sin necesidad real y documentada.

### 11.4 Qué debe evitarse
- duplicar todos los roles de Material 3 sin justificación;
- ignorar roles nativos que ya resuelven el problema;
- usar tokens del producto para describir internals del framework;
- mezclar semántica Material 3 y semántica de negocio en un mismo nombre de token.

---

## 12. Estrategia de implementación futura

### 12.1 Principio general
La implementación futura debe seguir el orden de menor ambigüedad a mayor especialización. Primero se consolidan fundaciones y semántica; después alias y consumo por componentes.

### 12.2 Orden recomendado

#### Fase 1 — Auditoría de mapeo
- identificar valores repetidos ya existentes;
- agruparlos por intención semántica;
- detectar duplicaciones y excepciones.

#### Fase 2 — Fundaciones
- formalizar foundation tokens de color, spacing, shape, elevation, typography y motion;
- asegurar que las escalas sean completas y cerradas.

#### Fase 3 — Tokens semánticos
- construir la capa semántica oficial del producto;
- separar claramente semántica visual general y semántica financiera.

#### Fase 4 — Alias de sistema
- mapear tokens semánticos a slots del sistema y a roles equivalentes de Material 3;
- crear contratos de consumo consistentes.

#### Fase 5 — State Tokens transversales
- definir cómo los estados alteran las familias existentes;
- estandarizar combinaciones de estado por tipo de patrón.

#### Fase 6 — Consumo por primitives
- aplicar tokens a primitives y componentes base;
- validar consistencia entre navegación, inputs, feedback y overlays.

#### Fase 7 — Migración por features
- migrar pantallas por dominio funcional;
- eliminar valores locales redundantes;
- documentar excepciones reales y descartes.

#### Fase 8 — Validación sistémica
- revisar dark theme;
- revisar accesibilidad;
- revisar consistencia de estados;
- revisar visualizaciones y semántica financiera.

### 12.3 Prioridad recomendada de familias
1. Color Tokens
2. Surface Tokens
3. Elevation Tokens
4. Shape Tokens
5. Spacing Tokens
6. Typography Tokens
7. State Tokens
8. Motion Tokens

### 12.4 Criterios de finalización por fase
Una fase debe considerarse madura cuando:
- las reglas están documentadas;
- no existen duplicaciones semánticas relevantes;
- los aliases son comprensibles;
- las excepciones están justificadas;
- el sistema puede escalar sin añadir una segunda gramática paralela.

---

## Criterio final de esta especificación

Si esta especificación está bien construida, el siguiente paso técnico no consistirá en “elegir estilos”, sino en mapear una arquitectura ya decidida.

Ese es el propósito de este documento: convertir el Design System de Xpendz en una estructura de tokens implementable, gobernable y estable a largo plazo.