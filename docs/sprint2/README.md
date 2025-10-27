# 🧠 Sprint 2: Análisis Avanzado con LLM

**Estado**: 📝 Planificado
**Duración estimada**: 1 semana (5 días hábiles)
**Dependencias**: Sprint 1 completado ✅

---

## 🎯 Objetivos

Sprint 2 se enfoca en **análisis semántico avanzado** usando LLM-as-a-judge para evaluar la calidad del contenido generado.

**Principales entregables:**
1. **Integración con LLM** para análisis semántico (GPT-4)
2. **Prompt engineering** optimizado para evaluación de respuestas
3. **QualityReportGenerator.java** con métricas agregadas
4. **Métricas avanzadas** de coherencia, relevancia y corrección técnica

---

## 📋 Tareas Sprint 2

### ✅ **Tarea 2.2: Integración con LLM para Análisis Semántico**

**Objetivo**: Reemplazar Jaccard similarity con evaluación LLM real.

**Implementación:**

```java
public class LLMAnalyzer {
    private final OpenAIClient client;

    public SemanticAnalysisResult analyzeSimilarity(
        String prompt,
        List<String> responses
    ) {
        // Usar GPT-4 para comparar respuestas del mismo prompt
        String evaluationPrompt = buildEvaluationPrompt(prompt, responses);

        ChatCompletion completion = client.chat()
            .model("gpt-4")
            .messages(List.of(
                new Message("system", EVALUATION_SYSTEM_PROMPT),
                new Message("user", evaluationPrompt)
            ))
            .create();

        return parseSemanticResult(completion.choices().get(0).message().content());
    }
}
```

**Outputs esperados:**
- Similarity score (0-1) basado en similitud semántica real
- Identificación de variaciones legítimas (creatividad) vs inconsistencias técnicas
- Detección de alucinaciones o información incorrecta

**Estimado**: 2 días

---

### ✅ **Tarea 2.3: Implementar Prompt Mejorado**

**Objetivo**: Diseñar prompts efectivos para que GPT-4 evalúe calidad de respuestas.

**Prompt de evaluación (draft):**

```markdown
# System Prompt
Eres un evaluador experto de respuestas de LLMs. Tu trabajo es analizar respuestas
generadas por GPT-3.5-turbo y evaluar su calidad según criterios objetivos.

# Evaluation Prompt Template
Analiza las siguientes respuestas al prompt: "{original_prompt}"

**Respuestas a evaluar:**
1. {response_1}
2. {response_2}
...
N. {response_N}

**Criterios de evaluación:**

1. **Similitud Semántica (0-10)**: ¿Las respuestas comunican el mismo mensaje?
   - 10: Idénticas en significado
   - 7-9: Variaciones de estilo pero mismo contenido
   - 4-6: Algunas diferencias de contenido
   - 0-3: Contenido significativamente diferente

2. **Corrección Técnica (0-10)**: Para prompts técnicos, ¿las respuestas son correctas?
   - Validar precisión de código, conceptos, terminología

3. **Coherencia (0-10)**: ¿Las respuestas son lógicas y completas?

4. **Detección de Problemas**:
   - ¿Hay alucinaciones (información inventada)?
   - ¿Hay respuestas que contradigan el prompt?
   - ¿Hay respuestas incompletas o cortadas?

**Output esperado (JSON):**
{
  "similarity_score": 0.85,
  "technical_correctness": 9.2,
  "coherence_score": 8.5,
  "issues_detected": [
    "Response 3 contains hallucinated information about X",
    "Response 7 is incomplete (truncated)"
  ],
  "legitimate_variations": [
    "Responses use different code implementations (recursion vs iteration) but both correct"
  ]
}
```

**Validación del prompt:**
- Testear con 5-10 casos conocidos (prompts creativos vs técnicos)
- Validar que distingue creatividad de inconsistencia
- Ajustar según falsos positivos/negativos

**Estimado**: 1 día

---

### ✅ **Tarea 3.1: QualityReportGenerator.java**

**Objetivo**: Generar reportes agregados con scores de calidad por dimensión.

