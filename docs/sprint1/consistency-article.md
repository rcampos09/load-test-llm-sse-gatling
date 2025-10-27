# Load Testing de LLMs con Análisis de Consistencia: Mi Viaje desde la Teoría hasta Datos Brutales

**Fecha**: Octubre 2025
**Autor**: Ricardo Campos
**Contexto**: Load Testing de APIs LLM con Server-Sent Events (SSE)
**Stack**: Gatling 3.11 + Java 11 + OpenAI GPT-3.5-turbo

---

## 🎯 El Problema que Quise Resolver

Cuando comencé este proyecto, tenía una pregunta simple pero crítica:

> **¿Cómo sé si mi API LLM mantiene la calidad de respuestas cuando está bajo carga?**

Las herramientas tradicionales de load testing (Gatling, JMeter, k6) me dan métricas de **performance**:
- ✅ Latencia (ms)
- ✅ Throughput (req/s)
- ✅ Tasa de error (%)

Pero NO responden preguntas de **calidad**:
- ❓ ¿Las respuestas se truncan bajo carga?
- ❓ ¿El LLM genera contenido inconsistente cuando está saturado?
- ❓ ¿La experiencia del usuario se degrada aunque el HTTP 200 diga "éxito"?

**El problema técnico específico**: Estaba haciendo load testing de la API de OpenAI con streaming SSE, y necesitaba saber si el sistema colapsaba en calidad (no solo en latencia) bajo presión.

---

## 💭 Mi Filosofía de Approach: Sprint 1 como MVP

Decidí NO esperar a tener la solución perfecta. En vez de eso, opté por:

✅ **Implementar algo funcional HOY** - No esperar 3 semanas investigando embeddings
✅ **Sin dependencias externas** - No quería depender de librerías complejas
✅ **Métricas cuantificables** - Un score de 0-1 objetivo
✅ **Transparencia brutal** - Reconocer limitaciones desde el inicio
✅ **Iterativo** - Sprint 1 (básico) → Sprint 2 (avanzado)

**Por qué este approach**: Necesitaba detectar problemas **esta semana**, no en 3 meses. Sprint 1 sería mi MVP para validar si el sistema es siquiera testeable.

---

## 🏗️ Lo Que Construí: Pipeline de 3 Etapas

### **Etapa 1: Captura Enriquecida de Metadatos**

Durante el load test con Gatling, implementé captura de **16 campos** por respuesta (vs los 4 originales que tenía):

```json
{
  "session_id": "1-Scenario",
  "chunk_id": "chatcmpl-xyz",
  "user_id": 1,
  "category": "short",
  "prompt": "¿Qué es la fotosíntesis?",
  "max_tokens": 200,
  "temperature": 0.7,
  "response": "[contenido completo capturado]",
  "response_length": 371,
  "timestamp": "2025-10-24T03:22:05.889Z",
  "response_time_ms": 2018,        // ← Timing manual end-to-end
  "ttft_ms": 6,                     // ← Time To First Token
  "total_chunks": 100,              // ← Chunks SSE procesados
  "truncated": false,               // ← Detección automática
  "truncation_reason": "NONE",      // ← TIMEOUT, BUFFER_OVERFLOW, NONE
  "test_phase": "RAMP"              // ← RAMP vs STEADY
}
```

**Detección automática de truncamiento:**
```java
// Timeout detection (max 10 seconds)
long elapsed = currentTime - requestStartTime;
boolean timedOut = elapsed > 10000;

// Detect truncation
boolean truncated = timedOut || !done;
String truncationReason = timedOut ? "TIMEOUT" : "NONE";
```

**Salida**: `responses_metadata.jsonl` (610 líneas, una por respuesta)

---

### **Etapa 2: Agregación por Dimensiones**

Creé `ResponseAggregator.java` para agrupar respuestas:

```java
Map<String, List<ResponseMetadata>> byPrompt = groupByPrompt();
Map<String, List<ResponseMetadata>> byCategory = groupByCategory();
Map<String, List<ResponseMetadata>> byPhase = groupByTestPhase();
```

**Salida**: `responses_by_prompt.json` con estadísticas por:
- Prompt específico (30 prompts únicos)
- Categoría (short, medium, long, creative, code_generation, etc.)
- Fase del test (RAMP vs STEADY)

---

### **Etapa 3: Análisis de Consistencia (Con Limitaciones Reconocidas)**

Implementé análisis en **5 dimensiones**. IMPORTANTE: Estos métodos son básicos y tienen limitaciones.

| Dimensión | Peso | Método | Efectividad Real |
|-----------|------|--------|------------------|
| **Completitud** | 25% | Detección de truncamiento | ✅ **MUY EFECTIVO** - Detectó 47.5% truncamiento |
| **Temporal** | 5% | Comparación RAMP vs STEADY | ✅ **EFECTIVO** - Detectó +775% degradación |
| **Categoría** | 5% | Análisis por tipo de prompt | ✅ **EFECTIVO** - 70% falla en long prompts |
| **Estructural** | 25% | Formato, longitud, idioma | ✅ **FUNCIONA BIEN** - Detecta cambios obvios |
| **Semántica** | 40% | Jaccard similarity keywords | ❌ **LIMITADO** - Muchos falsos positivos |

#### **Sobre el Análisis Semántico (Jaccard): Mi Mayor Limitación**

Implementé Jaccard similarity porque es simple:

```java
// 1. Extraer keywords con regex
Set<String> keywords1 = extractKeywords(response1);
Set<String> keywords2 = extractKeywords(response2);

// 2. Calcular similitud
double similarity = |intersection| / |union|;
```

**El problema que descubrí con datos reales:**

Score semántico obtenido: **0.306 (30.6%)** - parece muy bajo, pero incluye falsos positivos:

