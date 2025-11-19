# 📚 Documentación Técnica del Código - Sprint 2

**Última actualización**: Noviembre 2025
**Autor**: Rodrigo Campos .T
**Versión**: 2.0 (Sprint 2 - Análisis Avanzado)

---

## 🎯 Visión General del Sistema

Sprint 2 extiende el sistema de Sprint 1 agregando **análisis semántico avanzado** con:

1. **Timeouts dinámicos por categoría** (SSELLM.java)
2. **Análisis semántico con embeddings** (SemanticAnalyzer.java)
3. **Evaluación cualitativa con GPT-4** (LLMJudge.java)
4. **Cliente OpenAI nativo** (OpenAIClient.java)
5. **Generación de reportes completos** (QualityReportGenerator.java)

**Stack de modelos OpenAI**:
- **Target de pruebas**: GPT-3.5-turbo-0125
- **Análisis semántico**: text-embedding-3-small
- **LLM Judge**: GPT-4o-2024-08-06

---

## 📐 Arquitectura del Sistema Sprint 2

```
┌─────────────────────────────────────────────────────────────────┐
│                      SSELLM.java (Gatling)                      │
│            Load Test + SSE + Timeouts Dinámicos                 │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ├─> Timeouts por categoría (5s-20s)
                        ├─> Captura chunks en tiempo real
                        ├─> Calcula métricas (17 campos)
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              target/responses_metadata.jsonl                    │
│         17 campos × 610 responses = Metadata completa           │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              QualityReportGenerator.java                        │
│     Pipeline completo: Básico + Semántico + LLM Judge          │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ├──> SemanticAnalyzer.java (OpenAI Embeddings)
                        ├──> LLMJudge.java (GPT-4 evaluation)
                        └──> AdvancedMetrics.java (Estadísticas avanzadas)
                        │
                        ▼
        quality_report_sprint2_new.json (Report final)
```

---

## 📁 Estructura de Archivos Sprint 2

```
src/test/java/ssellm/
├── SSELLM.java                            # MODIFICADO: Timeouts dinámicos
├── analyzers/                             # NUEVO paquete
│   ├── SemanticAnalyzer.java             # Análisis con embeddings
│   ├── LLMJudge.java                     # GPT-4 como juez
│   ├── QualityReportGenerator.java       # Pipeline completo
│   └── AdvancedMetrics.java              # Métricas avanzadas
├── clients/                               # NUEVO paquete
│   └── OpenAIClient.java                 # Cliente OpenAI nativo
└── models/                                # EXTENDIDO
    ├── ResponseMetadata.java             # MODIFICADO: +1 campo (timeout_used_ms)
    ├── SemanticAnalysisResult.java       # NUEVO
    ├── LLMJudgeEvaluation.java           # NUEVO
    └── QualityReport.java                # NUEVO

target/
├── responses_metadata.jsonl               # 610 líneas JSONL
├── quality_report_sprint2_new.json       # NUEVO: Reporte completo
└── gatling/                              # Reportes Gatling
```

---

## 🆕 Nuevas Clases Sprint 2

### 📦 Clase: `OpenAIClient.java`

**Ubicación**: `src/test/java/ssellm/clients/OpenAIClient.java`

**Propósito**: Cliente HTTP nativo para comunicarse con OpenAI API (embeddings y GPT-4).

#### 🔍 Métodos Principales

```java
public class OpenAIClient {
    private final HttpClient httpClient;
    private final String apiKey;

    // Embeddings
    public List<Double> getEmbedding(String text) throws Exception;
    public List<List<Double>> getEmbeddings(List<String> texts) throws Exception;

    // GPT-4 Judge
    public String evaluateWithGPT4(String systemPrompt, String userPrompt) throws Exception;
    public String evaluateWithGPT4JSON(String systemPrompt, String userPrompt) throws Exception;

    // Utilidades
    public boolean testConnection();
}
```

**Características técnicas**:
- Java 11 HTTP client nativo (sin dependencias externas)
- Timeout configurable (60-120 segundos)
- Manejo de errores robusto
- Soporte para JSON mode (structured output)