**Implementación:**

```java
public class QualityReportGenerator {

    public QualityReport generateReport(List<ResponseMetadata> responses) {
        QualityReport report = new QualityReport();

        // Agrupar por prompt
        Map<String, List<ResponseMetadata>> byPrompt =
            responses.stream()
                .collect(Collectors.groupingBy(ResponseMetadata::getPrompt));

        // Para cada prompt, analizar consistencia
        for (Map.Entry<String, List<ResponseMetadata>> entry : byPrompt.entrySet()) {
            String prompt = entry.getKey();
            List<ResponseMetadata> promptResponses = entry.getValue();

            // Análisis básico (Sprint 1)
            BasicAnalysis basic = analyzeBasic(promptResponses);

            // Análisis LLM (Sprint 2 - NUEVO)
            LLMAnalysis llm = llmAnalyzer.analyzeSimilarity(
                prompt,
                extractTexts(promptResponses)
            );

            PromptQualityScore score = new PromptQualityScore(
                prompt,
                basic.truncationRate(),
                basic.avgResponseTime(),
                llm.similarityScore(),
                llm.technicalCorrectness(),
                llm.coherenceScore()
            );

            report.addPromptScore(score);
        }

        // Scores globales
        report.setGlobalConsistencyScore(calculateGlobalScore(report));
        report.setTimestamp(Instant.now());

        return report;
    }
}
```

**Output JSON:**

```json
{
  "timestamp": "2025-10-26T10:30:00Z",
  "global_consistency_score": 0.752,
  "total_requests": 610,
  "summary": {
    "truncation_rate": 0.475,
    "avg_similarity": 0.82,
    "avg_technical_correctness": 8.5,
    "avg_coherence": 8.1
  },
  "by_prompt": [
    {
      "prompt": "Implementa búsqueda binaria en Java",
      "responses_count": 20,
      "truncation_rate": 0.15,
      "similarity_score": 0.75,
      "technical_correctness": 9.2,
      "coherence": 8.8,
      "issues": ["2 responses truncated"]
    }
  ]
}
```

**Estimado**: 1 día

---

### ✅ **Tarea 3.2: Métricas Avanzadas**

**Objetivo**: Expandir métricas más allá de lo básico de Sprint 1.

**Nuevas métricas:**

1. **Prompt Category Performance**
   - Score por categoría (short, medium, long, creative, technical)
   - Identificar qué categorías tienen peor calidad bajo carga

2. **Degradation by Phase**
   - Comparar RAMP vs STEADY en todas las métricas
   - No solo latencia, también calidad semántica

3. **Quality vs Speed Correlation**
   - ¿Respuestas más rápidas son de menor calidad?
   - Plot: response_time vs semantic_score

4. **False Positive Rate (Sprint 2)**
   - Comparar Jaccard (Sprint 1) vs LLM (Sprint 2)
   - Documentar cuántos falsos positivos eliminamos

**Implementación:**

```java
public class AdvancedMetrics {

    public CategoryPerformance analyzeByCategoryPerformance(QualityReport report) {
        // Agrupar por categoría y calcular scores promedio
        return report.getPromptScores().stream()
            .collect(Collectors.groupingBy(
                PromptQualityScore::getCategory,
                Collectors.averagingDouble(PromptQualityScore::getOverallScore)
            ));
    }

    public PhaseComparison comparePhases(QualityReport report) {
        List<PromptQualityScore> rampScores = filterByPhase(report, "RAMP");
        List<PromptQualityScore> steadyScores = filterByPhase(report, "STEADY");

        return new PhaseComparison(
            avgScore(rampScores),
            avgScore(steadyScores),
            calculateDegradation(rampScores, steadyScores)
        );
    }
}
```

**Estimado**: 1 día

---

## 📊 Métricas de Éxito Sprint 2