- ❌ Prompt "Propón nombres creativos" → Similarity 0.099
  - Cada respuesta da nombres diferentes → **Esto es CORRECTO, no error**

- ❌ Prompt "Genera eslogan" → Similarity 0.415
  - Creatividad esperada → **Variación legítima**

- ✅ Prompt "Implementa búsqueda binaria" → Similarity 0.278
  - Implementaciones técnicas diferentes → **Esto SÍ es problema**

**Conclusión honesta**: Jaccard NO distingue entre creatividad legítima vs inconsistencia técnica. Por eso Sprint 2 usará embeddings.

**Por qué lo usé igual**: Las otras 4 dimensiones SÍ funcionan (truncamiento, temporal, estructural, categoría). Un 80% de efectividad es mejor que 0%.

**Score Global**: Media ponderada de 5 dimensiones (0.0 - 1.0)

**Salida**: `consistency_analysis.json` con score global + issues detectados (algunos son falsos positivos)

---

## 🧪 La Ejecución del Test: Configuración Real

**Configuración Gatling:**
```java
setUp(
  prompt.injectOpen(
    rampUsers(10).during(10),           // RAMP: 10 usuarios en 10s
    constantUsersPerSec(10).during(60)  // STEADY: 10 usuarios/seg × 60s = 600
  )
).protocols(httpProtocol);
```

**Carga generada:**
- Fase RAMP: 10 usuarios gradualmente
- Fase STEADY: 600 usuarios (10/seg × 60s)
- **Total inyectado: 610 requests**
- **Concurrencia real: ~88 usuarios simultáneos** (10/seg × 8.8s latencia promedio)

**Prompts testeados:**
- **30 prompts individuales** distribuidos en 9 categorías
- Categorías: short, medium, long, creative, code_generation, analysis, troubleshooting, documentation, contextual
- **Cada prompt individual se ejecuta ~20 veces** bajo carga

**Distribución de prompts y requests por categoría:**

| Categoría | Prompts | Total Requests | Ejecuciones/prompt |
|-----------|---------|----------------|-------------------|
| short | 4 | 84 | 21 |
| medium | 5 | 105 | 21 |
| long | 4 | 81 | ~20 |
| contextual | 3 | 60 | 20 |
| code_generation | 4 | 80 | 20 |
| analysis | 3 | 60 | 20 |
| troubleshooting | 3 | 60 | 20 |
| documentation | 2 | 40 | 20 |
| creative | 2 | 40 | 20 |
| **TOTAL** | **30** | **610** | **~20.3** |

**Nota importante**: El número de requests por categoría **varía** (40-105) porque cada categoría tiene diferente cantidad de prompts (2-5), NO porque los prompts se ejecuten diferente cantidad de veces. Por ejemplo:
- `medium` tiene 105 requests = 5 prompts × 21 ejecuciones
- `creative` tiene 40 requests = 2 prompts × 20 ejecuciones

---

## 🔥 Hallazgos Críticos: Lo Que Los Datos Me Mostraron (Y Me Sorprendió)

**📌 Nota metodológica importante:**

A lo largo de estos hallazgos menciono **dos tipos de métricas de latencia**:

1. **Métricas de Gatling** (1,751ms): Solo miden el establecimiento de conexión HTTP hasta recibir HTTP 200 OK
2. **Métricas end-to-end manuales** (8,826ms): Miden el tiempo real desde request hasta recibir `[DONE]` en el stream SSE

Todas las comparaciones **RAMP vs STEADY** usan mediciones end-to-end manuales. El **Hallazgo #4** compara Gatling vs manual para mostrar el gap.

---

### **Hallazgo #1: Disponibilidad Alta, Calidad Baja (Score Global: 50.5%)**

**Primero, separemos dos conceptos críticos:**

| Concepto | Valor | Estado |
|----------|-------|--------|
| **Disponibilidad** (ChatGPT responde) | 98.36% (600/610) | ✅ EXCELENTE |
| **Calidad/Consistencia** (respuestas útiles) | 50.5% | ❌ CRÍTICO |

**Resultado esperado**: Score > 0.8 (80%) para considerar "aceptable"
**Resultado real**: Score = **0.505 (50.5%)** 🚨

**Desglose por dimensión:**

```
❌ Completitud:   0.525  (47.5% truncamiento - CRÍTICO)
✓  Estructural:   0.795  (formato mayormente consistente)
❌ Semántica:     0.306  (baja similitud - pero con falsos positivos)
❌ Temporal:      0.521  (degradación RAMP→STEADY)
✓  Categoría:    0.534  (varía por tipo de prompt)

🎯 Global Consistency Score: 0.505
```

**Mi interpretación correcta**:

- ✅ **ChatGPT SÍ responde casi siempre** (98.36% disponibilidad)
- ❌ **Pero la CALIDAD de las respuestas es inconsistente** bajo carga
- 🚨 **El problema NO es que "falle"**, sino que **entrega respuestas parciales/inconsistentes**

**Conclusión**: La API está disponible y funcional, pero la **calidad de las respuestas degrada severamente bajo carga**. NO está listo para producción **no por fallas de disponibilidad, sino por problemas de calidad**.

---

### **Hallazgo #2: Truncamiento Masivo (47.5% de Respuestas Incompletas)**

**El problema más grave que descubrí:**

| Métrica | Valor | Estado |
|---------|-------|--------|
| **Total requests enviados** | 610 | - |
| **ChatGPT respondió** | 600 (98.36%) | ✅ Alta disponibilidad |
| **Fallas de conexión** | 10 (1.64%) | ⚠️ Premature close |
| **Respuestas completas** | 320 (52.5%) | ✅ |
| **Respuestas truncadas** | 290 (47.5%) | ❌ CRÍTICO |
| **Razón del truncamiento** | 100% TIMEOUT (>10s) | ⚠️ |

**Distribución del truncamiento por categoría:**

