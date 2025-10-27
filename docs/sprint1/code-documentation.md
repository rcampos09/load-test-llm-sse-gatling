# 📚 Documentación Técnica del Código - Sprint 1

**Última actualización**: Octubre 2025
**Autor**: Ricardo Campos
**Versión**: 1.0 (Sprint 1)

---

## 🎯 Visión General del Sistema

Este proyecto implementa un sistema de **load testing para APIs LLM con Server-Sent Events (SSE)** que incluye:

1. **Generación de carga** usando Gatling (SSELLM.java)
2. **Captura de metadata completa** en tiempo real (JSONL format)
3. **Agregación de respuestas** por múltiples dimensiones (ResponseAggregator.java)
4. **Análisis de consistencia** sin LLM externo (ConsistencyAnalyzer.java)
5. **Modelo de datos rico** con 16 campos (ResponseMetadata.java)

---

## 📐 Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                      SSELLM.java (Gatling)                      │
│                  Load Test + SSE Processing                     │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ├─> Genera requests al LLM (SSE)
                        ├─> Captura chunks en tiempo real
                        ├─> Calcula métricas (TTFT, response time, truncation)
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              target/responses_metadata.jsonl                    │
│         16 campos × 610 responses = Metadata completa           │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              ResponseAggregator.java                            │
│     Agrupa respuestas por: prompt, category, phase             │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              ConsistencyAnalyzer.java                           │
│   Analiza 5 dimensiones: completeness, structural, semantic,   │
│            temporal, category → Score global 0-1                │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
        target/consistency_analysis.json (Report final)
```

---

## 📁 Estructura de Archivos del Proyecto

```
src/test/java/ssellm/
├── SSELLM.java                     # Simulación Gatling + captura SSE
├── ResponseAggregator.java         # Agregación de respuestas
├── ConsistencyAnalyzer.java        # Análisis de 5 dimensiones
└── models/
    └── ResponseMetadata.java       # Modelo de datos (16 campos)

target/
├── responses_metadata.jsonl        # 610 líneas JSONL (1 por response)
├── responses_by_prompt.json        # Agrupación por prompt
├── consistency_analysis.json       # Reporte de análisis completo
├── llm_response.txt               # Formato legible para debugging
└── sse_chunks.txt                 # Chunks SSE raw (debugging)
```

---

## 📦 Clase: `ResponseMetadata.java`

**Ubicación**: `src/test/java/ssellm/models/ResponseMetadata.java`

**Propósito**: Modelo de datos que representa **una respuesta LLM completa** con toda su metadata.

### 🔍 Campos (16 en total)

#### **Identificadores**
```java
@JsonProperty("session_id")
private String sessionId;           // userId-scenario (ej: "1-Scenario")

@JsonProperty("chunk_id")
private String chunkId;              // ID del SSE chunk (ej: "chatcmpl-xyz")

@JsonProperty("user_id")
private long userId;                 // Gatling user ID (1-10)
```

#### **Contexto del Test**
```java
@JsonProperty("category")
private String category;             // "short", "medium", "long", "creative", "technical"

@JsonProperty("prompt")
private String prompt;               // Texto del prompt enviado

@JsonProperty("max_tokens")
private int maxTokens;               // Límite de tokens configurado

@JsonProperty("temperature")
private double temperature;          // Parámetro de creatividad (0.7)
```

#### **Contenido de la Respuesta**
```java
@JsonProperty("response")
private String response;             // Texto completo de la respuesta

@JsonProperty("response_length")
private int responseLength;          // Longitud en caracteres (auto-calculado)
```

#### **Métricas de Performance**
```java
@JsonProperty("timestamp")
private Instant timestamp;           // Momento de completion (ISO-8601)

@JsonProperty("response_time_ms")
private long responseTimeMs;         // Latencia end-to-end (requestStart → [DONE])

@JsonProperty("ttft_ms")
private long ttftMs;                 // Time To First Token (requestStart → primer chunk con content)

@JsonProperty("total_chunks")
private int totalChunks;             // Cantidad de chunks SSE recibidos
```

#### **Indicadores de Calidad**
```java
@JsonProperty("truncated")
private boolean truncated;           // true si la respuesta está incompleta

@JsonProperty("truncation_reason")
private String truncationReason;     // "NONE", "TIMEOUT", "BUFFER_OVERFLOW"

@JsonProperty("test_phase")
private String testPhase;            // "RAMP" (primeros 10s) o "STEADY" (60s siguientes)
```

### 🏗️ Builder Pattern

La clase implementa el **Builder pattern** para construcción fluida:

```java
ResponseMetadata metadata = ResponseMetadata.builder()
    .sessionId("1-Scenario")
    .chunkId("chatcmpl-xyz")
    .userId(1L)
    .category("short")
    .prompt("Explica qué es Java")
    .maxTokens(150)
    .temperature(0.7)
    .response("Java es un lenguaje de programación...")
    .timestamp(Instant.now())
    .responseTimeMs(1250L)
    .ttftMs(320L)
    .totalChunks(15)
    .truncated(false)
    .truncationReason("NONE")
    .testPhase("STEADY")
    .build();