| Métrica | Sprint 1 (Actual) | Sprint 2 (Objetivo) |
|---------|-------------------|---------------------|
| **Análisis Semántico** | Jaccard 0.306 (no confiable) | LLM score >0.7 (confiable) |
| **Falsos Positivos** | Muchos (no cuantificado) | <10% validado |
| **Cobertura de Análisis** | 100% (Jaccard simple) | 20-30% (LLM muestreado por costo) |
| **Detección Alucinaciones** | No | Sí (via LLM-as-judge) |
| **Costo por Test** | $0.30 (solo generación) | $0.30 + $2-3 (análisis LLM) ≈ $3 total |

---

## 💰 Presupuesto Sprint 2

| Componente | Costo Estimado | Justificación |
|------------|----------------|---------------|
| Test original (610 responses) | $0.30 | Sprint 1 (sin cambios) |
| Análisis LLM (20% sampling) | $2-3 | GPT-4 evaluando 120 respuestas |
| Iteración de prompts | $0.50 | Testing del evaluation prompt |
| **Total Sprint 2** | **~$3.50** | Por test completo |

**Estrategia de muestreo:**
- Evaluar 20-30% de respuestas con LLM (no 100% por costo)
- Priorizar prompts con baja similitud Jaccard (posibles problemas)
- Validar manualmente subset de resultados LLM

---

## 🔄 Plan de Implementación (5 días)

### **Día 1: Setup y Prompt Engineering**
- [ ] Setup de OpenAI API con GPT-4
- [ ] Diseño del evaluation prompt
- [ ] Testing con 5-10 casos conocidos
- [ ] Ajuste de prompt según resultados

### **Día 2: Integración LLM**
- [ ] Implementar `LLMAnalyzer.java`
- [ ] Integrar con `ConsistencyAnalyzer` de Sprint 1
- [ ] Testing de integración
- [ ] Manejo de rate limits y errores

### **Día 3: QualityReportGenerator**
- [ ] Implementar `QualityReportGenerator.java`
- [ ] Formato JSON del reporte
- [ ] Validar con datos de Sprint 1
- [ ] Testing unitario

### **Día 4: Métricas Avanzadas**
- [ ] Implementar `AdvancedMetrics.java`
- [ ] Category performance analysis
- [ ] Phase comparison (RAMP vs STEADY)
- [ ] Quality vs Speed correlation

### **Día 5: Testing y Documentación**
- [ ] Re-ejecutar test de 610 requests
- [ ] Generar reporte Sprint 2
- [ ] Comparar Sprint 1 vs Sprint 2
- [ ] Documentar lecciones aprendidas
- [ ] Update `SPRINT2_VALIDATION_REPORT.md`

---

## 🎯 Entregables Sprint 2

1. **Código:**
   - `src/test/java/ssellm/LLMAnalyzer.java`
   - `src/test/java/ssellm/QualityReportGenerator.java`
   - `src/test/java/ssellm/AdvancedMetrics.java`

2. **Reportes:**
   - `target/quality_report_sprint2.json`
   - `docs/sprint2/validation-report.md`
   - Comparativa Sprint 1 vs Sprint 2

3. **Documentación:**
   - Evaluation prompts usados
   - Análisis de falsos positivos eliminados
   - Recomendaciones para Sprint 3

---

## 🚨 Riesgos y Mitigaciones

### Riesgo 1: Costo de GPT-4
**Impacto**: $3-5 por test completo puede ser prohibitivo
**Mitigación**: Sampling (20-30%), no evaluar todo al 100%

### Riesgo 2: Rate Limits de OpenAI
**Impacto**: Test puede fallar si excedemos límites
**Mitigación**: Implement exponential backoff, retry logic

### Riesgo 3: Consistencia de GPT-4
**Impacto**: Evaluaciones pueden variar entre ejecuciones
**Mitigación**: Temperature=0, validar con 3 ejecuciones del mismo caso

---

## 📚 Referencias

- [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [LLM-as-a-Judge Best Practices](https://www.deeplearning.ai/short-courses/quality-safety-llm-applications/)
- Sprint 1: `docs/sprint1/consistency-article.md`

---

**Estado**: Planificado | **Owner**: Ricardo Campos | **Última actualización**: Octubre 2025