| Categoría | Truncamiento | Interpretación |
|-----------|--------------|----------------|
| **short** | 8.3% | ✅ Funcional |
| **creative** | 10% | ✅ Aceptable |
| **medium** | 54.3% | ❌ Vulnerable |
| **code_generation** | 50% | ❌ Crítico |
| **troubleshooting** | 53.3% | ❌ Mal |
| **documentation** | 55% | ❌ Crítico |
| **contextual** | 56.7% | ❌ Crítico |
| **analysis** | 61.7% | 🚨 Muy crítico |
| **long** | **70.4%** | 🚨 **Extremadamente crítico** |

**Lo que aprendí**:
1. Prompts cortos/creativos funcionan bajo carga (<10% truncamiento)
2. Prompts largos/complejos se truncan masivamente (70% incompletos)
3. El timeout de 10s es **inadecuado** - el 47.5% de las respuestas necesita más tiempo
4. ChatGPT **SÍ responde**, pero muchas respuestas quedan **incompletas** antes de terminar

**Implicación brutal**: Si lanzo esto a producción, **1 de cada 2 usuarios recibirá respuestas incompletas** (no errores, sino respuestas cortadas a la mitad).

---

### **Hallazgo #3: Degradación Temporal Extrema (+775% Latencia, +47.85% Truncamiento)**

**Nota importante**: Las métricas de latencia son **mediciones manuales end-to-end** (desde request hasta `[DONE]`), NO las reportadas por Gatling.

Comparando fase RAMP vs STEADY:

| Métrica | RAMP (inicial) | STEADY (sostenida) | Degradación |
|---------|----------------|-------------------|-------------|
| **Respuestas** | 4 | 606 | - |
| **Latencia end-to-end** | 1,009ms | 8,826ms | **+775%** 🚨 |
| **Truncamientos** | 0% | **47.85%** | **+47.85%** 🚨 |

**Timeline de la degradación:**

```
t=0-10s (RAMP - baja concurrencia ~1-2 usuarios):
  ✅ ChatGPT responde rápido (1s end-to-end)
  ✅ 0% truncamiento
  ✅ Respuestas completas y rápidas

t=10-70s (STEADY - alta concurrencia ~88 usuarios):
  ⚠️ Latencia se dispara a 8.8s end-to-end (+775%)
  ❌ Truncamiento salta a 47.85%
  ❌ La mayoría de respuestas quedan incompletas

🎯 Comparación: ambas mediciones hasta recibir [DONE], NO mediciones de Gatling
```

**Lo que aprendí**:
- ChatGPT **SÍ responde** bajo carga sostenida (98.36% disponibilidad)
- Pero la **calidad degrada severamente**: latencia aumenta y respuestas se truncan
- El problema NO es de "caídas", sino de **deterioro de la experiencia de usuario**
- Esto **NO escala** para producción sin ajustes de timeout y manejo de backpressure

---

### **Hallazgo #4: Gap Brutal en Medición de Gatling (+403%)**

**Descubrimiento que cambió todo mi entendimiento del testing SSE:**

**Nota importante**: Esta comparación muestra la diferencia entre lo que **Gatling reporta** (solo HTTP connection) vs lo que **el usuario experimenta** (streaming completo hasta `[DONE]`).

Correlación entre Gatling Report vs Mi Análisis End-to-End:

| Métrica | Gatling Report | Mi Análisis (Real) | Gap |
|---------|----------------|-------------------|-----|
| **Mean Response Time** | 1,751ms | 8,826ms | **+403%** 🚨 |
| **P99 Response Time** | 17,958ms | No medido | - |
| **Qué mide** | Solo HTTP 200 OK | Hasta `[DONE]` | Realidad vs ilusión |

**¿Por qué esta diferencia?**

```
Timeline de un request SSE:

t=0ms      → Cliente envía request
t=1,751ms  → HTTP 200 OK recibido
             ↑
             GATLING DETIENE TIMER AQUÍ ⏱️
             (Cree que el request terminó)

t=1,752ms  → Comienza streaming SSE...
t=2,000ms  → Primer chunk (TTFT)
t=4,500ms  → Más chunks...
t=8,826ms  → [DONE] recibido
             ↑
             EXPERIENCIA REAL DEL USUARIO ⏱️
             (El request REALMENTE terminó)
```

**El problema**: Gatling mide solo el establecimiento de conexión HTTP, NO el streaming completo. El loop `asLongAs()` que procesa chunks **NO se mide**.

**Impacto en mi decisión**:
- ❌ Si hubiera confiado en Gatling: "Mi API responde en 1.7s, ¡perfecto!"
- ✅ Realidad del usuario: "Tu API responde en 8.8s, inaceptable"
- 🎯 Gap de **403%** entre lo que Gatling reporta y la experiencia real

**Aclaración importante**: Este gap (403%) es **diferente** a la degradación RAMP→STEADY (775%). Son dos comparaciones distintas:
- **403%**: Gatling (1,751ms) vs Real (8,826ms) - gap de medición
- **775%**: RAMP (1,009ms) vs STEADY (8,826ms) - degradación bajo carga

**Solución que implementé**: Timing manual end-to-end en el código Java de Gatling.

---

### **Hallazgo #5: Errores de Conexión Concentrados en Categoría "Medium" (5 KO)**

**Correlación entre Gatling Report y OpenAI Dashboard:**

| Fuente | Requests | Resultado |
|--------|----------|-----------|
| **Gatling (intentados)** | 610 | - |
| **Gatling (exitosos)** | 605 | 99.18% |
| **Gatling (fallidos)** | 5 KO | 0.82% |
| **OpenAI Dashboard** | 600 | Source of truth |
| **Discrepancia** | 10 requests | 1.64% |