```

### 📋 Métodos Importantes

- **`setResponse(String response)`**: Actualiza respuesta y calcula `responseLength` automáticamente
- **`setTruncationReason(String reason)`**: Si reason != "NONE", marca `truncated = true` automáticamente
- **`toString()`**: Formato compacto para logging

### 📊 Ejemplo de JSON Serializado

```json
{
  "session_id": "1-Scenario",
  "chunk_id": "chatcmpl-AQWoV6",
  "user_id": 1,
  "category": "short",
  "prompt": "Explica qué es Java en una oración",
  "max_tokens": 150,
  "temperature": 0.7,
  "response": "Java es un lenguaje de programación orientado a objetos...",
  "response_length": 245,
  "timestamp": "2025-10-26T10:15:32.123Z",
  "response_time_ms": 1250,
  "ttft_ms": 320,
  "total_chunks": 15,
  "truncated": false,
  "truncation_reason": "NONE",
  "test_phase": "STEADY"
}
```

---

## 🚀 Clase: `SSELLM.java`

**Ubicación**: `src/test/java/ssellm/SSELLM.java`

**Propósito**: Simulación Gatling que ejecuta el load test y captura **toda la metadata en tiempo real**.

### 🔧 Configuración Principal

```java
String api_key = System.getenv("api_key");  // API key de OpenAI desde variable de entorno

// Archivos de salida
Path ruta = Path.of("target/sse_chunks.txt");              // Raw SSE chunks (debugging)
Path rutaRespuesta = Path.of("target/llm_response.txt");   // Formato legible
Path rutaMetadata = Path.of("target/responses_metadata.jsonl");  // JSONL (principal)

// Feeder CSV para prompts
FeederBuilder<String> promptFeeder = csv("prompts.csv").circular();

// ObjectMapper para serialización JSON
ObjectMapper objectMapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

### 📝 Detección de Fase del Test

```java
long testStartTime = System.currentTimeMillis();
long rampDuration = 10000; // 10 segundos RAMP

// Durante el procesamiento de cada response:
long timeSinceTestStart = currentTime - testStartTime;
String testPhase = timeSinceTestStart < rampDuration ? "RAMP" : "STEADY";
```

**Lógica**:
- Primeros 10 segundos → `RAMP`
- Después de 10 segundos → `STEADY`

### 🌐 Protocolo HTTP SSE

```java
HttpProtocolBuilder httpProtocol = http
    .baseUrl("https://api.openai.com/v1/chat")
    .sseUnmatchedInboundMessageBufferSize(100);  // Buffer para chunks SSE
```

### 🎭 Escenario Gatling

```java
ScenarioBuilder prompt = scenario("Scenario")
    .feed(promptFeeder)  // Inyecta: category, prompt, max_tokens, temperature
    .exec(
        sse("Connect to LLM - #{category}")
            .post("/completions")
            .header("Authorization", "Bearer " + api_key)
            .header("Content-Type", "application/json")
            .body(StringBody("{...}"))  // Payload JSON con prompt
            .asJson()
    )
    .asLongAs("#{stop.isUndefined()}").on(  // Loop hasta recibir [DONE]
        sse.processUnmatchedMessages((messages, session) -> {
            // ⚙️ PROCESAMIENTO DE CHUNKS SSE (explicado abajo)
        })
    )
    .exec(sse("close").close());
```

### 🔄 Procesamiento de Chunks SSE

Este es el **corazón del sistema**. Se ejecuta cada vez que llegan chunks SSE:

#### **1. Inicialización de Variables de Sesión**

```java
StringBuilder responseContent = new StringBuilder();
final String[] chunkIdHolder = new String[1];
final int[] chunkCounter = new int[1];

// Recuperar timestamp del inicio del request
long requestStartTime = session.contains("requestStartTime")
    ? session.getLong("requestStartTime")
    : System.currentTimeMillis();

// Recuperar contenido acumulado previo
if (session.contains("llmResponse")) {
    responseContent.append(session.getString("llmResponse"));
}

// Recuperar chunk ID previo
if (session.contains("chunkId")) {
    chunkIdHolder[0] = session.getString("chunkId");
}

// Recuperar contador de chunks previo
if (session.contains("chunkCount")) {
    chunkCounter[0] = session.getInt("chunkCount");
}
```

**Nota clave**: Gatling invoca este lambda **múltiples veces por request** (una por batch de chunks), por eso recuperamos el estado previo.

#### **2. Procesamiento de Cada Mensaje SSE**

```java
messages.forEach(message -> {
    String data = message.message();
    if (data != null && !data.isEmpty() && !data.contains("[DONE]")) {
        chunkCounter[0]++; // Incrementar contador

        // Calcular TTFT (solo en el primer chunk con content)
        if (ttftHolder[0] == 0) {
            ttftHolder[0] = System.currentTimeMillis() - requestStartTime;
        }

        // Guardar chunk raw en archivo (debugging)
        Files.writeString(ruta, "🔹 SSE chunk: " + data + "\n", APPEND);

        // Parsear JSON del chunk
        JsonObject chunkJson = JsonParser.parseString(data).getAsJsonObject();
        if (chunkJson.has("data")) {
            String innerData = chunkJson.get("data").getAsString();
            JsonObject innerJson = JsonParser.parseString(innerData).getAsJsonObject();

            // Extraer chunk ID (solo la primera vez)
            if (innerJson.has("id") && chunkIdHolder[0] == null) {
                chunkIdHolder[0] = innerJson.get("id").getAsString();
            }

            // Extraer contenido del delta
            if (innerJson.has("choices")) {
                JsonObject choice = innerJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choice.has("delta")) {
                    JsonObject delta = choice.getAsJsonObject("delta");
                    if (delta.has("content")) {
                        String content = delta.get("content").getAsString();
                        responseContent.append(content);
                    }
                }
            }
        }
    }
});
```