**Ejemplo de uso**:
```java
OpenAIClient client = new OpenAIClient(apiKey);

// Obtener embeddings
List<Double> embedding = client.getEmbedding("Texto a analizar");

// Evaluación con GPT-4
String evaluation = client.evaluateWithGPT4JSON(systemPrompt, userPrompt);
```

---

### 📦 Clase: `SemanticAnalyzer.java`

**Ubicación**: `src/test/java/ssellm/analyzers/SemanticAnalyzer.java`

**Propósito**: Análisis de similitud semántica usando embeddings de OpenAI.

#### 🔍 Método Principal

```java
public SemanticAnalysisResult analyzeSimilarity(
    String prompt,
    String category,
    List<String> responses
) {
    // 1. Obtener embeddings de todas las respuestas
    List<List<Double>> embeddings = openAIClient.getEmbeddings(responses);

    // 2. Calcular matriz de similitud (cosine similarity)
    double[][] similarityMatrix = calculateSimilarityMatrix(embeddings);

    // 3. Calcular estadísticas
    double avgSimilarity = calculateAverage(similarityMatrix);
    double minSimilarity = findMin(similarityMatrix);
    double maxSimilarity = findMax(similarityMatrix);

    // 4. Detectar inconsistencias (threshold: 0.70)
    boolean isConsistent = avgSimilarity >= SIMILARITY_THRESHOLD;
    List<String> issues = detectIssues(avgSimilarity, minSimilarity);

    return SemanticAnalysisResult.builder()
        .prompt(prompt)
        .avgSimilarity(avgSimilarity)
        .minSimilarity(minSimilarity)
        .maxSimilarity(maxSimilarity)
        .isConsistent(isConsistent)
        .issues(issues)
        .build();
}
```

**Algoritmo de Cosine Similarity**:
```java
private double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
    // Producto punto
    double dotProduct = 0.0;
    for (int i = 0; i < vec1.size(); i++) {
        dotProduct += vec1.get(i) * vec2.get(i);
    }

    // Normas
    double norm1 = Math.sqrt(vec1.stream().mapToDouble(x -> x * x).sum());
    double norm2 = Math.sqrt(vec2.stream().mapToDouble(x -> x * x).sum());

    return dotProduct / (norm1 * norm2);
}
```

**Threshold de consistencia**: 0.70 (70% de similitud mínima)

---

### 📦 Clase: `LLMJudge.java`

**Ubicación**: `src/test/java/ssellm/analyzers/LLMJudge.java`

**Propósito**: Evaluación cualitativa de respuestas usando GPT-4 como juez.

#### 🔍 Método Principal

```java
public LLMJudgeEvaluation evaluate(
    String prompt,
    String category,
    List<String> responses
) {
    // 1. Seleccionar muestra (sampling)
    List<String> sample = sampleResponses(responses, 5);

    // 2. Construir prompts de evaluación
    String systemPrompt = buildSystemPrompt();
    String userPrompt = buildUserPrompt(prompt, category, sample);

    // 3. Llamar a GPT-4 con JSON mode
    String jsonResponse = openAIClient.evaluateWithGPT4JSON(systemPrompt, userPrompt);

    // 4. Parsear respuesta JSON
    JsonObject result = JsonParser.parseString(jsonResponse).getAsJsonObject();

    return LLMJudgeEvaluation.builder()
        .prompt(prompt)
        .category(category)
        .similarityScore(result.get("similarity").getAsDouble())
        .technicalCorrectness(result.get("technical_correctness").getAsDouble())
        .coherenceScore(result.get("coherence").getAsDouble())
        .creativityExpected(result.get("creativity_expected").getAsBoolean())
        .issuesDetected(parseIssues(result))
        .legitimateVariations(parseVariations(result))
        .overallScore(calculateOverallScore(result))
        .build();
}
```