**Errores detectados (Gatling Report):**
```
"Premature close"                        → 5 requests
"Stream already crashed"                 → 5 requests (adicionales)
────────────────────────────────────────────────────
Total errores SSE:                         10 requests
```

**Distribución de los 5 KO por categoría:**

```
Connect to LLM - medium:   105 total, 100 OK, 5 KO (4.76% error rate) ← TODOS LOS ERRORES
Connect to LLM - short:     84 total,  84 OK, 0 KO (0%)
Connect to LLM - long:      81 total,  81 OK, 0 KO (0%)
[resto: 0 KO]
```

**Lo que aprendí**:
1. Los **prompts medianos son los más frágiles** a errores de conexión bajo carga
2. "Premature close" = stream SSE se cortó abruptamente (timeout de red)
3. 1.64% error rate es **técnicamente aceptable** para testing
4. Pero en producción significa **1 de cada 60 usuarios ve un error** (inaceptable)

---

### **Hallazgo #6: Costos Reales ($0.30) vs Estimados ($0.61) - Ironía del Truncamiento**

**Datos del OpenAI Dashboard (24/10/2025):**

```
Total Spend:    $0.30
Total Tokens:   17,313 input
Total Requests: 600 (exitosos)
```

**Análisis de tokens:**

| Métrica | Valor | Observación |
|---------|-------|-------------|
| **Input tokens** | 17,313 | Prompts enviados |
| **Input promedio** | 28.9 tokens/req | Coherente (17,313 ÷ 600) |
| **Output tokens (calculado)** | ~200,000 | Del costo: $0.30 ÷ $1.50/1M |
| **Output promedio** | ~333 tokens/req | Reducido por truncamiento |

**Desglose de costos:**

```
Precios GPT-3.5-turbo:
- Input:  $0.50 / 1M tokens
- Output: $1.50 / 1M tokens

Costo real:
- Input:  17,313 × $0.50/1M  = $0.009
- Output: 200,000 × $1.50/1M = $0.30
────────────────────────────────────────
Total:                         $0.30
```

**La ironía que descubrí:**

Sin truncamiento (ideal):
```
600 requests × 655 tokens promedio = 393,000 tokens output
Costo estimado: $0.59
```

Con truncamiento (realidad):
```
600 requests × 333 tokens promedio = 200,000 tokens output
Costo real: $0.30
```

**Conclusión irónica**: El **problema de calidad** (truncamiento 47.5%) me **ahorró $0.29** (50% del costo). No es bueno, pero al menos no pagué por respuestas completas que no recibí.

**Implicaciones para testing continuo:**

| Escenario | Requests/día | Costo/día | Costo/mes |
|-----------|--------------|-----------|-----------|
| **Testing diario básico** | 100 | $0.05 | $1.50 |
| **Testing CI/CD (por PR)** | 600 | $0.30 | ~$9 (30 PRs/mes) |
| **Testing completo semanal** | 600 | $0.30 | $1.20 (4 tests/mes) |

**Lo que aprendí**: Este tipo de testing es **económicamente viable** ($9/mes en CI/CD). Mucho más barato que testing manual.

---

## 📊 Análisis de Consistencia: Qué Funcionó y Qué No

**Nota de transparencia**: Esta sección reporta **solo lo que realmente medí y validé** en Sprint 1. No incluyo métricas inventadas ni afirmaciones sin evidencia.

---

### **✅ Las 3 Dimensiones Que Funcionaron Perfectamente**

#### **1. Detección de Truncamiento - Crítico y 100% Efectivo**

**Implementación:**
```java
boolean truncated = (elapsed > 10000) || !done;
```

**Resultados reales del test:**
- ✅ Detectó **290 de 610 respuestas truncadas** (47.5%)
- ✅ Todas habrían pasado como HTTP 200 OK sin esta validación
- ✅ Sin esta métrica, habría lanzado a producción un sistema con 47.5% de respuestas incompletas

**Por qué funcionó**: Criterio simple y objetivo - si el stream no envía `[DONE]` o tarda >10s, está truncado.

**Evidencia de valor**: Este fue el **hallazgo más crítico** del test. Sin esta dimensión, todo el análisis habría sido inútil.

---

#### **2. Análisis Temporal (RAMP vs STEADY) - 100% Efectivo**

**Implementación:**
```java
String phase = (timeSinceStart < rampDuration) ? "RAMP" : "STEADY";
```

**Resultados reales del test:**

| Fase | Respuestas | Latencia promedio | Truncamiento | Evidencia |
|------|------------|-------------------|--------------|-----------|
| RAMP | 4 | 1,009ms | 0% | ✅ Sistema funcional |
| STEADY | 606 | 8,826ms | 47.85% | ❌ Degradación severa |

**Por qué funcionó**: Capturé el timestamp exacto de cada request y lo comparé contra la duración de RAMP (10s).

**Evidencia de valor**: Reveló que el problema NO es ChatGPT en sí, sino la **degradación bajo carga sostenida** (+775% latencia).

---

#### **3. Análisis por Categoría de Prompt - 100% Efectivo**

**Implementación:** Cada prompt tiene tag de categoría (`short`, `medium`, `long`, etc.)

**Resultados reales del test:**

| Categoría | Truncamiento | Latencia promedio | Conclusión |
|-----------|--------------|-------------------|------------|
| short | 8.3% | ~4s | ✅ Funcional bajo carga |
| creative | 10% | ~4s | ✅ Aceptable |
| medium | 54.3% | ~9.7s | ⚠️ Vulnerable |
| long | **70.4%** | >10s | 🚨 No funcional |

**Por qué funcionó**: Categorización manual en `prompts.csv` asegura 100% de precisión.

**Evidencia de valor**: Demostró que necesito **SLAs diferentes por categoría** - un timeout global de 10s NO funciona.

---

### **⚠️ Dimensión Implementada Pero NO Validada Exhaustivamente**