#### **3. Detección de Finalización**

```java
boolean done = messages.stream()
    .anyMatch(m -> m.message().contains("[DONE]"));

// Timeout detection (máximo 10 segundos por request)
long currentTime = System.currentTimeMillis();
long elapsed = currentTime - requestStartTime;
boolean timedOut = elapsed > 10000;

// Finalizar si: (done = true) OR (timeout = true)
if (done || timedOut) {
    // Guardar metadata completa...
}
```

#### **4. Construcción de Metadata y Guardado**

```java
String fullResponse = responseContent.toString();
long responseTimeMs = currentTime - requestStartTime;

// Obtener información de la sesión
String sessionId = updatedSession.userId() + "-" + updatedSession.scenario();
String category = updatedSession.getString("category");
String prompt = updatedSession.getString("prompt");
// ... más campos del feeder

// Determinar fase del test
long timeSinceTestStart = currentTime - testStartTime;
String testPhase = timeSinceTestStart < rampDuration ? "RAMP" : "STEADY";

// Detectar truncamiento
boolean truncated = timedOut || !done;
String truncationReason = "NONE";
if (timedOut) {
    truncationReason = "TIMEOUT";
}

// Construir ResponseMetadata
ResponseMetadata metadata = ResponseMetadata.builder()
    .sessionId(sessionId)
    .chunkId(storedChunkId)
    .userId(updatedSession.userId())
    .category(category)
    .prompt(prompt)
    .maxTokens(maxTokens)
    .temperature(temperature)
    .response(fullResponse)
    .timestamp(Instant.now())
    .responseTimeMs(responseTimeMs)
    .ttftMs(ttftHolder[0])
    .totalChunks(chunkCounter[0])
    .truncated(truncated)
    .truncationReason(truncationReason)
    .testPhase(testPhase)
    .build();

// Guardar metadata como JSONL (1 línea por response)
String jsonLine = objectMapper.writeValueAsString(metadata);
Files.writeString(rutaMetadata, jsonLine + "\n", APPEND);

// También guardar formato legible (legacy)
Files.writeString(rutaRespuesta, formattedResponse.toString(), APPEND);

// Señalar a Gatling que terminamos
return updatedSession.set("stop", true);
}
```

### ⚙️ Configuración de Inyección de Carga

```java
setUp(
    prompt.injectOpen(
        rampUsers(10).during(10),         // 0-10s: Subir de 0 a 10 usuarios graduales
        constantUsersPerSec(10).during(60) // 10-70s: Mantener 10 TPS durante 60s
    )
).protocols(httpProtocol);
```

**Resultado**:
- **RAMP phase** (0-10s): ~10 usuarios × ~20 prompts = **~200 requests**
- **STEADY phase** (10-70s): 10 TPS × 60s / ~20 prompts × 20 ejecuciones = **~410 requests**
- **Total**: **~610 requests**

### 🎯 Métricas Capturadas por SSELLM

| Métrica | Cómo se calcula | Ejemplo |
|---------|----------------|---------|
| **Response Time** | `currentTime - requestStartTime` | 8,826 ms |
| **TTFT** | Timestamp del primer chunk con content - requestStartTime | 320 ms |
| **Total Chunks** | Contador incremental en cada chunk | 42 chunks |
| **Truncated** | `timedOut OR !done` | true |
| **Truncation Reason** | "TIMEOUT" si elapsed > 10s, "NONE" si normal | TIMEOUT |
| **Test Phase** | `timeSinceTestStart < 10s ? "RAMP" : "STEADY"` | STEADY |

### 📂 Archivos Generados

1. **`target/responses_metadata.jsonl`** (PRINCIPAL)
   - Formato: JSON Lines (1 objeto JSON por línea)
   - Cantidad: 610 líneas (610 respuestas)
   - Uso: Entrada para ResponseAggregator y ConsistencyAnalyzer

2. **`target/llm_response.txt`** (LEGACY)
   - Formato: Texto plano con separadores
   - Propósito: Debugging manual, fácil de leer

3. **`target/sse_chunks.txt`** (DEBUGGING)
   - Formato: Raw SSE chunks JSON
   - Propósito: Debugging bajo nivel del protocolo SSE

---

## 📊 Clase: `ResponseAggregator.java`

**Ubicación**: `src/test/java/ssellm/ResponseAggregator.java`

**Propósito**: Lee el archivo JSONL y agrupa respuestas por diferentes dimensiones para análisis.

### 🔍 Métodos Principales

#### **1. `readAllResponses() → List<ResponseMetadata>`**

Lee todo el archivo JSONL y deserializa a objetos Java:

```java
public List<ResponseMetadata> readAllResponses() throws IOException {
    if (!Files.exists(metadataFile)) {
        return Collections.emptyList();
    }

    List<ResponseMetadata> responses = new ArrayList<>();
    List<String> lines = Files.readAllLines(metadataFile);

    for (String line : lines) {
        if (line.trim().isEmpty()) continue;

        try {
            ResponseMetadata metadata = objectMapper.readValue(line, ResponseMetadata.class);
            responses.add(metadata);
        } catch (IOException e) {
            System.err.println("⚠️ Error parsing line: " + e.getMessage());
        }
    }

    System.out.println("✅ Loaded " + responses.size() + " responses");
    return responses;
}
```

**Output de ejemplo**:
```
✅ Loaded 610 responses from target/responses_metadata.jsonl
```

#### **2. `groupByPrompt() → Map<String, List<ResponseMetadata>>`**

Agrupa respuestas que pertenecen al mismo prompt:

```java
public Map<String, List<ResponseMetadata>> groupByPrompt() throws IOException {
    List<ResponseMetadata> allResponses = readAllResponses();

    Map<String, List<ResponseMetadata>> grouped = allResponses.stream()
        .collect(Collectors.groupingBy(ResponseMetadata::getPrompt));

    System.out.println("📊 Grouped responses:");
    grouped.forEach((prompt, responses) -> {
        System.out.println("  - \"" + truncate(prompt, 60) + "\" → " + responses.size() + " responses");
    });

    return grouped;
}
```

**Output de ejemplo**:
```
📊 Grouped responses:
  - "Explica qué es Java en una oración" → 20 responses
  - "Implementa búsqueda binaria en Java" → 21 responses
  - "Propón nombres creativos para una startup de IA" → 20 responses
  ...
```

**Uso**: Esencial para análisis de **consistencia semántica** (comparar respuestas del mismo prompt).

#### **3. `groupByCategory() → Map<String, List<ResponseMetadata>>`**

Agrupa por categoría de prompt (short, medium, long, creative, technical):

```java
public Map<String, List<ResponseMetadata>> groupByCategory() throws IOException {
    List<ResponseMetadata> allResponses = readAllResponses();

    Map<String, List<ResponseMetadata>> grouped = allResponses.stream()
        .collect(Collectors.groupingBy(ResponseMetadata::getCategory));

    return grouped;
}
```

**Output de ejemplo**:
```
📊 Grouped by category:
  - short → 84 responses
  - medium → 105 responses
  - long → 81 responses
  - creative → 60 responses
  - technical → 280 responses
```

**Uso**: Análisis de **impacto por categoría** (¿los prompts largos fallan más?).

#### **4. `groupByTestPhase() → Map<String, List<ResponseMetadata>>`**

Agrupa por fase del test (RAMP vs STEADY):

```java
public Map<String, List<ResponseMetadata>> groupByTestPhase() throws IOException {
    List<ResponseMetadata> allResponses = readAllResponses();

    Map<String, List<ResponseMetadata>> grouped = allResponses.stream()
        .collect(Collectors.groupingBy(ResponseMetadata::getTestPhase));

    return grouped;
}
```

**Output de ejemplo**:
```
📊 Grouped by test phase:
  - RAMP → 200 responses
  - STEADY → 410 responses
```

**Uso**: Análisis **temporal** (¿hay degradación bajo carga sostenida?).

#### **5. `getTruncationStats() → Map<String, Object>`**

Calcula estadísticas de truncamiento:

```java
public Map<String, Object> getTruncationStats() throws IOException {
    List<ResponseMetadata> allResponses = readAllResponses();

    long totalResponses = allResponses.size();
    long truncatedCount = allResponses.stream()
        .filter(ResponseMetadata::isTruncated)
        .count();

    Map<String, Long> truncationReasons = allResponses.stream()
        .filter(ResponseMetadata::isTruncated)
        .collect(Collectors.groupingBy(
            ResponseMetadata::getTruncationReason,
            Collectors.counting()
        ));

    double truncationRate = (double) truncatedCount / totalResponses;

    Map<String, Object> stats = new HashMap<>();
    stats.put("total_responses", totalResponses);
    stats.put("truncated_count", truncatedCount);
    stats.put("truncation_rate", truncationRate);
    stats.put("truncation_reasons", truncationReasons);

    return stats;
}
```

**Output de ejemplo**:
```
📊 Truncation Statistics:
  - Total responses: 610
  - Truncated: 290 (47.50%)
  - Reasons: {TIMEOUT=290}
```

#### **6. `saveGroupedResponses(Path outputFile)`**

Guarda agrupación en formato JSON legible:

```java
public void saveGroupedResponses(Path outputFile) throws IOException {
    Map<String, List<ResponseMetadata>> grouped = groupByPrompt();

    Map<String, Object> output = new HashMap<>();

    for (Map.Entry<String, List<ResponseMetadata>> entry : grouped.entrySet()) {
        String prompt = entry.getKey();
        List<ResponseMetadata> responses = entry.getValue();

        Map<String, Object> promptData = new HashMap<>();
        if (!responses.isEmpty()) {
            ResponseMetadata first = responses.get(0);
            promptData.put("category", first.getCategory());
            promptData.put("max_tokens", first.getMaxTokens());
            promptData.put("temperature", first.getTemperature());
        }
        promptData.put("total_responses", responses.size());
        promptData.put("responses", responses);

        output.put(prompt, promptData);
    }

    ObjectMapper prettyMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

    String json = prettyMapper.writeValueAsString(output);
    Files.writeString(outputFile, json);

    System.out.println("💾 Grouped responses saved to: " + outputFile);
}
```

**Output**: `target/responses_by_prompt.json`

```json
{
  "Explica qué es Java en una oración": {
    "category": "short",
    "max_tokens": 150,
    "temperature": 0.7,
    "total_responses": 20,
    "responses": [
      { /* ResponseMetadata completo */ },
      { /* ResponseMetadata completo */ }
    ]
  }
}
```

### 🚀 Ejecución Standalone

```bash
java -cp target/test-classes ssellm.ResponseAggregator
```

Ejecuta todos los métodos de agregación y guarda `responses_by_prompt.json`.

---

## 🔍 Clase: `ConsistencyAnalyzer.java`

**Ubicación**: `src/test/java/ssellm/ConsistencyAnalyzer.java`

**Propósito**: Analiza la consistencia de las respuestas sin usar LLM externo, usando **heurísticas** en 5 dimensiones.

### 📊 5 Dimensiones de Análisis

#### **1. Completeness Analysis** (Peso: 25%)

**Pregunta**: ¿Las respuestas están completas o truncadas?

```java
private Map<String, Object> analyzeCompleteness(List<ResponseMetadata> responses) {
    long truncatedCount = responses.stream()
        .filter(ResponseMetadata::isTruncated)
        .count();

    double completenessScore = 1.0 - ((double) truncatedCount / responses.size());

    Map<String, Long> reasonBreakdown = responses.stream()
        .filter(ResponseMetadata::isTruncated)
        .collect(Collectors.groupingBy(
            ResponseMetadata::getTruncationReason,
            Collectors.counting()
        ));

    // ... construir issues si truncatedCount > 10%
}
```

**Métricas**:
- **Score**: `1.0 - (truncated / total)`
- **Truncation Rate**: `truncated / total`
- **Issues**: Si > 10% truncadas → severity "high"

**Ejemplo de output**:
```json
{
  "score": 0.525,
  "truncated_count": 290,
  "truncation_rate": 0.475,
  "issues": [
    {
      "description": "290 responses truncated",
      "severity": "high",
      "affected_count": 290,
      "reasons": {"TIMEOUT": 290}
    }
  ]
}
```

#### **2. Structural Analysis** (Peso: 25%)

**Pregunta**: ¿Las respuestas tienen formato y estructura consistente?

```java
private Map<String, Object> analyzeStructuralConsistency(
    Map<String, List<ResponseMetadata>> byPrompt
) {
    for (Map.Entry<String, List<ResponseMetadata>> entry : byPrompt.entrySet()) {
        List<ResponseMetadata> responses = entry.getValue();
        double promptScore = 1.0;

        // 1. Variación de longitud
        IntSummaryStatistics lengthStats = responses.stream()
            .mapToInt(ResponseMetadata::getResponseLength)
            .summaryStatistics();

        double lengthVariation = (maxLength - minLength) / avgLength;
        if (lengthVariation > 0.5) {  // Más del 50% de variación
            promptScore -= 0.1;
            // Agregar issue...
        }

        // 2. Formato Markdown inconsistente
        long markdownCount = responses.stream()
            .filter(r -> containsMarkdown(r.getResponse()))
            .count();

        if (markdownCount > 0 && markdownCount < responses.size()) {
            promptScore -= 0.15;
            // Agregar issue...
        }

        // 3. Mezcla de idiomas
        Map<String, Long> languages = responses.stream()
            .collect(Collectors.groupingBy(
                r -> detectLanguage(r.getResponse()),
                Collectors.counting()
            ));

        if (languages.size() > 1) {
            promptScore -= 0.2;
            // Agregar issue...
        }

        totalScore += Math.max(0.0, promptScore);
    }

    double structuralScore = totalScore / promptCount;
}
```

**Heurísticas**:
- **Length Variation**: Si (max - min) / avg > 50% → problema
- **Markdown Detection**: `text.contains("```") || text.contains("**")`
- **Language Detection**: Contar palabras comunes en español vs inglés

**Ejemplo de output**:
```json
{
  "score": 0.85,
  "issues": [
    {
      "prompt": "Implementa búsqueda binaria...",
      "description": "High length variation: 120.5%",
      "severity": "high",
      "min_length": 50,
      "max_length": 1200,
      "avg_length": 580
    }
  ]
}
```

#### **3. Semantic Analysis** (Peso: 40%)

**Pregunta**: ¿Las respuestas son semánticamente similares entre sí?

**Método**: Jaccard Similarity basado en keywords

