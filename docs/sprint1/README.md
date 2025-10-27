# Sprint 1: Sistema de Análisis de Calidad - Guía de Uso

## 📋 Resumen

El Sprint 1 implementa un **sistema automatizado de análisis de consistencia** para respuestas de LLMs bajo carga concurrente. El sistema captura metadatos enriquecidos durante el load test y realiza análisis exhaustivo de calidad sin necesidad de llamadas externas a LLMs.

---

## 🎯 Objetivos Completados

✅ **Tarea 1.1**: Enriquecer metadatos capturados
✅ **Tarea 1.2**: Implementar detección de truncamiento
✅ **Tarea 1.3**: Agrupar respuestas por prompt
✅ **Tarea 2.1**: Crear analizador de consistencia básico

---

## 🏗️ Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│ 1. LOAD TEST (SSELLM.java)                                 │
│    - Ejecuta Gatling con SSE streaming                     │
│    - Captura metadatos enriquecidos                         │
│    - Detecta truncamientos automáticamente                  │
│    - Salida: responses_metadata.jsonl                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. AGREGACIÓN (ResponseAggregator.java)                    │
│    - Lee archivo JSONL                                      │
│    - Agrupa respuestas por prompt                           │
│    - Calcula estadísticas de truncamiento                   │
│    - Salida: responses_by_prompt.json                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. ANÁLISIS (ConsistencyAnalyzer.java)                     │
│    - Analiza 5 dimensiones de consistencia                  │
│    - Calcula score global ponderado                         │
│    - Genera reporte con issues y recomendaciones            │
│    - Salida: consistency_analysis.json                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Metadatos Capturados

### Antes (Sprint 0)
```
Session ID: 2-Scenario
Chunk ID: chatcmpl-xyz
Category: short
Prompt: ¿Qué es la fotosíntesis?
Response: [contenido]
```

### Ahora (Sprint 1)
```json
{
  "session_id": "2-Scenario",
  "chunk_id": "chatcmpl-xyz",
  "user_id": 2,
  "category": "short",
  "prompt": "¿Qué es la fotosíntesis?",
  "max_tokens": 200,
  "temperature": 0.7,
  "response": "[contenido]",
  "response_length": 285,
  "timestamp": "2025-01-15T10:23:45.123Z",
  "response_time_ms": 1250,
  "ttft_ms": 320,
  "total_chunks": 8,
  "truncated": false,
  "truncation_reason": "NONE",
  "test_phase": "STEADY"
}
```

---

## 🔍 Dimensiones de Análisis

### 1. Consistencia Semántica (40%)
**Método**: Jaccard similarity sobre keywords extraídos
**Penaliza**: Baja similitud entre respuestas al mismo prompt
**Umbral**: < 0.6 = problema

### 2. Consistencia Estructural (25%)
**Analiza**:
- Variación de longitud (> 50% = problema)
- Formato Markdown inconsistente
- Mezcla de idiomas

### 3. Consistencia de Completitud (25%)
**Detecta**:
- Respuestas truncadas por timeout (> 10s)
- Tasa de truncamiento global
- Razones de truncamiento (TIMEOUT, BUFFER_OVERFLOW)

### 4. Consistencia Temporal (5%)
**Compara**:
- Fase RAMP vs STEADY
- Degradación de calidad bajo carga sostenida
- Aumento de truncamientos en fase STEADY

### 5. Consistencia por Categoría (5%)
**Evalúa**:
- Impacto diferencial por tipo de prompt
- Vulnerabilidad de prompts `long` vs `short`

---

## 🚀 Uso del Sistema

### Opción 1: Script Automatizado (Recomendado)

```bash
# Configurar API key
export api_key=tu_openai_key

# Ejecutar flujo completo
./scripts/run_quality_analysis.sh
```

Esto ejecuta:
1. Compilación del proyecto
2. Load test con Gatling
3. Agregación de respuestas
4. Análisis de consistencia
5. Generación de reportes

---

### Opción 2: Ejecución Manual

#### Paso 1: Compilar
```bash
./mvnw clean compile test-compile
```

#### Paso 2: Ejecutar load test
```bash
export api_key=tu_openai_key
./mvnw gatling:test
```

#### Paso 3: Agregar respuestas
```bash
./mvnw exec:java -Dexec.mainClass="ssellm.ResponseAggregator" -Dexec.classpathScope=test
```

#### Paso 4: Analizar consistencia
```bash
./mvnw exec:java -Dexec.mainClass="ssellm.ConsistencyAnalyzer" -Dexec.classpathScope=test
```

---

## 📄 Reportes Generados

### 1. `target/responses_metadata.jsonl`
Formato: **JSONL** (JSON Lines)
Contenido: Un objeto JSON por línea con todos los metadatos
Uso: Entrada para análisis posteriores

### 2. `target/responses_by_prompt.json`
Formato: **JSON**
Contenido: Respuestas agrupadas por prompt
```json
{
  "¿Qué es la fotosíntesis?": {
    "category": "short",
    "max_tokens": 200,
    "temperature": 0.7,
    "total_responses": 10,
    "responses": [...]
  }
}
```

### 3. `target/consistency_analysis.json`
Formato: **JSON**
Contenido: Reporte completo de análisis
```json
{
  "global_consistency_score": 0.83,
  "total_responses": 100,
  "summary": "⚠️ Consistencia aceptable - Score global: 83.0%...",
  "completeness_analysis": { ... },
  "structural_analysis": { ... },
  "semantic_analysis": { ... },
  "temporal_analysis": { ... },
  "category_analysis": { ... }
}
```

### 4. `target/gatling/*/index.html`
Reporte HTML interactivo de Gatling con métricas de performance.

---