#### **4. Análisis Estructural (Formato, Idioma, Longitud)**

**Implementación en código:**
```java
// Detecta cambio de idioma
boolean languageChanged = !detectLanguage(response).equals("es");

// Detecta cambios de formato
boolean formatChanged = containsMarkdown(response) != containsMarkdown(baseline);
```

**Estado real:**
- ✅ El código **existe** en `ConsistencyAnalyzer.java`
- ✅ Se ejecuta en cada análisis
- ❌ **NO validé manualmente** los resultados
- ❌ **NO documenté** falsos positivos vs verdaderos positivos
- ❌ **NO presenté** resultados concretos en este artículo

**Por qué NO lo validé**:
- Requiere revisión manual de 610 respuestas
- Enfoqué Sprint 1 en métricas críticas (truncamiento, temporal)
- Score estructural (0.795) sugiere que funciona, pero **no lo verifiqué**

**Acción honesta**: Esta dimensión debe ser **validada en Sprint 2** antes de confiar en ella.

---

### **❌ Dimensión Que Tiene Limitaciones Severas**

#### **5. Análisis Semántico con Jaccard Similarity - Muchos Falsos Positivos**

**Implementación:**
```java
// Compara palabras únicas entre respuestas del mismo prompt
double similarity = intersection / union;
```

**Problema fundamental:** Jaccard NO distingue **creatividad legítima** vs **inconsistencia técnica**.

**Evidencia real de falsos positivos:**

| Prompt | Score Jaccard | ¿Es problema real? | Veredicto |
|--------|---------------|-------------------|-----------|
| "Propón nombres creativos" | 0.099 | ❌ NO - variación esperada | Falso positivo |
| "Genera eslogan fitness" | 0.415 | ❌ NO - creatividad correcta | Falso positivo |
| "Implementa búsqueda binaria" | 0.278 | ✅ SÍ - inconsistencia técnica | Verdadero positivo |
| "Explica patrón Observer" | 0.248 | ❓ Incierto sin revisión manual | No validado |

**Por qué tiene limitaciones:**
- Compara **keywords literales**, no **significado semántico**
- Para prompts creativos, baja similitud es **deseable**
- Para prompts técnicos, baja similitud es **problema**
- **No tengo forma automática de distinguirlos** con Jaccard

**Transparencia brutal:** El score semántico global (0.306) está **inflado por falsos positivos**. NO confío en este número.

**Solución (Sprint 2):** Embeddings (OpenAI text-embedding-3) para similitud semántica real.

---

### **❌ Lo Que Directamente NO Pude Analizar**

Sprint 1 NO midió las siguientes dimensiones (requieren herramientas avanzadas):

| Dimensión | ¿Por qué no? | Herramienta necesaria |
|-----------|--------------|----------------------|
| **Calidad semántica profunda** | ¿La respuesta responde correctamente? | LLM-as-a-judge |
| **Alucinaciones** | ¿El LLM inventó datos? | Fact-checking con base de conocimiento |
| **Tono y estilo** | ¿Varía significativamente? | Embeddings + clustering |
| **Corrección factual** | ¿Los hechos son correctos? | Knowledge base + validation |
| **Coherencia lógica** | ¿La respuesta tiene sentido? | LLM-as-a-judge |

**Sprint 2 abordará 3 de estas:**
1. Calidad semántica → LLM-as-a-judge (GPT-4 como evaluador)
2. Similitud semántica → Embeddings (OpenAI text-embedding-3)
3. Coherencia lógica → Integración con DeepEval

---

### **🎯 Resumen Ejecutivo: ¿Qué Puedo Confiar de Sprint 1?**

| Dimensión | Confianza | Por qué |
|-----------|-----------|---------|
| **Truncamiento** | 100% ✅ | Datos objetivos: 290/610 truncadas |
| **Temporal** | 100% ✅ | Datos objetivos: RAMP 1s vs STEADY 8.8s |
| **Categoría** | 100% ✅ | Datos objetivos: long 70% vs short 8% |
| **Estructural** | 50% ⚠️ | Código existe, pero NO validado |
| **Semántica (Jaccard)** | 30% ❌ | Muchos falsos positivos confirmados |

**Conclusión honesta:**
- Las **3 dimensiones críticas funcionan perfectamente** (truncamiento, temporal, categoría)
- Estas 3 son **suficientes para detectar problemas de calidad** bajo carga
- Jaccard tiene limitaciones, pero **Sprint 2 lo reemplazará** con embeddings

---

## 💡 Las 7 Lecciones Más Importantes Que Aprendí (Y Los Errores Que Cometí)

### **Lección #1: "¡Funciona perfecto!" (Spoiler: No funcionaba)**

Cuando hice mi primer test pequeño con 20 requests, el sistema me devolvió un **score de 1.0 (100% perfecto)**.

Mi reacción honesta: "¡Ya está! Esto funciona increíble."

Luego ejecuté el test real con 610 requests y me encontré con un **score de 0.505** y **47.5% de respuestas truncadas**.

**Lo que realmente pasó:** Los tests pequeños son como ensayar tu presentación con tu gato - todo parece perfecto hasta que lo haces frente a 100 personas. Los problemas de carga **solo aparecen bajo carga real**. 20 requests no estresan nada, 610 sí.

**Mi error:** Confié en un test de validación que solo confirmaba que "el código no explotaba", no que funcionara bajo presión.

**Lo que cambió:** Ahora sé que si no duele (concurrencia, latencia, errores), no estás testeando lo suficiente.

---

### **Lección #2: Gatling me mintió (y yo le creí todo)**

Gatling me dijo: "605 requests OK (99.18% success rate) - todo bien!"

Yo pensé: "Excelente, casi todo funciona."

Pero cuando revisé el **contenido** de esas 605 respuestas "exitosas", descubrí que **290 estaban truncadas** (47.5% del total).