```java
private Map<String, Object> analyzeSemanticConsistency(
    Map<String, List<ResponseMetadata>> byPrompt
) {
    for (Map.Entry<String, List<ResponseMetadata>> entry : byPrompt.entrySet()) {
        List<ResponseMetadata> responses = entry.getValue();

        // Extraer keywords de todas las respuestas
        List<Set<String>> keywordSets = responses.stream()
            .map(r -> extractKeywords(r.getResponse()))
            .collect(Collectors.toList());

        // Calcular promedio de Jaccard similarity pairwise
        double avgSimilarity = calculateAverageJaccardSimilarity(keywordSets);

        if (avgSimilarity < 0.6) {  // Baja similitud semántica
            // Agregar issue...
        }
    }
}

private Set<String> extractKeywords(String text) {
    Set<String> stopwords = Set.of("the", "is", "are", "and", "el", "la", "de", ...);

    return Pattern.compile("\\w+")
        .matcher(text.toLowerCase())
        .results()
        .map(m -> m.group())
        .filter(word -> word.length() > 3)  // Solo palabras largas
        .filter(word -> !stopwords.contains(word))
        .collect(Collectors.toSet());
}

private double jaccardSimilarity(Set<String> set1, Set<String> set2) {
    Set<String> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);

    Set<String> union = new HashSet<>(set1);
    union.addAll(set2);

    return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
}
```

**Proceso**:
1. Extraer keywords (palabras > 3 chars, sin stopwords)
2. Calcular Jaccard similarity entre todos los pares de respuestas
3. Promediar todas las similitudes

**Limitación conocida** (documentada en Sprint 1):
- ⚠️ **Falsos positivos** en prompts creativos (Jaccard score bajo es esperado)
- ✅ Funciona bien para prompts técnicos (código, conceptos)

**Ejemplo de output**:
```json
{
  "score": 0.306,
  "issues": [
    {
      "prompt": "Propón nombres creativos...",
      "description": "Low semantic similarity between responses",
      "severity": "medium",
      "similarity_score": 0.099,
      "response_count": 20
    }
  ]
}
```

#### **4. Temporal Analysis** (Peso: 5%)

**Pregunta**: ¿Hay degradación de calidad entre RAMP y STEADY?

```java
private Map<String, Object> analyzeTemporalPatterns(
    Map<String, List<ResponseMetadata>> byPhase
) {
    List<ResponseMetadata> rampPhase = byPhase.get("RAMP");
    List<ResponseMetadata> steadyPhase = byPhase.get("STEADY");

    double rampTruncationRate = calculateTruncationRate(rampPhase);
    double steadyTruncationRate = calculateTruncationRate(steadyPhase);

    double rampAvgResponseTime = calculateAvgResponseTime(rampPhase);
    double steadyAvgResponseTime = calculateAvgResponseTime(steadyPhase);

    double degradation = steadyTruncationRate - rampTruncationRate;
    boolean degradationDetected = degradation > 0.1;  // Más de 10% de incremento

    double temporalScore = 1.0 - Math.max(0.0, degradation);

    return Map.of(
        "score", temporalScore,
        "ramp_truncation_rate", rampTruncationRate,
        "steady_truncation_rate", steadyTruncationRate,
        "ramp_avg_response_time_ms", rampAvgResponseTime,
        "steady_avg_response_time_ms", steadyAvgResponseTime,
        "degradation_detected", degradationDetected,
        "degradation_magnitude", degradation
    );
}
```

**Métricas**:
- Truncation rate: RAMP vs STEADY
- Avg response time: RAMP vs STEADY
- Degradation: `steady_truncation - ramp_truncation`

**Ejemplo de output**:
```json
{
  "score": 0.85,
  "ramp_truncation_rate": 0.05,
  "steady_truncation_rate": 0.52,
  "ramp_avg_response_time_ms": 1009,
  "steady_avg_response_time_ms": 8826,
  "degradation_detected": true,
  "degradation_magnitude": 0.47
}
```

#### **5. Category Analysis** (Peso: 5%)

**Pregunta**: ¿Qué categorías de prompts tienen peor calidad?

```java
private Map<String, Object> analyzeCategoryImpact(
    Map<String, List<ResponseMetadata>> byCategory
) {
    Map<String, Map<String, Object>> categoryScores = new HashMap<>();

    for (Map.Entry<String, List<ResponseMetadata>> entry : byCategory.entrySet()) {
        String category = entry.getKey();
        List<ResponseMetadata> responses = entry.getValue();

        double truncationRate = calculateTruncationRate(responses);
        double avgResponseTime = calculateAvgResponseTime(responses);

        Map<String, Object> categoryData = new HashMap<>();
        categoryData.put("response_count", responses.size());
        categoryData.put("truncation_rate", truncationRate);
        categoryData.put("avg_response_time_ms", avgResponseTime);
        categoryData.put("score", 1.0 - truncationRate);

        categoryScores.put(category, categoryData);
    }

    double avgCategoryScore = categoryScores.values().stream()
        .mapToDouble(m -> (double) m.get("score"))
        .average()
        .orElse(1.0);

    return Map.of("categories", categoryScores, "score", avgCategoryScore);
}
```

**Ejemplo de output**:
```json
{
  "score": 0.68,
  "categories": {
    "short": {
      "response_count": 84,
      "truncation_rate": 0.083,
      "avg_response_time_ms": 1250,
      "score": 0.917
    },
    "long": {
      "response_count": 81,
      "truncation_rate": 0.704,
      "avg_response_time_ms": 9200,
      "score": 0.296
    }
  }
}
```

