# 📚 Documentación del Proyecto: Load Testing LLM con Análisis de Consistencia

Este directorio contiene toda la documentación técnica, análisis, experimentos y publicaciones relacionadas con el proyecto de load testing de APIs LLM con Server-Sent Events (SSE).

---

## 📁 Estructura de Documentación

```
docs/
├── sprint1/              # Sprint 1: MVP de Análisis de Consistencia ✅
├── sprint2/              # Sprint 2: Análisis Avanzado con LLM 📝
├── sprint3/              # Sprint 3: Visualización y Automatización 📝
├── sprint4/              # Sprint 4: Mejoras Avanzadas (Opcional) 📝
└── publications/         # Artículos y posts publicados
```

---

## 🚀 Sprint 1: MVP de Análisis de Consistencia

**Estado**: ✅ Completado (Octubre 2025)

**Objetivo**: Construir un sistema de análisis de consistencia para respuestas LLM bajo carga usando herramientas básicas (Gatling + Java custom).

**Documentos principales:**

- **[README.md](sprint1/README.md)** - Guía completa del Sprint 1
- **[consistency-article.md](sprint1/consistency-article.md)** - Artículo exhaustivo (1,048 líneas) sobre hallazgos y aprendizajes
- **[validation-report.md](sprint1/validation-report.md)** - Reporte de validación técnica

**Experimentos y análisis técnicos:**

- **[experiments/gatling-sse-analysis-en.md](sprint1/experiments/gatling-sse-analysis-en.md)** - Análisis del gap de medición SSE en Gatling (inglés)
- **[experiments/gatling-sse-analysis-es.md](sprint1/experiments/gatling-sse-analysis-es.md)** - Análisis del gap de medición SSE en Gatling (español)

**Resultados clave:**
- ✅ 610 requests testeados
- ✅ 47.5% truncamiento detectado
- ✅ Gap de +403% en medición Gatling vs realidad
- ✅ Score de consistencia: 0.505 (50.5%)
- ✅ Costo: $0.30 por test completo

---

## 🧠 Sprint 2: Análisis Avanzado con LLM

**Estado**: 📝 Planificado
**Duración estimada**: 1 semana (5 días hábiles)

**Objetivo**: Reemplazar Jaccard similarity con análisis semántico real usando LLM-as-a-judge (GPT-4).

**Documentos:**

- **[README.md](sprint2/README.md)** - Plan completo de implementación

**Tareas principales:**
- Tarea 2.2: Integración con LLM para análisis semántico
- Tarea 2.3: Prompt engineering para evaluación
- Tarea 3.1: QualityReportGenerator.java
- Tarea 3.2: Métricas avanzadas (categoría, fase, correlaciones)

**Objetivos:**
- ✅ Similarity score real (LLM-based) en lugar de Jaccard
- ✅ Detección de alucinaciones
- ✅ Distinción entre creatividad legítima vs inconsistencia técnica
- ✅ Reducir falsos positivos de <20% a <10%

**Costo estimado**: $3.50 por test (incluye análisis LLM con 20% sampling)

---

## 📊 Sprint 3: Visualización y Automatización

**Estado**: 📝 Planificado
**Duración estimada**: 3-4 días hábiles

**Objetivo**: Automatizar flujo completo y crear visualizaciones interactivas.

**Documentos:**

- **[README.md](sprint3/README.md)** - Plan completo de implementación

**Tareas principales:**
- Tarea 3.3: Dashboard HTML interactivo (Plotly.js)
- Tarea 4.1: Script automatizado (`run_quality_test.sh`)
- Tarea 4.2: Sistema de umbrales configurables (YAML)

**Entregables:**
- 📊 `target/quality_dashboard.html` - Dashboard visual con 4 gráficos
- 🤖 `run_quality_test.sh` - Un comando para ejecutar todo el flujo
- ⚙️ `thresholds.yaml` - Configuración de SLAs y alertas
- 🚨 Integración con CI/CD (exit codes, alertas automáticas)

**Mejora clave**: De ~15 min manuales a <5 min automatizado

---

## 🚀 Sprint 4: Mejoras Avanzadas (Opcional)

**Estado**: 📝 Planificado
**Duración estimada**: 1-2 semanas (opcional)
**Prioridad**: BAJA