**Lo que realmente pasó:**
- Gatling mide si el server **respondió** (HTTP 200 OK)
- NO mide si la respuesta está **completa** (tiene `[DONE]`)
- Para Gatling, una respuesta cortada a la mitad = éxito ✅

**El momento "WTF":** Ver que el 99.18% de "éxito" incluía respuestas como:
```
"Para implementar búsqueda binaria en Java debes: 1. Ordenar el arr"
[TRUNCADO - falta el 70% de la respuesta]
```

**Lo que aprendí (a la fuerza):** Para LLMs, HTTP 200 OK es **solo el inicio**. Tienes que validar el contenido o estás volando a ciegas pensando que todo funciona cuando la mitad de tus usuarios reciben respuestas inútiles.

---

### **Lección #3: El día que descubrí que Gatling mide otra cosa (+403% de diferencia)**

Estaba analizando el reporte de Gatling y vi: "Mean Response Time: 1,751ms"

Mi reacción: "¡Wow, la API responde en menos de 2 segundos! Eso es rápido."

Luego implementé **timing manual** hasta que llegara `[DONE]` y me dio: **8,826ms**

Hice la cuenta: (8,826 - 1,751) / 1,751 = **+403% de diferencia**

**El momento de confusión total:** ¿Cómo puede haber una diferencia de 403%? ¿Qué está midiendo Gatling?

**La respuesta (que me tomó horas entender):**
- Gatling mide: "¿Cuánto tarda en recibir HTTP 200 OK?" → 1,751ms
- Yo necesito: "¿Cuánto tarda el usuario en ver la respuesta completa?" → 8,826ms
- El streaming SSE (donde van todos los tokens) **NO se cuenta** en Gatling

**Lo que esto significa:** Si hubiera confiado en Gatling para definir mi SLA, habría dicho "timeout de 3 segundos es suficiente" y **truncado el 80% de las respuestas**.

**Lección brutal:** Las herramientas tradicionales de load testing **NO fueron diseñadas para LLMs con streaming**. Necesitas medir end-to-end o terminas optimizando la métrica equivocada.

---

### **Lección #4: "Funciona bien... espera, ¿por qué se está rompiendo?"**

Durante los primeros 10 segundos del test (RAMP phase):
- Latencia: 1s
- Truncamiento: 0%
- Yo: "¡Perfecto, todo funciona!"

A partir del segundo 10 (STEADY phase con ~88 usuarios concurrentes):
- Latencia: 8.8s (+775%)
- Truncamiento: 47.85%
- Yo: "¿Qué carajo pasó?"

**El momento de pánico:** Ver cómo el sistema que funcionaba "perfecto" se degrada en tiempo real cuando aumenta la concurrencia.

**Lo que aprendí:** Los sistemas NO fallan de golpe. Se **degradan gradualmente** bajo carga. Si solo miras promedios globales, no ves el momento exacto donde todo empieza a romperse.

Por eso ahora capturo `test_phase` - necesito saber **cuándo** empieza el problema, no solo que existe.

---

### **Lección #5: "¿Por qué los prompts largos siempre fallan?"**

Configuré un timeout global de 10 segundos pensando: "Es razonable, ¿no?"

Resultados por categoría:
- Prompts cortos (short): 8.3% truncamiento → Funciona bien ✅
- Prompts largos (long): 70.4% truncamiento → Literalmente no funciona 🚨

**Mi error:** Pensé que todos los prompts eran iguales.

**La realidad:** Un prompt que pide "Define IA en una frase" termina en 2 segundos. Un prompt que pide "Diseña una arquitectura de microservicios completa" necesita 15-20 segundos.

**El momento "obvio en retrospectiva":** Claro que necesitan tiempos diferentes - generan respuestas de 50 tokens vs 1,500 tokens. ¿Por qué esperaba que terminaran al mismo tiempo?

**Lo que cambiaré:** SLAs específicos por categoría:
- `short/creative`: 5s timeout
- `medium/code`: 12s timeout
- `long/analysis`: 20s timeout

O circuit breakers inteligentes que detecten el tipo de prompt y ajusten el timeout automáticamente.

---

### **Lección #6: Jaccard similarity me dio falsos positivos todo el tiempo**

Implementé Jaccard similarity pensando: "Es simple pero efectivo."

Luego vi estos resultados:

**Prompt:** "Propón nombres creativos para una startup"
- **Score Jaccard:** 0.099 (muy baja similitud)
- **Mi análisis:** "¡Problema! Las respuestas son muy diferentes!"
- **Realidad:** Las respuestas DEBEN ser diferentes - es un prompt creativo 🤦

**Prompt:** "Implementa búsqueda binaria en Java"
- **Score Jaccard:** 0.278 (baja similitud)
- **Mi análisis:** "Posible problema... pero ¿es real?"
- **Realidad:** Las implementaciones varían (algunos usan recursión, otros iteración), pero todas son correctas

**El problema:** Jaccard compara palabras literales. No entiende que:
- "startup" y "emprendimiento" son sinónimos
- Dos implementaciones diferentes pueden ser ambas correctas
- Creatividad NO es inconsistencia

**Lo que aprendí (a la mala):** Las métricas simples tienen **limitaciones severas** cuando trabajas con lenguaje. No confío en el score semántico (0.306) - sé que está lleno de falsos positivos que no validé manualmente.

**Sprint 2:** Embeddings de OpenAI para medir similitud semántica REAL, no solo coincidencia de palabras.

---

### **Lección #7: Siempre valida con el source of truth (me salvó de un error)**

Mis números locales:
- Gatling: 610 requests intentados
- responses_metadata.jsonl: 610 líneas

Pensé: "Todo concuerda, perfecto."

Luego revisé el **dashboard de OpenAI** y vi: **600 requests**

