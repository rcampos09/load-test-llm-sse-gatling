# 🚀 Análisis: ¿Feature Request para Gatling?

**Fecha**: 22 de Octubre, 2025
**Contexto**: Análisis de inconsistencia TTFT vs P99 (Sprint 1)
**Pregunta**: ¿Debería Gatling soportar medición nativa de latencia end-to-end para SSE?

---

## 📋 Resumen Ejecutivo

**Pregunta del Usuario:**
> "Esta observación podría ser un nuevo feature para el equipo de Gatling para mejorar el código de la herramienta? Entiendo que la documentación oficial puede ser confusa, pero en realidad confunde el tiempo de respuesta vs lo que el protocolo SSE entrega?"

**Respuesta Corta:** ✅ **SÍ**, esto es un feature request válido y útil para la comunidad.

**Matiz Importante:** Gatling NO confunde el protocolo SSE - su comportamiento es correcto según el estándar HTTP. Sin embargo, la **expectativa del usuario** (medir latencia percibida) es legítima y actualmente no está cubierta.

---

## 🔍 Análisis en Dos Perspectivas

### Perspectiva 1: Gatling tiene razón ✅

**Argumento:**
- El protocolo HTTP define que un request "completa" cuando se establece la conexión
- SSE es simplemente un `Content-Type: text/event-stream` sobre HTTP
- RFC 6202 (SSE) NO define un "completion marker" estándar
- Gatling cumple perfectamente con el estándar HTTP

**Evidencia:**
```
POST /v1/chat/completions HTTP/1.1
Host: api.openai.com
Content-Type: application/json
...

HTTP/1.1 200 OK  ← Aquí Gatling marca "request completo"
Content-Type: text/event-stream
Transfer-Encoding: chunked

data: {"choices":[...]}  ← Esto ya no es parte del "request"
...
data: [DONE]
```

**Desde esta perspectiva:**
- ✅ Gatling mide correctamente el HTTP request/response
- ✅ El streaming es un "evento post-request"
- ✅ Comportamiento correcto según RFC

---

### Perspectiva 2: El usuario tiene razón ✅

**Argumento:**
- En aplicaciones LLM, la **latencia percibida** es lo que importa
- Un request no es "útil" hasta que el stream completa
- La experiencia del usuario incluye TODO el streaming
- Gatling debería ofrecer esta opción

**Evidencia:**
```
Usuario: "¿Cuál es la capital de Francia?"
Sistema: HTTP 200 OK en 558ms  ← Gatling dice "done"
Usuario: [Esperando...]
Sistema: "París" aparece en pantalla después de 2,018ms  ← Usuario dice "done"
```

**Desde esta perspectiva:**
- ⚠️ Gatling P99 = 558ms NO representa la realidad del usuario
- ⚠️ Para SLAs de UX, necesitamos medir hasta `[DONE]`
- ⚠️ La métrica de Gatling puede llevar a optimizaciones incorrectas

---

## 🎯 Conclusión: Ambos tienen razón

### Gatling no está "mal" - está diseñado para HTTP clásico

**HTTP Request/Response tradicional:**
```
Cliente: GET /api/data
Servidor: 200 OK + JSON body
         ↑
    Aquí medir latencia tiene sentido perfecto
```

**SSE/Streaming (caso LLM):**
```
Cliente: POST /completions
Servidor: 200 OK
         ↑
    Gatling mide aquí... pero el valor real viene después
Servidor: [streaming por 2+ segundos]
Servidor: [DONE]
         ↑
    Aquí es donde el usuario percibe "completado"
```

---

## 💡 Propuesta de Feature Request

### API Propuesta

```java
// Comportamiento actual (default)
sse("Connect to LLM")
  .post("/v1/chat/completions")
  .body(StringBody(requestBody))
  // Mide solo hasta HTTP 200 OK

// Nuevo comportamiento (opt-in)
sse("Connect to LLM")
  .post("/v1/chat/completions")
  .body(StringBody(requestBody))
  .measureUntilStreamCompletion()  // ← NUEVO
  .completionMarker("[DONE]")      // ← NUEVO (opcional)
  // Ahora mide hasta recibir [DONE]
```

### Comportamiento Esperado

**Con `.measureUntilStreamCompletion()`:**
- El timer de Gatling NO se detiene en HTTP 200
- Continúa midiendo durante el `.asLongAs()` loop
- Se detiene cuando se detecta el completion marker
- Las métricas P99/P95 reflejan latencia end-to-end real