**System Prompt** (Resumen):
```java
private String buildSystemPrompt() {
    return """
        Eres un evaluador experto de respuestas LLM. Evalúa según:
        1. Similarity (0-10): Similitud semántica entre respuestas
        2. Technical Correctness (0-10): Corrección técnica
        3. Coherence (0-10): Coherencia y completitud
        4. Creativity Expected (bool): ¿Es esperada la variación?

        Distingue entre:
        - Variaciones legítimas (diferentes enfoques válidos)
        - Inconsistencias problemáticas (errores, omisiones)

        Output JSON:
        {
          "similarity": float,
          "technical_correctness": float,
          "coherence": float,
          "creativity_expected": bool,
          "issues": [str],
          "variations": [str]
        }
        """;
}
```

---

### 📦 Clase: `QualityReportGenerator.java`

**Ubicación**: `src/test/java/ssellm/analyzers/QualityReportGenerator.java`

**Propósito**: Orquestador principal que ejecuta todo el pipeline de análisis.

#### 🔍 Pipeline Completo

```java
public void generateReport(String inputFile, String outputFile) {
    // [1/6] Cargar metadata
    List<ResponseMetadata> responses = loadMetadata(inputFile);

    // [2/6] Calcular métricas básicas
    double truncationRate = calculateTruncationRate(responses);

    // [3/6] Agrupar por prompt
    Map<String, List<ResponseMetadata>> byPrompt = groupByPrompt(responses);

    // [4/6] Análisis semántico (30% sampling)
    List<SemanticAnalysisResult> semanticResults =
        analyzeSemantic(byPrompt, 0.30);

    // [5/6] LLM Judge (30% de analyzed prompts)
    List<LLMJudgeEvaluation> judgeResults =
        evaluateWithJudge(semanticResults, 0.30);

    // [6/6] Generar reporte final
    QualityReport report = buildReport(
        responses,
        semanticResults,
        judgeResults
    );

    saveReport(report, outputFile);
}
```

**Salida del pipeline**:
```
================================================================================
📊 SPRINT 2 - QUALITY REPORT GENERATOR
================================================================================

[1/6] 📂 Loading metadata file...
   ✓ Loaded 610 responses

[2/6] 📈 Calculating basic metrics...
   ✓ Total responses: 610
   ✓ Truncated: 0 (0.0%)

[3/6] 🗂️ Grouping responses by prompt...
   ✓ 30 unique prompts
   ✓ 9 categories

[4/6] 🔍 Running semantic analysis...
   📊 Analyzing 9 prompts (sampled at 30%)
   ✅ Semantic analysis complete: 9 prompts analyzed

[5/6] ⚖️ Running LLM-as-judge evaluation...
   ⚖️ Evaluating 5 prompts with GPT-4
   ✅ LLM judge evaluation complete: 5 prompts evaluated

[6/6] 📊 Analyzing by category and phase...

💾 Saving report to: quality_report_sprint2_new.json
   ✓ Report saved successfully

================================================================================
📊 QUALITY REPORT SUMMARY
================================================================================

📈 Overall Metrics:
   Total Responses: 610
   Truncation Rate: 0.0%

🔍 Semantic Analysis:
   Prompts Analyzed: 9
   Avg Similarity: 0.889

⚖️ LLM Judge Evaluation:
   Prompts Evaluated: 5
   Avg LLM Score: 7.4/10

================================================================================

✅ Quality report generated successfully!
```

---

## 🔧 Cambios Principales Sprint 1 → Sprint 2

### 1. Timeouts Dinámicos en `SSELLM.java`

**Antes (Sprint 1)**:
```java
boolean timedOut = elapsed > 10000; // Timeout global 10s
```

**Después (Sprint 2)**:
```java
private long getTimeoutForCategory(String category) {
    switch (category.toLowerCase()) {
        case "short":
        case "creative":
            return 5000;  // 5s

        case "medium":
        case "code_generation":
        case "analysis":
            return 12000; // 12s

        case "long":
        case "contextual":
        case "troubleshooting":
        case "documentation":
            return 20000; // 20s

        default:
            return 10000; // 10s fallback
    }
}

// Uso en detección de timeout
long timeout = getTimeoutForCategory(category);
boolean timedOut = elapsed > timeout;
```