**Mi reacción:** "Espera... falta 10. ¿Dónde están?"

Revisé los logs de Gatling y encontré:
- 5 errores "Premature close"
- 5 errores "Stream already crashed"
- Total: 10 requests que fallaron en conectarse

**Lo que me salvó:** Si hubiera confiado solo en mis datos locales, habría reportado "610 requests exitosos" cuando en realidad **10 nunca llegaron a ChatGPT**.

**Lección simple pero crítica:** El dashboard del proveedor es tu **source of truth**. Tus métricas locales pueden mentir (bugs, timeouts, errores de red). Los datos del proveedor son lo que realmente pasó.

Ahora siempre comparo:
- Mis datos locales ← pueden tener bugs
- Dashboard del proveedor ← realidad objetiva
- Si discrepancia >5% → tengo un bug de medición

---

## 🚀 Sprint 2: Hacia Dónde Voy (Herramientas Avanzadas)

Sprint 1 me dio un sistema funcional que detecta problemas críticos (truncamiento, degradación temporal). Pero tiene limitaciones en análisis semántico.

### **Objetivos de Sprint 2**

1. **Análisis semántico real con embeddings**
2. **Detección de alucinaciones**
3. **Evaluación cualitativa con LLM-as-a-judge**
4. **Comparación Sprint 1 vs Sprint 2**
5. **Dashboard HTML interactivo**

---

### **Herramientas a Implementar**

#### **1. OpenAI Embeddings + Cosine Similarity**

```python
from openai import OpenAI
import numpy as np

# Generate embeddings
emb1 = client.embeddings.create(input=response1, model="text-embedding-3-small")
emb2 = client.embeddings.create(input=response2, model="text-embedding-3-small")

# Cosine similarity
similarity = np.dot(emb1, emb2) / (np.linalg.norm(emb1) * np.linalg.norm(emb2))
```

**Ventaja sobre Jaccard**: Captura similitud semántica (sinónimos, parafraseo), no solo keywords literales.

---

#### **2. LangChain Evaluators**

```python
from langchain.evaluation import load_evaluator

# Similarity evaluator
evaluator = load_evaluator("embedding_distance")
score = evaluator.evaluate_strings(
    prediction=response1,
    reference=response2
)
```

**Ventaja**: Framework completo de evaluación con múltiples métricas.

---

#### **3. RAGAS (RAG Assessment)**

Métricas especializadas para evaluar respuestas LLM:
- **Faithfulness**: ¿La respuesta es fiel al contexto?
- **Answer Relevancy**: ¿Responde la pregunta?
- **Context Precision**: ¿Usa el contexto relevante?

**Ventaja**: Diseñado específicamente para LLMs, no genérico.

---

#### **4. DeepEval**

Framework completo de testing para LLMs con métricas:
- **Hallucination**: Detección de información inventada
- **Toxicity**: Detección de contenido tóxico
- **Bias**: Detección de sesgos
- **Relevancy**: Relevancia de la respuesta

**Ventaja**: Suite completa de métricas out-of-the-box.

---

#### **5. LLM-as-a-Judge**

Usar GPT-4 para evaluar calidad de respuestas de GPT-3.5:

```python
judge_prompt = """
Evalúa la siguiente respuesta del LLM según estos criterios:
1. Corrección técnica (0-10)
2. Completitud (0-10)
3. Claridad (0-10)
4. Precisión factual (0-10)

Pregunta: {question}
Respuesta: {answer}

Evaluación:
"""

evaluation = gpt4.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": judge_prompt}]
)
```

**Ventaja**: Evaluación cualitativa que captura matices que métricas numéricas no pueden.

---

### **Plan de Implementación Sprint 2**

**Semana 1-2: Investigación y Prototipo**
- [ ] Evaluar OpenAI Embeddings vs Sentence Transformers (costo/latencia)
- [ ] Prototipar LangChain evaluators con subset de respuestas
- [ ] Validar RAGAS con casos de uso específicos
- [ ] Benchmarking: Sprint 1 (Jaccard) vs Sprint 2 (Embeddings)

**Semana 3-4: Implementación**
- [ ] Integrar embeddings en ConsistencyAnalyzer.java (o migrar a Python)
- [ ] Implementar LLM-as-a-judge para 10% de respuestas (muestreo)
- [ ] Crear dashboard HTML interactivo (Plotly/D3.js)
- [ ] Documentar diferencias Sprint 1 vs Sprint 2

**Semana 5: Validación**
- [ ] Re-ejecutar test de 610 requests con análisis Sprint 2
- [ ] Comparar scores: Jaccard vs Embeddings
- [ ] Identificar falsos positivos eliminados
- [ ] Validar detección de alucinaciones

**Entregables Sprint 2:**
1. `ConsistencyAnalyzerV2.py` (con embeddings)
2. `LLMJudge.py` (evaluación cualitativa)
3. `dashboard.html` (visualización interactiva)
4. `SPRINT2_VALIDATION_REPORT.md` (comparación exhaustiva)

---

## 🎯 Conclusiones: Lo Que Realmente Importa

Cuando empecé este proyecto, quería construir "el sistema perfecto de análisis de consistencia para LLMs".

Terminé con algo imperfecto (score 0.505, Jaccard lleno de falsos positivos, análisis estructural sin validar), pero **útil**.

**¿Qué funcionó realmente?**

Las 3 métricas críticas detectaron problemas que **nunca** hubiera visto con Gatling solo:
- 47.5% de respuestas truncadas (todas con HTTP 200 OK)
- Degradación de +775% bajo carga
- Prompts largos fallando 70% del tiempo

Sin estas métricas, habría lanzado esto a producción pensando "99% success rate, todo bien" y mis usuarios habrían recibido respuestas cortadas a la mitad.

**¿Vale la pena lo imperfecto?**