**Ventajas:**
1. ✅ **Backward compatible** - Requiere opt-in explícito
2. ✅ **Flexible** - Soporta diferentes completion markers (`[DONE]`, EOF, timeout)
3. ✅ **Útil** - Cubre un caso de uso real (LLM streaming)
4. ✅ **Preciso** - Mide lo que el usuario realmente experimenta

---

## 📊 Comparación: Antes vs Después del Feature

### Escenario Actual (Sin Feature)

```
Test con 100 usuarios concurrentes:
┌─────────────────────────────────────┐
│ Gatling Report                      │
├─────────────────────────────────────┤
│ P99 Response Time: 558ms           │ ← NO representa UX real
│ Mean: 280ms                        │
│ Errors: 0%                         │
└─────────────────────────────────────┘

Realidad:
• Los usuarios esperan 2-5 segundos para respuestas completas
• El equipo optimiza basándose en métricas incorrectas
• Los SLAs están desalineados con la experiencia real
```

### Escenario Propuesto (Con Feature)

```
Test con 100 usuarios concurrentes:
┌─────────────────────────────────────┐
│ Gatling Report                      │
├─────────────────────────────────────┤
│ Connection P99: 558ms              │ ← Útil para capacidad
│ End-to-End P99: 2,018ms           │ ← NUEVO - UX real
│ TTFT P99: 6ms                     │ ← NUEVO - Responsiveness
│ Streaming P99: 2,012ms            │ ← NUEVO - Processing
│ Errors: 0%                        │
└─────────────────────────────────────┘

Ventajas:
• Métricas alineadas con experiencia del usuario
• Optimización basada en datos reales
• SLAs precisos
```

---

## 🤔 ¿La Documentación Oficial es Confusa?

### Cita de la Documentación

https://docs.gatling.io/guides/use-cases/llm-api/

> "Gatling waits for complete stream completion before considering the request finished, not just connection establishment."

### Análisis de la Confusión

**✅ TÉCNICAMENTE CORRECTO:**
- Gatling SÍ espera a que el stream complete
- El scenario NO continúa hasta que `.asLongAs()` termina
- Gatling NO procede al siguiente step prematuramente

**❌ PRÁCTICAMENTE ENGAÑOSO:**
- "Waits for" ≠ "Measures"
- El `.asLongAs()` es un **loop**, no un "request"
- El tiempo del stream **NO aparece** en las métricas de Gatling
- Los usuarios asumen que "wait" implica "measure"

### Analogía Perfecta

```
Gatling dice: "Yo espero a que termines de comer"
Usuario asume: "Entonces mides cuánto tardo en comer"
Realidad: Gatling solo mide "cuánto tardas en sentarte a la mesa"
```

### Evidencia en Nuestros Reportes

```
---- Requests --------------------------------------------------------
> Connect to LLM - short    | P99: 558ms
> close                     | P99: 1ms
```

**Observación crítica:** El `.asLongAs()` **NO aparece** como request en el reporte.

---

## 🔬 Análisis Profundo del Código Oficial de Gatling

### Código de Ejemplo Oficial

https://docs.gatling.io/guides/use-cases/llm-api/

```java
ScenarioBuilder prompt = scenario("Scenario").exec(
  sse("Connect to LLM and get Answer")    // ← STEP 1
    .post("/completions")
    .header("Authorization", "Bearer " + apiKey)
    .body(StringBody("{\"model\": \"gpt-3.5-turbo\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Just say HI\"}]}"))
    .asJson(),
  asLongAs("#{stop.isUndefined()}").on(   // ← STEP 2
    sse.processUnmatchedMessages((messages, session) ->
      messages.stream()
        .anyMatch(message -> message.message().contains("{\"data\":\"[DONE]\"}"))
        ? session.set("stop", true) : session;
    )
  ),
  sse("close").close()                     // ← STEP 3
);
```

### Desglose: ¿Qué Mide Gatling en Cada Step?

#### STEP 1: `sse("Connect to LLM and get Answer").post("/completions")`

```
⏱️  TIMER INICIA
    ↓
📤 Envía POST request a /completions
    ↓
🔄 Espera respuesta del servidor
    ↓
✅ Recibe HTTP/1.1 200 OK
    ↓
⏱️  TIMER DETIENE → Métrica capturada: "Connect to LLM and get Answer" ≈ 558ms
```