**Nuevo campo en metadata**:
```java
@JsonProperty("timeout_used_ms")
private long timeoutUsedMs; // Timeout aplicado para esta categoría
```

---

### 2. Análisis Semántico: Jaccard → Embeddings

**Sprint 1 (Jaccard - Limitado)**:
```java
// Compara keywords literales
Set<String> keywords1 = extractKeywords(response1);
Set<String> keywords2 = extractKeywords(response2);
double similarity = intersection / union; // Score: 0.306
```

**Sprint 2 (Embeddings - Preciso)**:
```java
// Compara significado semántico
List<Double> emb1 = openAIClient.getEmbedding(response1);
List<Double> emb2 = openAIClient.getEmbedding(response2);
double similarity = cosineSimilarity(emb1, emb2); // Score: 0.889
```

**Mejora**: +190% en precisión (0.306 → 0.889)

---

### 3. Evaluación Cualitativa: Manual → Automatizada

**Sprint 1**:
- ❌ No existía evaluación cualitativa
- ❌ Solo métricas numéricas (truncamiento, latencia)
- ❌ No detección de issues específicos

**Sprint 2**:
- ✅ GPT-4 como juez automático
- ✅ Scores en 4 dimensiones (similarity, technical, coherence, creativity)
- ✅ Detección de issues específicos con descripción
- ✅ Distinción entre variación legítima vs inconsistencia

---

## 📊 Estructura del Reporte Sprint 2

### JSON Output: `quality_report_sprint2_new.json`

```json
{
  "timestamp": 1762750095.823,
  "total_requests": 610,
  "summary": {
    "truncation_rate": 0.0,
    "avg_similarity_embeddings": 0.889,
    "avg_llm_judge_score": 7.4
  },
  "by_prompt": [
    {
      "prompt": "Traduce 'Hello World' al español",
      "category": "short",
      "responses_count": 21,
      "truncation_rate": 0.0,
      "avg_response_time": 301.2,
      "similarity_embeddings": 0.9999,
      "llm_judge_score": 10.0,
      "issues": []
    },
    {
      "prompt": "Analiza este código y sugiere mejoras...",
      "category": "medium",
      "responses_count": 21,
      "truncation_rate": 0.0,
      "avg_response_time": 2954.8,
      "similarity_embeddings": 0.719,
      "llm_judge_score": null,
      "issues": []
    }
  ],
  "by_category": {
    "short": {
      "response_count": 84,
      "truncation_rate": 0.0,
      "avg_response_time": 511.3,
      "score": 9.92
    },
    "long": {
      "response_count": 81,
      "truncation_rate": 0.0,
      "avg_response_time": 4770.9,
      "score": 9.28
    }
  },
  "by_phase": {
    "ramp": {
      "response_count": 10,
      "avg_response_time": 1411.0,
      "truncation_rate": 0.0
    },
    "steady": {
      "response_count": 600,
      "avg_response_time": 2872.0,
      "truncation_rate": 0.0
    },
    "degradation_magnitude": 103.0
  }
}
```

---

## 🚀 Guía de Uso Rápido Sprint 2

### Script Automatizado

```bash
#!/bin/bash
# run_sprint2_analysis.sh

# 1. Configurar API key
export api_key=$(grep "api_key" .env | cut -d'=' -f2 | tr -d '"')

# 2. Ejecutar test de Gatling
./mvnw gatling:test -Dgatling.simulationClass=ssellm.SSELLM

# 3. Generar classpath (si no existe)
./mvnw dependency:build-classpath -Dmdep.outputFile=classpath.txt

# 4. Ejecutar análisis completo con embeddings + GPT-4 judge
java -cp "target/test-classes:$(cat classpath.txt)" \
  ssellm.analyzers.QualityReportGenerator \
  target/responses_metadata.jsonl \
  quality_report_sprint2_new.json

# 5. Ver resultados
cat quality_report_sprint2_new.json
```

### Ejecución Manual