Absolutamente. Podría haber esperado 3 semanas para implementar embeddings y LLM-as-a-judge desde el inicio. Pero entonces **hoy no sabría**:
- Que el truncamiento es el problema #1
- Que Gatling mide solo el 20% de la latencia real (+403% gap)
- Que necesito SLAs diferentes por tipo de prompt

Sprint 1 me dio problemas reales hoy. Sprint 2 me dará mejores herramientas mañana.

**La lección más importante:**

Gatling, JMeter, k6 - todas las herramientas tradicionales de load testing miden **disponibilidad** (¿respondió el server?). Ninguna mide **calidad** (¿la respuesta es útil?).

Para LLMs con streaming, HTTP 200 OK significa "empezó a responder", no "terminó correctamente". El gap de 403% lo prueba.

Necesitamos herramientas nuevas que entiendan que para LLMs:
- Latencia real = hasta recibir `[DONE]`, no HTTP 200
- Éxito = respuesta completa y coherente, no solo status code 200
- Calidad del contenido importa tanto como la performance

**Este proyecto es mi primer paso en esa dirección.** Es imperfecto, pero funciona. Y eso es suficiente para empezar.

---

## 📊 Stack Técnico Completo

| Componente | Tecnología | Propósito |
|------------|-----------|-----------|
| **Load Testing** | Gatling 3.11.3 | Generación de carga concurrente |
| **Language** | Java 11 | Backend del sistema de análisis |
| **API Target** | OpenAI GPT-3.5-turbo | LLM bajo test |
| **Protocol** | Server-Sent Events (SSE) | Streaming de respuestas |
| **Serialization** | Jackson 2.18 | Manejo de JSON/JSONL |
| **Analysis (Sprint 1)** | Algoritmos custom | Jaccard, regex, estadísticas |
| **Analysis (Sprint 2)** | Embeddings, LLM-as-judge | Similitud semántica, evaluación cualitativa |
| **Reports** | JSON + TXT + HTML | Formato estructurado para CI/CD |

---

## 📁 Archivos Generados Por El Sistema

| Archivo | Formato | Tamaño | Contenido | Uso |
|---------|---------|--------|-----------|-----|
| `responses_metadata.jsonl` | JSONL | 664KB | 610 líneas, metadatos completos | Entrada para análisis |
| `responses_by_prompt.json` | JSON | ~50KB | Agrupación por prompt/categoría/fase | Análisis comparativo |
| `consistency_analysis.json` | JSON | ~20KB | Reporte completo + score global | Dashboard/alertas |
| `llm_response.txt` | TXT | ~500KB | Respuestas legibles con headers | Debugging/auditoría |
| `target/gatling/*/index.html` | HTML | ~2MB | Reporte visual de Gatling | Performance metrics |

---

## 🤝 Agradecimientos y Contexto

Este proyecto fue desarrollado en colaboración con **Claude Code (Anthropic)** durante una sesión intensiva de pair programming distribuida en múltiples sesiones.

**Metodología**:
- Desarrollo iterativo con feedback constante
- Documentación en tiempo real
- Análisis de datos reales (no mocks)
- Transparencia sobre limitaciones

**Aprendizaje clave de la colaboración humano-IA**: Claude me ayudó a mantener la objetividad técnica y evitar confirmation bias. Cuando encontramos el gap de +403% en Gatling, Claude insistió en documentarlo honestamente en vez de "barrerlo bajo la alfombra".

---

## 📚 Recursos y Referencias

### **Código Principal**

- `src/test/java/ssellm/SSELLM.java` - Load test con captura de metadatos (16 campos)
- `src/test/java/ssellm/models/ResponseMetadata.java` - Modelo de datos
- `src/test/java/ssellm/ResponseAggregator.java` - Agrupación de respuestas
- `src/test/java/ssellm/ConsistencyAnalyzer.java` - Análisis multidimensional

### **Documentación Relacionada**

- `GATLING_FEATURE_REQUEST_ANALYSIS.md` - Análisis del gap SSE + propuesta para Gatling
- `GATLING_SSE_DOCUMENTATION_ANALYSIS.md` - Validación contra docs oficiales
- `SPRINT1_VALIDATION_REPORT.md` - Reporte detallado de validación

### **Referencias Externas**

- [Gatling SSE Documentation](https://docs.gatling.io/reference/script/sse/)
- [Gatling LLM API Guide](https://docs.gatling.io/guides/use-cases/llm-api/)
- [OpenAI Streaming API](https://platform.openai.com/docs/api-reference/streaming)
- [RFC 6202: Server-Sent Events](https://datatracker.ietf.org/doc/html/rfc6202)

---

## 💬 Reflexión Final

Empecé queriendo responder: **"¿Cómo mido la calidad de respuestas LLM bajo carga?"**

Terminé aprendiendo que:
- Las herramientas tradicionales no sirven para esto (Gatling mide 20% de la latencia real)
- HTTP 200 OK no significa nada cuando la mitad está truncada
- Los sistemas no fallan, se degradan (y necesitas medirlo)

Lo más valioso: construí esto por **$0.30** y detecté problemas que habrían arruinado la experiencia de usuario en producción.

No es perfecto. Jaccard está lleno de falsos positivos. El análisis estructural no lo validé. El score global (0.505) suena terrible.

Pero **funciona** para lo que importa: evitó que lanzara un sistema que entrega respuestas incompletas a la mitad de los usuarios.

Eso es suficiente para Sprint 1.

**Si estás construyendo algo con LLMs bajo carga, no confíes en las métricas tradicionales. La disponibilidad miente. Mide la calidad del contenido o volarás a ciegas.**

---

**Última actualización**: Octubre 24, 2025
**Licencia**: MIT
**Autor**: Ricardo Campos

---

*"Lo imperfecto que funciona hoy vale más que lo perfecto que nunca terminas."*