**Lo que Gatling mide aquí:**
- ✅ Latencia de red
- ✅ Tiempo de handshake SSL/TLS
- ✅ Tiempo de procesamiento inicial del servidor
- ✅ Establecimiento de conexión SSE

**Lo que Gatling NO mide:**
- ❌ Procesamiento de chunks
- ❌ Tiempo de streaming
- ❌ Latencia percibida por el usuario

---

#### STEP 2: `asLongAs("#{stop.isUndefined()}").on(...)`

```
[NO HAY TIMER ACTIVO]
    ↓
🔁 Loop: Procesa mensajes SSE entrantes
    ↓
📥 Recibe chunk 1, 2, 3... 100
    ↓
🔍 Busca marker [DONE] en cada mensaje
    ↓
✅ Detecta [DONE], establece session("stop", true)
    ↓
🔚 Sale del loop
    ↓
[NINGUNA MÉTRICA CAPTURADA]
```

**⚠️ CRÍTICO: Esto es un LOOP, NO un "request"**

- ❌ Gatling NO tiene un timer activo aquí
- ❌ Esta parte NO aparece en las métricas
- ❌ El tiempo transcurrido (~1,460ms en nuestras pruebas) se PIERDE

**Evidencia en el reporte Gatling:**

```
---- Requests --------------------------------------------------------
> Connect to LLM and get Answer    | P99: 558ms     ← Este aparece
> close                            | P99: 1ms       ← Este aparece
                                                    ← asLongAs() NO aparece
```

---

#### STEP 3: `sse("close").close()`

```
⏱️  NUEVO TIMER INICIA (independiente del STEP 1)
    ↓
🔌 Cierra la conexión SSE
    ↓
⏱️  TIMER DETIENE → Métrica capturada: "close" ≈ 1ms
```

**Lo que Gatling mide aquí:**
- ✅ Tiempo de cierre de conexión (despreciable)

---

### Timeline Completo: Lo que Realmente Sucede

```
Tiempo    │ Evento                              │ Gatling Mide   │ Usuario Experimenta
──────────┼─────────────────────────────────────┼────────────────┼─────────────────────
0ms       │ POST /completions enviado           │ ⏱️  Timer ON    │ [Esperando...]
          │                                     │                │
100ms     │ [Handshake SSL/TLS]                │ ⏱️  Midiendo   │ [Esperando...]
          │                                     │                │
558ms     │ ✅ HTTP 200 OK recibido             │ ⏱️  Timer OFF  │ [Esperando...]
          │ Conexión SSE establecida           │ ✅ 558ms       │
          │ ────────────────────────────────── │ ────────────── │ ──────────────────
          │ [asLongAs loop INICIA]             │ ❌ NO MIDE     │ [Esperando...]
          │                                     │                │
564ms     │ Primer chunk recibido (TTFT)       │ ❌ NO MIDE     │ [Ve primer texto!]
          │                                     │                │
600ms     │ Chunks 2-10 recibidos              │ ❌ NO MIDE     │ [Leyendo...]
          │                                     │                │
1,000ms   │ Chunks 11-50 recibidos             │ ❌ NO MIDE     │ [Leyendo...]
          │                                     │                │
1,500ms   │ Chunks 51-90 recibidos             │ ❌ NO MIDE     │ [Leyendo...]
          │                                     │                │
2,018ms   │ ✅ [DONE] recibido, chunk 100       │ ❌ NO MIDE     │ ✅ [Respuesta completa!]
          │ [asLongAs loop TERMINA]            │                │
          │ ────────────────────────────────── │ ────────────── │ ──────────────────
2,019ms   │ close() invocado                   │ ⏱️  Timer ON    │ [Ya tiene respuesta]
          │                                     │                │
2,020ms   │ Conexión cerrada                   │ ⏱️  Timer OFF  │ [Ya tiene respuesta]
          │                                     │ ✅ 1ms         │
```

**Resumen de métricas:**
- **Gatling reporta**: 558ms (conexión) + 1ms (cierre) = 559ms total
- **Usuario experimenta**: 2,018ms (desde request hasta respuesta completa)
- **Gap**: 1,459ms (261% de diferencia)

---

### 🎯 ¿Por qué el `asLongAs()` NO se mide?

**Razón arquitectural de Gatling:**