### 🎯 Global Consistency Score

Promedio ponderado de las 5 dimensiones:

```java
private double calculateGlobalScore(Map<String, Object> report) {
    double score =
        completeness.score * 0.25 +  // 25%
        structural.score  * 0.25 +   // 25%
        semantic.score    * 0.40 +   // 40% (más importante)
        temporal.score    * 0.05 +   // 5%
        category.score    * 0.05;    // 5%

    return score;
}
```

**Ejemplo**: Si tenemos:
- Completeness: 0.525
- Structural: 0.85
- Semantic: 0.306
- Temporal: 0.85
- Category: 0.68

```
Global = (0.525 × 0.25) + (0.85 × 0.25) + (0.306 × 0.40) + (0.85 × 0.05) + (0.68 × 0.05)
       = 0.131 + 0.212 + 0.122 + 0.042 + 0.034
       = 0.541 (54.1%)
```

### 📝 Summary Generator

```java
private String generateSummary(Map<String, Object> report) {
    double score = (double) report.get("global_consistency_score");

    StringBuilder summary = new StringBuilder();

    if (score >= 0.90) {
        summary.append("✅ Excelente consistencia - ");
    } else if (score >= 0.75) {
        summary.append("⚠️ Consistencia aceptable - ");
    } else if (score >= 0.60) {
        summary.append("⚠️ Consistencia preocupante - ");
    } else {
        summary.append("❌ Problemas críticos de consistencia - ");
    }

    summary.append(String.format("Score global: %.1f%%. ", score * 100));
    // ... más información contextual

    return summary.toString();
}
```

### 📂 Output Final

```java
analyzer.saveReport(report, Path.of("target/consistency_analysis.json"));
```

**Estructura del JSON**:
```json
{
  "analysis_timestamp": "2025-10-26 10:30:00",
  "total_responses": 610,
  "unique_prompts": 30,
  "completeness_analysis": { ... },
  "structural_analysis": { ... },
  "semantic_analysis": { ... },
  "temporal_analysis": { ... },
  "category_analysis": { ... },
  "global_consistency_score": 0.505,
  "summary": "⚠️ Consistencia preocupante - Score global: 50.5%. ..."
}
```

### 🚀 Ejecución Standalone

```bash
java -cp target/test-classes ssellm.ConsistencyAnalyzer
```

---

## 🔄 Flujo de Ejecución Completo

### 1️⃣ **Ejecutar Load Test**

```bash
export api_key="sk-..."
./mvnw gatling:test
```

**Qué hace**:
- Gatling ejecuta `SSELLM.java`
- Genera 610 requests a OpenAI API
- Captura chunks SSE en tiempo real
- Calcula 16 métricas por response
- Guarda en `target/responses_metadata.jsonl`

**Archivos generados**:
- `target/responses_metadata.jsonl` (610 líneas)
- `target/llm_response.txt` (formato legible)
- `target/sse_chunks.txt` (debugging)
- `target/gatling/...` (reportes HTML de Gatling)

### 2️⃣ **Agregar Respuestas**

```bash
java -cp target/test-classes ssellm.ResponseAggregator
```

**Qué hace**:
- Lee `responses_metadata.jsonl`
- Agrupa por: prompt, category, phase
- Calcula estadísticas de truncamiento
- Guarda en `target/responses_by_prompt.json`

**Output de consola**:
```
✅ Loaded 610 responses from target/responses_metadata.jsonl
📊 Grouped responses:
  - "Explica qué es Java..." → 20 responses
  ...
📊 Truncation Statistics:
  - Total responses: 610
  - Truncated: 290 (47.50%)
💾 Grouped responses saved to: target/responses_by_prompt.json
```

### 3️⃣ **Analizar Consistencia**

```bash
java -cp target/test-classes ssellm.ConsistencyAnalyzer
```

**Qué hace**:
- Lee `responses_metadata.jsonl`
- Analiza 5 dimensiones de consistencia
- Calcula score global (0-1)
- Genera issues por dimensión
- Guarda en `target/consistency_analysis.json`

**Output de consola**:
```
🔍 Starting Consistency Analysis...

📊 Analyzing completeness...
  ✓ Completeness score: 0.525
📊 Analyzing structural consistency...
  ✓ Structural score: 0.850
📊 Analyzing semantic consistency...
  ✓ Semantic score: 0.306
📊 Analyzing temporal patterns...
  ✓ Temporal score: 0.850
📊 Analyzing category impact...
  ✓ Category score: 0.680

🎯 Global Consistency Score: 0.505

✅ Consistency analysis completed successfully!
📊 Report saved to: target/consistency_analysis.json
```

---

## 📈 Ejemplo de Datos Reales (Sprint 1)

### Input: `responses_metadata.jsonl` (610 líneas)