**Objetivo**: Técnicas avanzadas de ML/AI para análisis más profundo.

**Documentos:**

- **[README.md](sprint4/README.md)** - Plan completo de implementación

**Tareas principales:**
- Tarea 5.1: Embeddings vectoriales para semántica (reemplazar Jaccard definitivamente)
- Tarea 5.2: Detección de anomalías con ML (Isolation Forest)
- Tarea 5.3: Comparación multi-modelo (GPT-3.5 vs GPT-4 vs Claude vs Llama)

**Entregables:**
- 🔢 Análisis con embeddings (cosine similarity, clustering)
- 🎯 Detección automática de outliers (ML-based)
- ⚖️ Comparación de calidad entre modelos LLM
- 📈 Análisis predictivo de degradación

**Costo estimado**: $11-14 por test (incluye multi-modelo)

**⚠️ Nota**: Solo implementar si Sprint 2 muestra >20% falsos positivos o si necesitas comparar múltiples modelos.

---

## 📝 Publicaciones

Artículos, posts y contenido publicado en comunidades técnicas.

**Plataformas objetivo:**
- LinkedIn
- Medium
- Dev.to
- Foro de Gatling
- Comunidades LLM/AI

---

## 🎯 Navegación Rápida

### Para entender el proyecto completo:
1. Leer **[Sprint 1 README](sprint1/README.md)** - MVP completado ✅
2. Leer **[Artículo de Consistencia](sprint1/consistency-article.md)** - Hallazgos y lecciones (1,048 líneas)
3. Revisar **[Roadmap Sprint 2-4](sprint2/README.md)** - Próximos pasos 📝

### Para análisis técnico profundo:
1. **[Análisis de Gap SSE en Gatling](sprint1/experiments/gatling-sse-analysis-en.md)** - ¿Por qué +403% diferencia?
2. **[Reporte de Validación Sprint 1](sprint1/validation-report.md)** - Validación técnica completa
3. **[Plan de Análisis LLM (Sprint 2)](sprint2/README.md)** - LLM-as-a-judge

### Para replicar el experimento:
1. Ver código en `/src/test/java/ssellm/`
2. Seguir instrucciones en **[Sprint 1 README](sprint1/README.md)**
3. Para automatización futura: ver **[Sprint 3 README](sprint3/README.md)**

### Para planificar mejoras:
- **Sprint 2**: Análisis LLM avanzado → [Ver plan](sprint2/README.md)
- **Sprint 3**: Dashboard + automatización → [Ver plan](sprint3/README.md)
- **Sprint 4**: ML + multi-modelo (opcional) → [Ver plan](sprint4/README.md)

---

## 📊 Archivos de Datos (Target)

Los resultados de ejecución se encuentran en `/target/`:

```
target/
├── responses_metadata.jsonl       # 610 respuestas con 16 campos de metadata
├── responses_by_prompt.json       # Agrupación por prompt/categoría/fase
├── consistency_analysis.json      # Análisis de 5 dimensiones + score global
├── llm_response.txt              # Respuestas legibles para debugging
└── gatling/                      # Reportes HTML de Gatling
```

---

## 🤝 Contribuciones

Este proyecto es un experimento de investigación aplicada. Si tienes sugerencias o ideas:

- 📝 Abre un issue en el repositorio
- 💡 Propón mejoras en el análisis o roadmap de sprints
- 🧪 Comparte tus propios experimentos de load testing LLM
- 🔧 Contribuye con implementaciones de Sprint 2-4
- 📚 Sugiere mejoras en la documentación

---

## 📚 Referencias Técnicas

### Herramientas Utilizadas (Sprint 1):
- Gatling 3.11.3
- Java 11
- OpenAI API (GPT-3.5-turbo)
- Jackson 2.18 (JSON/JSONL)

### Protocolos:
- HTTP/1.1
- Server-Sent Events (SSE) - RFC 6202
- OpenAI Streaming API

### Metodologías:
- Load Testing con inyección gradual (rampUsers + constantUsersPerSec)
- Análisis multidimensional de consistencia (5 dimensiones)
- Detección automática de truncamiento
- Medición manual end-to-end (timing custom)

---

**Última actualización**: Octubre 2025
**Autor**: Ricardo Campos
**Licencia**: MIT