En Gatling, un "request" es una unidad atómica que tiene:
1. Un nombre (string identificador)
2. Un método HTTP (POST, GET, etc.)
3. Un timer que se inicia y detiene automáticamente

**El `asLongAs()` NO es un request, es un control de flujo:**

```java
// Esto SÍ es un request (tiene nombre y método HTTP)
sse("Connect to LLM and get Answer").post("/completions")

// Esto NO es un request (es un loop de control)
asLongAs("#{stop.isUndefined()}").on(...)

// Esto SÍ es un request (tiene nombre y acción SSE)
sse("close").close()
```

**Analogía con código imperativo:**

```java
long startTime = System.currentTimeMillis();
HttpResponse response = httpClient.post("/completions");  // ← Gatling mide esto
long gatlingMetric = System.currentTimeMillis() - startTime;

// El loop NO se mide
while (!done) {                                           // ← Gatling NO mide esto
    String chunk = readNextChunk();
    if (chunk.contains("[DONE]")) done = true;
}

connection.close();  // ← Gatling mide esto (como request separado)
```

---

### 📊 Comparación: Código Oficial vs Nuestro Código (Sprint 1)

| Aspecto | Código Oficial Gatling | Nuestro Código (Sprint 1) |
|---------|------------------------|---------------------------|
| **Estructura** | 3 steps separados | 3 steps + timing manual |
| **Medición de conexión** | ✅ `sse("Connect...").post()` = 558ms | ✅ Automático por Gatling |
| **Medición de streaming** | ❌ NO - solo loop sin timer | ✅ `requestStartTime` → `currentTime` |
| **TTFT** | ❌ NO capturado | ✅ Primer `delta.content` timestamp |
| **Response Time Total** | ❌ Solo 558ms (conexión) | ✅ 2,018ms (end-to-end real) |
| **Métricas en reporte** | ✅ P99 de conexión (incompleto) | ✅ P99 de experiencia completa |
| **Truncation Detection** | ❌ Solo timeout del loop | ✅ Timeout + buffer overflow |
| **Test Phase Tracking** | ❌ NO | ✅ RAMP vs STEADY |
| **Export Format** | ✅ HTML report de Gatling | ✅ JSONL + Gatling report |
| **Análisis posterior** | ❌ Limitado a métricas Gatling | ✅ 5 dimensiones de calidad |

**Código Sprint 1 (timing manual):**

```java
// Inicialización del timer (DENTRO del asLongAs)
long requestStartTime = session.contains("requestStartTime")
    ? session.getLong("requestStartTime")
    : System.currentTimeMillis();

if (!session.contains("requestStartTime")) {
    session = session.set("requestStartTime", requestStartTime);
}

// ... procesamiento de chunks ...

// Al detectar [DONE] o timeout
if (done || timedOut) {
    long currentTime = System.currentTimeMillis();
    long responseTimeMs = currentTime - requestStartTime;  // ← ESTA es la métrica real

    // responseTimeMs = 2,018ms (vs Gatling = 558ms)
}
```

---

### 💡 Insights Clave del Análisis del Código

#### 1. **El nombre del request es engañoso**

```java
sse("Connect to LLM and get Answer")  // ← Dice "get Answer"
  .post("/completions")               // ← Solo mide "Connect", NO "get Answer"
```

**El nombre sugiere:** Medir hasta obtener la respuesta
**La realidad:** Solo mide hasta establecer la conexión

**Esto refuerza el argumento del feature request:**
- Los usuarios esperan que un request llamado "Connect **and get Answer**" mida ambas cosas
- La expectativa natural es que incluya la respuesta completa
- El comportamiento actual es contra-intuitivo

---

#### 2. **La separación artificial entre conexión y streaming**

Desde la perspectiva del usuario, esto es **UNA sola operación**:

```
Usuario hace pregunta → Espera respuesta completa
```

Pero Gatling lo divide en:

```
1. Conexión (medido)
2. Streaming (NO medido)  ← Artificial desde perspectiva de UX
3. Cierre (medido)
```

---

#### 3. **El código oficial DEMUESTRA la necesidad del feature**

El hecho de que Gatling proporcione este código como ejemplo oficial pero:
- ❌ NO capture TTFT
- ❌ NO capture response time completo
- ❌ NO capture métricas de streaming

...demuestra que el framework **necesita evolucionar** para este caso de uso.

---

### 🎯 Propuesta Mejorada de API