```jsonl
{"session_id":"1-Scenario","chunk_id":"chatcmpl-xyz","user_id":1,"category":"short","prompt":"Explica qué es Java","max_tokens":150,"temperature":0.7,"response":"Java es un lenguaje...","response_length":245,"timestamp":"2025-10-26T10:15:32.123Z","response_time_ms":1250,"ttft_ms":320,"total_chunks":15,"truncated":false,"truncation_reason":"NONE","test_phase":"STEADY"}
{"session_id":"2-Scenario","chunk_id":"chatcmpl-abc","user_id":2,"category":"long","prompt":"Implementa búsqueda binaria completa con casos edge","max_tokens":800,"temperature":0.7,"response":"```java\npublic class BinarySearch { ... [TRUNCATED]","response_length":3500,"timestamp":"2025-10-26T10:15:42.890Z","response_time_ms":9800,"ttft_ms":450,"total_chunks":42,"truncated":true,"truncation_reason":"TIMEOUT","test_phase":"STEADY"}
...
```

### Output: `consistency_analysis.json`

```json
{
  "analysis_timestamp": "Sat Oct 26 10:30:00 CLT 2025",
  "total_responses": 610,
  "unique_prompts": 30,
  "completeness_analysis": {
    "score": 0.525,
    "truncated_count": 290,
    "truncation_rate": 0.475,
    "issues": [
      {
        "description": "290 responses truncated",
        "severity": "high",
        "affected_count": 290,
        "reasons": {"TIMEOUT": 290}
      }
    ]
  },
  "structural_analysis": {
    "score": 0.850,
    "issues": []
  },
  "semantic_analysis": {
    "score": 0.306,
    "issues": [
      {
        "prompt": "Propón nombres creativos para una startup de IA",
        "description": "Low semantic similarity between responses",
        "severity": "medium",
        "similarity_score": 0.099,
        "response_count": 20
      }
    ]
  },
  "temporal_analysis": {
    "score": 0.850,
    "ramp_truncation_rate": 0.05,
    "steady_truncation_rate": 0.52,
    "ramp_avg_response_time_ms": 1009,
    "steady_avg_response_time_ms": 8826,
    "degradation_detected": true,
    "degradation_magnitude": 0.47
  },
  "category_analysis": {
    "score": 0.680,
    "categories": {
      "short": {
        "response_count": 84,
        "truncation_rate": 0.083,
        "avg_response_time_ms": 1250,
        "score": 0.917
      },
      "long": {
        "response_count": 81,
        "truncation_rate": 0.704,
        "avg_response_time_ms": 9200,
        "score": 0.296
      }
    }
  },
  "global_consistency_score": 0.505,
  "summary": "⚠️ Consistencia preocupante - Score global: 50.5%. Analizadas 610 respuestas. 290 respuestas truncadas. Degradación detectada bajo carga sostenida."
}
```

---

## 🛠️ Dependencias del Proyecto

### Maven `pom.xml`

```xml
<!-- Gatling para load testing -->
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>3.11.3</version>
    <scope>test</scope>
</dependency>

<!-- Jackson para JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.18.0</version>
</dependency>

<!-- Gson para parsing SSE -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
</dependency>
```

---

## 🚀 Guía de Uso Rápido

### Ejecutar Test Completo

```bash
# 1. Configurar API key
export api_key="sk-proj-..."

# 2. Ejecutar load test
./mvnw gatling:test

# 3. Agregar respuestas
java -cp target/test-classes ssellm.ResponseAggregator

# 4. Analizar consistencia
java -cp target/test-classes ssellm.ConsistencyAnalyzer

# 5. Ver resultados
cat target/consistency_analysis.json
open target/gatling/*.html  # Reportes Gatling
```

### Ver Archivos Generados

```bash
ls -lh target/

-rw-r--r--  responses_metadata.jsonl   # 610 líneas JSONL
-rw-r--r--  responses_by_prompt.json   # Agrupación por prompt
-rw-r--r--  consistency_analysis.json  # Reporte final
-rw-r--r--  llm_response.txt          # Formato legible
-rw-r--r--  sse_chunks.txt            # Raw SSE chunks
drwxr-xr-x  gatling/                  # Reportes HTML Gatling
```

---

## 🔮 Próximos Pasos (Sprint 2-4)

### Sprint 2: Análisis LLM Avanzado
- **LLM-as-a-judge**: Usar GPT-4 para evaluar calidad semántica
- **Prompt engineering**: Diseñar prompts de evaluación efectivos
- Reemplazar Jaccard similarity con análisis LLM real

### Sprint 3: Visualización y Automatización
- **Dashboard HTML**: Gráficos interactivos con Plotly.js
- **Script automatizado**: `run_quality_test.sh` para todo el flujo
- **Umbrales configurables**: YAML con SLAs y alertas

### Sprint 4: ML Avanzado (Opcional)
- **Embeddings vectoriales**: text-embedding-3 para semántica
- **Anomaly detection**: Isolation Forest para outliers
- **Multi-modelo**: Comparar GPT-3.5 vs GPT-4 vs Claude

---

## 📚 Referencias

- **Código fuente**: `/src/test/java/ssellm/`
- **Documentación Sprint 1**: `/docs/sprint1/`
- **Artículo de consistencia**: `/docs/sprint1/consistency-article.md`
- **Roadmap**: `/docs/README.md`

---

**Última actualización**: Octubre 2025
**Autor**: Ricardo Campos
**Licencia**: MIT