```bash
# 1. Load test
export api_key="sk-proj-..."
./mvnw gatling:test

# 2. Análisis completo
./mvnw dependency:build-classpath -Dmdep.outputFile=classpath.txt
java -cp "target/test-classes:$(cat classpath.txt)" \
  ssellm.analyzers.QualityReportGenerator \
  target/responses_metadata.jsonl \
  quality_report_sprint2.json
```

---

## 🛠️ Dependencias Nuevas Sprint 2

### Maven `pom.xml` (Agregadas)

```xml
<!-- Apache Commons Math para cosine similarity -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
    <scope>test</scope>
</dependency>
```

**Nota**: No se agregaron más dependencias. OpenAI client usa Java 11 HTTP nativo.

---

## 📈 Métricas Capturadas Sprint 2

| Métrica | Sprint 1 | Sprint 2 | Mejora |
|---------|----------|----------|--------|
| **Campos de metadata** | 16 | 17 (+timeout_used_ms) | +6.25% |
| **Truncamiento** | 47.5% | 0.0% | -100% |
| **Similitud semántica** | 0.306 (Jaccard) | 0.889 (Embeddings) | +190% |
| **Evaluación cualitativa** | ❌ No existe | ✅ 7.4/10 (GPT-4o-2024-08-06) | Nuevo |
| **Costo por análisis** | $0.30 | $0.45 | +50% |

---

## 🔮 Próximos Pasos (Sprint 3-4)

### Sprint 3: Optimización de Carga
- Experimentar con diferentes niveles de concurrencia (5, 10, 15, 20 usuarios/seg)
- Encontrar el punto óptimo carga/calidad
- Implementar circuit breakers inteligentes

### Sprint 4: Dashboard y CI/CD
- Dashboard HTML interactivo (Plotly/D3.js)
- Integración con GitHub Actions
- Alertas automáticas cuando score < threshold

---

## 📚 Referencias Técnicas

### **Documentación del Proyecto**
- **Código fuente Sprint 2**: `/src/test/java/ssellm/analyzers/`
- **Documentación Sprint 2**: `/docs/sprint2/`
- **Artículo técnico**: `/docs/sprint2/linkedin-article.md`
- **Reporte de validación**: `/docs/sprint2/validation-report.md`

### **Referencias Oficiales de OpenAI**

#### **Modelos Utilizados**
- **GPT-3.5 Turbo**: [Chat Completions API Documentation](https://platform.openai.com/docs/guides/text-generation)
  - Versión específica: GPT-3.5-turbo-0125
- **GPT-4o**: [GPT-4 and GPT-4 Turbo](https://platform.openai.com/docs/models/gpt-4-and-gpt-4-turbo)
  - Versión específica: GPT-4o-2024-08-06
- **Text Embeddings**: [Embeddings Guide](https://platform.openai.com/docs/guides/embeddings)
  - Modelo: text-embedding-3-small

#### **APIs y Best Practices**
- **Chat Completions API**: [API Reference](https://platform.openai.com/docs/api-reference/chat)
- **Embeddings API**: [API Reference](https://platform.openai.com/docs/api-reference/embeddings)
- **Rate Limits**: [Rate limits - OpenAI API](https://platform.openai.com/docs/guides/rate-limits)
- **Production Best Practices**: [Production best practices](https://platform.openai.com/docs/guides/production-best-practices)
- **Streaming (SSE)**: [How to stream completions](https://platform.openai.com/docs/api-reference/streaming)

#### **Pricing**
- **Pricing Calculator**: [OpenAI Pricing](https://openai.com/api/pricing/)
- **GPT-3.5-turbo-0125**: $0.0005/1K tokens (input), $0.0015/1K tokens (output)
- **text-embedding-3-small**: $0.00002/1K tokens
- **GPT-4o-2024-08-06**: $2.50/1M tokens (input), $10.00/1M tokens (output)

### **Herramientas de Testing**
- **Gatling**: [Gatling Load Testing](https://gatling.io/docs/gatling/)
- **Apache Commons Math**: [Cosine Similarity](https://commons.apache.org/proper/commons-math/)

---

**Última actualización**: Noviembre 19, 2025
**Autor**: Rodrigo Campos .T
**Versión**: 2.0 (Production-Ready)