Basándonos en el código oficial, proponemos:

#### Opción 1: Unificar en un solo request (más simple)

```java
sse("Connect to LLM and get Answer")
  .post("/completions")
  .body(StringBody("{...}"))
  .measureUntilStreamCompletion()        // ← NUEVO: Extiende timer
  .completionMarker("[DONE]")            // ← NUEVO: Define marcador
  .timeout(10, TimeUnit.SECONDS)         // ← NUEVO: Timeout explícito
  .asJson()
// El asLongAs() ya no es necesario - Gatling lo maneja internamente
```

**Comportamiento:**
- Timer NO se detiene en HTTP 200 OK
- Gatling procesa chunks internamente
- Timer se detiene al detectar `[DONE]` o timeout
- Métricas P99 reflejan experiencia completa

---

#### Opción 2: Request separado para streaming (más flexible)

```java
scenario("Scenario").exec(
  sse("Connect to LLM")
    .post("/completions")
    .asJson(),

  sse("Process Stream")                  // ← NUEVO: Request type para streaming
    .measureStreamDuration()
    .asLongAs("#{stop.isUndefined()}").on(
      sse.processUnmatchedMessages(...)
    ),

  sse("close").close()
)
```

**Ventaja:** Reportes separados para conexión vs streaming

```
---- Requests --------------------------------------------------------
> Connect to LLM        | P99: 558ms    ← Capacidad de conexiones
> Process Stream        | P99: 1,460ms  ← Latencia de streaming  ← NUEVO
> close                 | P99: 1ms      ← Cierre
```

---

### 🔍 Preguntas de Diseño para el Feature Request

#### 1. **¿Qué pasa si nunca llega `[DONE]`?**

```java
.measureUntilStreamCompletion()
.completionMarker("[DONE]")
.timeout(10, TimeUnit.SECONDS)         // ← Necesario
.onTimeout(MarkAs.ERROR)               // ← O MarkAs.SUCCESS con flag
```

**Opciones:**
- `MarkAs.ERROR` → Request falla, aparece en "Errors" del reporte
- `MarkAs.SUCCESS` → Request completa exitosamente, pero flag indica timeout

---

#### 2. **¿Soportar múltiples completion markers?**

Diferentes APIs LLM usan diferentes markers:

```java
.completionMarkers(Arrays.asList(
    "[DONE]",                    // OpenAI
    "data: [DONE]",              // Variante OpenAI
    "{\"finish_reason\":\"stop\"}"  // Anthropic/otros
))
.orStreamEnd()                   // O detectar EOF del stream
```

---

#### 3. **¿Cómo capturar TTFT además de response time?**

```java
sse("Connect to LLM")
  .post("/completions")
  .measureUntilStreamCompletion()
  .captureTimeToFirstData()      // ← NUEVO: Captura TTFT
  .completionMarker("[DONE]")
```

**Reporte resultante:**

```
---- Requests --------------------------------------------------------
> Connect to LLM        | TTFT P99: 6ms | Total P99: 2,018ms
```

---

## 🎯 Recomendación

### 1. Sí, es un Feature Request Válido

**Razones:**
- ✅ Cubre un caso de uso real y creciente (LLM streaming)
- ✅ La implementación actual lleva a malinterpretación
- ✅ Otros usuarios probablemente tienen el mismo problema
- ✅ Gatling se posiciona como herramienta para LLM testing

### 2. No, Gatling NO "confunde" el protocolo

**Clarificación:**
- ❌ Gatling NO malinterpreta SSE
- ❌ Gatling NO viola estándares HTTP
- ✅ Gatling simplemente no fue diseñado para este caso de uso
- ✅ El comportamiento actual es correcto desde perspectiva HTTP

### 3. El Gap está en la Expectativa vs Realidad

**El problema real:**
```
Expectativa: "Medir latencia percibida por el usuario"
Realidad: "Medir latencia de establecimiento de conexión HTTP"
Gap: Estos son diferentes en streaming, iguales en HTTP tradicional
```

---

## 📝 Propuesta para GitHub Issue

### Título Sugerido
```
Feature Request: Add optional end-to-end latency measurement for SSE streaming
```

### Contenido Sugerido