## 📈 Interpretación de Resultados

### Score Global

| Score | Interpretación | Acción |
|-------|---------------|--------|
| **0.90 - 1.00** | ✅ Excelente | Consistencia óptima, sin problemas |
| **0.75 - 0.89** | ⚠️ Aceptable | Ligeras variaciones, monitorear |
| **0.60 - 0.74** | ⚠️ Preocupante | Inconsistencias significativas, investigar |
| **< 0.60** | ❌ Crítico | Problemas serios, requiere acción inmediata |

### Análisis de Issues

El reporte incluye listas de `issues` para cada dimensión:

```json
{
  "issues": [
    {
      "prompt": "Crea una API REST en Java con Spring Boot...",
      "description": "High length variation: 65.3%",
      "severity": "high",
      "min_length": 450,
      "max_length": 1200,
      "avg_length": 825
    }
  ]
}
```

**Campos clave**:
- `severity`: `low`, `medium`, `high`
- `description`: Descripción del problema
- `prompt`: Prompt afectado (truncado a 60 chars)

---

## 🔧 Configuración

### Ajustar duración del test
Editar `SSELLM.java:278-280`:
```java
setUp(prompt.injectOpen(
    rampUsers(10).during(10),           // Ramp: 10 usuarios en 10s
    constantUsersPerSec(10).during(60)  // Steady: 10 req/s por 60s
)).protocols(httpProtocol);
```

### Ajustar timeout de truncamiento
Editar `SSELLM.java:180-183`:
```java
// Timeout detection (max 10 seconds per request)
long currentTime = System.currentTimeMillis();
long elapsed = currentTime - requestStartTime;
boolean timedOut = elapsed > 10000; // Cambiar 10000 (10s)
```

### Ajustar pesos de scoring
Editar `ConsistencyAnalyzer.java:calculateGlobalScore`:
```java
double score = ((double) completeness.get("score")) * 0.25 +  // Completitud
               ((double) structural.get("score")) * 0.25 +     // Estructural
               ((double) semantic.get("score")) * 0.40 +       // Semántica
               ((double) temporal.get("score")) * 0.05 +       // Temporal
               ((double) category.get("score")) * 0.05;        // Categoría
```

---

## 🐛 Troubleshooting

### Error: "api_key environment variable not set"
```bash
export api_key=your_openai_api_key_here
```

### Error: "Metadata file not found"
El archivo `responses_metadata.jsonl` solo se crea después del load test.
Ejecutar: `./mvnw gatling:test` primero.

### Reporte vacío o sin datos
Verificar que el test se completó exitosamente:
```bash
ls -lh target/responses_metadata.jsonl
```

Si el archivo existe pero está vacío, revisar logs de Gatling para errores de API.

### Errores de compilación
```bash
./mvnw clean compile test-compile
```

---

## 📊 Ejemplo de Salida

```
🔍 Starting Consistency Analysis...

✅ Loaded 100 responses from target/responses_metadata.jsonl
📊 Grouped responses:
  - "¿Qué es la fotosíntesis?" → 10 responses
  - "Define inteligencia artificial en una frase" → 10 responses
  - "Explica las ventajas y desventajas de usar microservicios..." → 10 responses
  [...]

📊 Analyzing completeness...
  ✓ Completeness score: 0.980

📊 Analyzing structural consistency...
  ✓ Structural score: 0.850

📊 Analyzing semantic consistency...
  ✓ Semantic score: 0.820

📊 Analyzing temporal patterns...
  ✓ Temporal score: 0.950

📊 Analyzing category impact...
  ✓ Category score: 0.900

🎯 Global Consistency Score: 0.860

✅ Consistency analysis completed!
💾 Analysis report saved to: target/consistency_analysis.json
```

---

## 🔜 Próximos Pasos (Sprint 2)

Sprint 1 implementa análisis **básico** sin LLM externo. Sprint 2 agregará:

1. **Análisis semántico avanzado** con embeddings de OpenAI
2. **Integración con LLM** para análisis cualitativo profundo
3. **Dashboard HTML interactivo** con visualizaciones
4. **Comparación histórica** entre múltiples ejecuciones
5. **Umbrales configurables** por categoría

---

## 📚 Referencias

### **Documentación**
- **[Documentación Técnica del Código](code-documentation.md)** - Explicación exhaustiva de todas las clases, métodos y flujo de ejecución
- **[Artículo de Consistencia](consistency-article.md)** - Hallazgos, lecciones aprendidas y análisis completo (1,048 líneas)
- **[Reporte de Validación](validation-report.md)** - Validación técnica del Sprint 1

### **Código Fuente**
- **Código principal**: `src/test/java/ssellm/SSELLM.java`
- **Modelos**: `src/test/java/ssellm/models/ResponseMetadata.java`
- **Agregador**: `src/test/java/ssellm/ResponseAggregator.java`
- **Analizador**: `src/test/java/ssellm/ConsistencyAnalyzer.java`
- **Script automatizado**: `scripts/run_quality_analysis.sh`

---

## ✅ Checklist de Validación

Antes de considerar Sprint 1 completo, verificar:

- [ ] El proyecto compila sin errores
- [ ] El load test ejecuta y completa exitosamente
- [ ] Se genera `responses_metadata.jsonl` con datos válidos
- [ ] Se genera `responses_by_prompt.json` correctamente
- [ ] Se genera `consistency_analysis.json` con score global
- [ ] El script automatizado funciona end-to-end
- [ ] Los reportes contienen información útil y accionable

---

**¡Sprint 1 Completado! 🎉**

El sistema ahora captura metadatos enriquecidos y realiza análisis automatizado de consistencia. Listo para Sprint 2: análisis avanzado con LLM.