```markdown
## Context

When load testing LLM APIs with Server-Sent Events (SSE), Gatling correctly
measures HTTP connection establishment (~500ms) but not the full streaming
duration (~2000ms). For user experience metrics, we need end-to-end latency.

## Current Behavior

Using the official example from https://docs.gatling.io/guides/use-cases/llm-api/:

```java
ScenarioBuilder prompt = scenario("Scenario").exec(
  sse("Connect to LLM and get Answer")    // ← Timer starts/stops here
    .post("/completions")
    .body(StringBody("{...}"))
    .asJson(),
  asLongAs("#{stop.isUndefined()}").on(   // ← NO timer here
    sse.processUnmatchedMessages((messages, session) ->
      messages.stream()
        .anyMatch(message -> message.message().contains("[DONE]"))
        ? session.set("stop", true) : session
    )
  ),
  sse("close").close()
);
```

**Problem:** The `asLongAs()` loop (where streaming happens) is NOT measured.

**Gatling report shows:**
```
---- Requests --------------------------------------------------------
> Connect to LLM and get Answer    | P99: 558ms
> close                            | P99: 1ms
                                    ← asLongAs loop missing
```

**What Gatling measures:** Connection setup (558ms in our tests)
**What user experiences:** Full response time (2,018ms in our tests)
**Gap:** 261% difference (1,460ms of streaming NOT captured)

## Proposed Feature

### Option 1: Extend timer until stream completion (simpler)

```java
sse("Connect to LLM and get Answer")
  .post("/completions")
  .body(StringBody("{...}"))
  .measureUntilStreamCompletion()        // ← NEW: Timer doesn't stop at HTTP 200
  .completionMarker("[DONE]")            // ← NEW: Define completion condition
  .timeout(10, TimeUnit.SECONDS)         // ← NEW: Explicit timeout
  .asJson()
// The asLongAs() could be handled internally by Gatling
```

**Result:** Single metric that represents full user experience (connection + streaming).

---

### Option 2: Separate measurable request for streaming (more flexible)

```java
scenario("Scenario").exec(
  sse("Connect to LLM")
    .post("/completions")
    .asJson(),

  sse("Process Stream")                  // ← NEW: Measurable streaming request
    .measureStreamDuration()
    .completionMarker("[DONE]")
    .asLongAs("#{stop.isUndefined()}").on(
      sse.processUnmatchedMessages(...)
    ),

  sse("close").close()
)
```

**Result:** Separate metrics for connection vs streaming (better for analysis).

```
---- Requests --------------------------------------------------------
> Connect to LLM        | P99: 558ms    ← Connection capacity
> Process Stream        | P99: 1,460ms  ← Streaming latency (NEW)
> close                 | P99: 1ms      ← Close
```

Both options would include streaming time in Gatling's P99/P95 metrics.

## Benefits

1. Accurate UX metrics for streaming APIs
2. Proper SLA definition for LLM services
3. Aligned with growing LLM testing use case
4. Backward compatible (opt-in)

## Workaround (Current)

We implemented manual timing in session:
- Capture `requestStartTime` before request
- Calculate `responseTimeMs` after `[DONE]`
- Export to custom JSONL for analysis

This works but loses Gatling's built-in percentile calculations.

## Evidence

- Official guide: https://docs.gatling.io/guides/use-cases/llm-api/
- Our analysis: [link to TTFT_PERCENTIL99_ANALYSIS.md]

## Why This Matters

The official example names the request **"Connect to LLM and get Answer"** but only measures the "Connect" part, not the "get Answer" part. This creates a gap between:

- **User expectation:** "I want to measure how long it takes to get an answer"
- **Gatling behavior:** "I measure how long it takes to establish the connection"

For LLM applications, the answer **IS** the streaming phase, not just the connection.

## Additional Considerations

### 1. TTFT (Time To First Token)
Consider also capturing time to first data chunk:

```java
.captureTimeToFirstData()  // Captures TTFT separately
```

**Report output:**
```
> Connect to LLM    | TTFT P99: 6ms | Total P99: 2,018ms
```

### 2. Multiple completion markers
Different LLM APIs use different markers:

```java
.completionMarkers(Arrays.asList("[DONE]", "data: [DONE]"))
.orStreamEnd()  // Or detect natural EOF
```

### 3. Timeout handling
Clear semantics for when completion marker never arrives:

```java
.onTimeout(MarkAs.ERROR)     // Fail the request
// OR
.onTimeout(MarkAs.SUCCESS)   // Complete with flag
```
```

---

## ✅ Conclusiones Finales

### 1. Feature Request: Sí, es válido

- ✅ Beneficiaría a la comunidad de LLM testing
- ✅ Alineado con la dirección de Gatling (LLM use cases)
- ✅ Solución técnicamente factible
- ✅ Backward compatible

### 2. Gatling no está "mal"

- ✅ Comportamiento correcto según HTTP/SSE estándares
- ✅ Diseñado para request/response tradicional
- ✅ No es un bug, es un gap de feature

### 3. La documentación podría mejorar

**Sugerencia de mejora:**

```markdown
## ⚠️ Important Note on SSE Metrics

Gatling waits for stream completion before proceeding to the next step,
but **does not include streaming time in request metrics**.

The `.asLongAs()` loop processes events but is not measured as part of
the request's response time. Only the initial HTTP connection setup is
included in P99/P95 statistics.

For end-to-end latency measurement (including full stream processing),
use custom session timing or see [Feature Request #XXXX].
```

### 4. Sprint 1 sigue siendo la mejor solución actual

- ✅ Medición manual es NECESARIA hoy
- ✅ Nuestro approach es correcto y completo
- ✅ Captura métricas que Gatling no puede capturar nativamente

---

## 🎯 Próximos Pasos Recomendados

1. **Continuar usando Sprint 1** - Es la única forma de obtener métricas precisas hoy
2. **Opcional:** Abrir GitHub Issue en Gatling para future feature
3. **Documentar internamente** - Explicar por qué usamos medición manual
4. **Evangelizar** - Compartir findings con la comunidad

---

## 📋 Resumen Ejecutivo para Gatling Team

### TL;DR

El código oficial de ejemplo para LLM testing (https://docs.gatling.io/guides/use-cases/llm-api/) tiene un gap crítico:

**El request se llama** "Connect to LLM **and get Answer**"
**Pero solo mide** "Connect to LLM" (no "get Answer")

El 72% del tiempo de respuesta (la parte del streaming) no se captura en métricas.

---

### Impact

- **261% de subestimación** en latencia reportada (558ms vs 2,018ms real)
- **SLAs incorrectos** basados en métricas incompletas
- **Optimizaciones mal dirigidas** hacia componentes que no son el cuello de botella
- **Gap entre métricas y experiencia de usuario** en aplicaciones LLM

---

### Root Cause

El `asLongAs()` loop (donde ocurre el streaming) es un **control de flujo**, no un **request medible**:

```java
sse("Connect to LLM and get Answer").post("/completions")  // ← Medido: 558ms
asLongAs("#{stop.isUndefined()}").on(...)                  // ← NO medido: 1,460ms
sse("close").close()                                        // ← Medido: 1ms
```

**Total medido:** 559ms | **Total real:** 2,018ms | **Gap:** 1,459ms (72%)

---

### Proposed Solution

Opción 1 (simple):
```java
.measureUntilStreamCompletion().completionMarker("[DONE]")
```

Opción 2 (flexible):
```java
sse("Process Stream").measureStreamDuration().asLongAs(...)
```

**Benefits:** Backward compatible, opt-in, soluciona caso de uso LLM creciente

---

### Validation

- ✅ Implementamos medición manual (Sprint 1) - funciona perfectamente
- ✅ Confirmado contra RFC 6202 (SSE) y documentación oficial
- ✅ Comportamiento actual de Gatling es correcto según HTTP estándar
- ✅ Feature request alineado con dirección de Gatling hacia LLM testing

---

### Community Impact

Con la explosión de aplicaciones LLM, muchos equipos probablemente enfrentan este mismo problema. Este feature beneficiaría a:

- ✅ Equipos de QA testeando APIs LLM
- ✅ Engineers definiendo SLAs para servicios streaming
- ✅ Product teams optimizando UX de aplicaciones conversacionales
- ✅ Performance engineers midiendo latencia percibida

---

**Última actualización**: 22 de Octubre, 2025
**Autor**: Análisis post-Sprint (Load Testing LLM SSE)
**Contacto**: [Tu información de contacto para GitHub]
**Referencias**:
- TTFT_PERCENTIL99_ANALYSIS.md (análisis detallado de nuestras pruebas)
- https://docs.gatling.io/guides/use-cases/llm-api/ (documentación oficial)
- RFC 6202 (Server-Sent Events)
- Este documento: GATLING_FEATURE_REQUEST_ANALYSIS.md
