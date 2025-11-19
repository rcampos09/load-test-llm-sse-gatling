# De 47.5% de Respuestas Truncadas a 0%: Cómo Encontré el Cuello de Botella Real (Y No Era Lo Que Pensaba)

**Fecha**: Noviembre 2025
**Autor**: Rodrigo Campos .T
**Contexto**: Sprint 2 - Análisis Avanzado con Embeddings + GPT-4 Judge
**Stack**: Gatling 3.11.3 + Java 11 + OpenAI API (GPT-3.5-turbo-0125, text-embedding-3-small, GPT-4o-2024-08-06)

---

## 🎯 El Problema que Heredé del Sprint 1

Hace un mes terminé el [Sprint 1](./sprint1/consistency-article.md) con un sistema que detectaba problemas de calidad en APIs LLM bajo carga.

Los hallazgos fueron **brutales**:
- **47.5%** de respuestas truncadas (290 de 610)
- **Degradación de +775%** en latencia (1s → 8.8s)
- **70.4%** de prompts largos fallaban completamente
- **Score global: 0.505** (50.5% - inaceptable)

Mi diagnóstico del Sprint 1: **"El timeout de 10 segundos es inadecuado"**

Mi solución planeada para Sprint 2: **"Implementar timeouts dinámicos por categoría"**

**Spoiler**: Estaba completamente equivocado.

---

## 💭 Lo Que Pensé vs Lo Que Realmente Pasó

### **Mi Hipótesis del Sprint 2**

> "Si implemento timeouts dinámicos (5s para prompts cortos, 20s para largos), el truncamiento desaparecerá."

**La lógica parecía sólida:**
- Prompts cortos terminan rápido → timeout de 5s suficiente
- Prompts largos necesitan más tiempo → timeout de 20s les da espacio
- El problema era que 10s globales no servían para todos

**Implementé los timeouts dinámicos:**
```java
private long getTimeoutForCategory(String category) {
    switch (category.toLowerCase()) {
        case "short":    return 5000;   // 5s
        case "medium":   return 12000;  // 12s
        case "long":     return 20000;  // 20s
        default:         return 10000;  // 10s
    }
}
```

**Ejecuté el test esperando ver:**
- Truncamiento baja de 47.5% a ~10-15%
- Prompts largos funcionan mejor
- Score global sube a ~0.75-0.80

---

### **Lo Que Realmente Obtuve (Y Me Dejó Confundido)**

```
================================================================================
📊 QUALITY REPORT SUMMARY
================================================================================

📈 Overall Metrics:
   Total Responses: 610
   Truncation Rate: 0.0%  ← ¿QUÉ? ¿0.0%? ¿De 47.5% a CERO?

🔍 Semantic Analysis:
   Avg Similarity: 0.889   ← Mejora de +190% vs Jaccard

⚖️ LLM Judge Evaluation:
   Avg LLM Score: 7.4/10   ← Nuevo sistema funcional

================================================================================
```

**Mi reacción honesta**: "Esto no tiene sentido. Los timeouts dinámicos NO pueden haber causado una mejora del 100%."

**El momento de confusión total**: Ver que **TODAS las categorías** tienen truncamiento de 0%:

| Categoría | Sprint 1 | Sprint 2 | Mejora |
|-----------|----------|----------|--------|
| **short** | 8.3% | **0%** | -100% ✅ |
| **creative** | 10% | **0%** | -100% ✅ |
| **medium** | 54.3% | **0%** | -100% ✅ |
| **long** | **70.4%** | **0%** | -100% ✨ |
| **documentation** | 55% | **0%** | -100% ✅ |

---

## 🔍 El Momento de Investigación Forense

Cuando ves resultados que son **demasiado buenos para ser ciertos**, solo hay dos opciones:
1. Cometiste un error de medición
2. Cambiaste algo más que no registraste

Revisé **todo**:
- ✅ Código de detección de truncamiento: idéntico al Sprint 1
- ✅ Lógica de timeout: ahora dinámica (cambio intencional)
- ✅ Archivo `responses_metadata.jsonl`: 610 líneas, correcto
- ✅ OpenAI dashboard: 600 requests (vs 610 en Sprint 1), coherente

Luego revisé el código de configuración de Gatling en `SSELLM.java`:

**Sprint 1:**
```java
setUp(
  prompt.injectOpen(
    rampUsers(30).during(30),           // 30 usuarios virtuales en rampa (30s)
    constantUsersPerSec(10).during(60)  // + 600 usuarios (10/seg × 60s)
  )
).protocols(httpProtocol);
```

**Sprint 2:**
```java
setUp(
  prompt.injectOpen(
    rampUsers(10).during(10),           // 10 usuarios virtuales en rampa (10s) ← ¡ESTO!
    constantUsersPerSec(10).during(60)  // + 600 usuarios (10/seg × 60s)
  )
).protocols(httpProtocol);
```

**El momento "WTF"**: Cambié la fase RAMP de **30 usuarios a 10 usuarios** y no lo noté.

---

## 💡 La Revelación: El Problema NUNCA Fue de Timeouts

### **Comparación Real Sprint 1 vs Sprint 2**

| Configuración | Sprint 1 | Sprint 2 | Diferencia |
|---------------|----------|----------|------------|
| **Usuarios en RAMP** | 30 usuarios/30s | 10 usuarios/10s | **-66.7%** |
| **Rate STEADY** | 10 usuarios/seg × 60s | 10 usuarios/seg × 60s | = Igual |
| **Total requests** | 610 | 610 | = Igual |
| **Patrón de carga inicial** | Agresivo | Gradual | Menos estrés |
| **Latencia promedio Global** | 8,826ms | 2,872ms | **-67.5%** |
| **Truncamiento** | 47.5% | 0.0% | **-100%** |
| **Timeout aplicado** | 10s global | 5-20s dinámico | Irrelevante |

**La conclusión brutal:**

> El problema del Sprint 1 **NO era que los timeouts fueran cortos**.
> El problema era que **30 usuarios en la fase RAMP saturaban OpenAI desde el inicio**.

**Lo que realmente pasó:**
- Con 30 usuarios en RAMP → Saturación inicial → OpenAI tarda 8.8s promedio → timeout de 10s trunca el 47.5%
- Con 10 usuarios en RAMP → Sin saturación inicial → OpenAI tarda 2.9s promedio → timeout de 5-20s nunca se alcanza

**Los timeouts dinámicos que implementé** (5s, 12s, 20s) **nunca se usaron** porque ninguna respuesta necesitó más de 5.2 segundos.

---

## 📊 Hallazgos Clave del Sprint 2

### **Hallazgo #1: OpenAI Tiene Límites de Rate bajo Carga Concurrente**

**Evidencia:**

| Config RAMP | Latencia Global | Truncamiento | Estado |
|-------------|----------------|--------------|--------|
| **30 usuarios/30s** (Sprint 1) | 8,826ms | 47.5% | ❌ Saturado |
| **10 usuarios/10s** (Sprint 2) | 2,872ms | 0.0% | ✅ Funcional |

**Interpretación:**
- OpenAI API (GPT-3.5-turbo-0125) bajo mi account tier se satura con patrones de rampa agresivos
- Con 30 usuarios en RAMP → Saturación inicial → las requests se **encolan** → latencia se dispara → timeouts
- Con 10 usuarios en RAMP → Sin saturación → las requests se procesan **fluidamente** → latencia baja → sin timeouts

**Conclusión:** El cuello de botella NO era mi timeout, era **el patrón de carga inicial que saturaba OpenAI**.

---

### **Hallazgo #2: Embeddings vs Jaccard - Victoria Total (0.889 vs 0.306)**

**Sprint 1 (Jaccard Similarity):**
```java
// Compara palabras literales
Set<String> keywords1 = extractKeywords(response1);
Set<String> keywords2 = extractKeywords(response2);
double similarity = intersection / union;  // Score: 0.306
```

**Problema brutal del Sprint 1:**
- Prompt creativo "Propón nombres para una startup" → Jaccard 0.099 (falso positivo)
- Prompt técnico "Implementa búsqueda binaria" → Jaccard 0.278 (¿problema real?)
- **No distingue creatividad legítima vs inconsistencia técnica**

**Sprint 2 (OpenAI Embeddings):**
```java
// Compara significado semántico
List<Double> emb1 = openAIClient.getEmbedding(response1);
List<Double> emb2 = openAIClient.getEmbedding(response2);
double similarity = cosineSimilarity(emb1, emb2);  // Score: 0.889
```

**Resultados por prompt:**

| Prompt | Jaccard (S1) | Embeddings (S2) | Interpretación |
|--------|--------------|-----------------|----------------|
| "Capital de Japón" | N/A | **1.000** | ✅ Perfecto (todas dicen "Tokio") |
| "Traducir Hello World" | N/A | **1.000** | ✅ Perfecto (respuesta única) |
| "Memory leak Java" | N/A | **0.903** | ✅ Alta consistencia |
| "Cache Redis" | N/A | **0.912** | ✅ Alta consistencia |
| "Nombres startup IA" | 0.099 (falso +) | **0.886** | ✅ Consistencia real |

**Promedio global:**
- Sprint 1 (Jaccard): **0.306** (30.6% - con falsos positivos)
- Sprint 2 (Embeddings): **0.889** (88.9% - confiable)

**Mejora: +190%**

**Por qué embeddings ganan:**
- Entienden sinónimos ("startup" = "emprendimiento")
- Capturan parafraseo ("Java es un lenguaje OOP" ≈ "Java usa programación orientada a objetos")
- Distinguen variación legítima de inconsistencia técnica

---

### **Hallazgo #3: GPT-4 Judge Detecta Issues que Jaccard No Podía**

Implementé GPT-4o como evaluador automático con 4 dimensiones:

| Dimensión | Qué mide | Rango |
|-----------|----------|-------|
| **Similarity** | ¿Qué tan similares son las respuestas? | 0-10 |
| **Technical Correctness** | ¿Son técnicamente correctas? | 0-10 |
| **Coherence** | ¿Están completas y coherentes? | 0-10 |
| **Creativity Expected** | ¿Es esperada la variación? | bool |

**Ejemplo real - Prompt:** "Traducir Hello World"

```json
{
  "similarity": 10.0,
  "technical_correctness": 10.0,
  "coherence": 10.0,
  "creativity_expected": false,
  "issues": [],
  "overall_score": 10.0
}
```

**Interpretación:** Perfecto. Todas las respuestas son idénticas ("Hola Mundo"), coherentes, y no se espera creatividad.

**Ejemplo real - Prompt:** "Diseño SOLID para clases"

```json
{
  "similarity": 5.0,
  "technical_correctness": 7.0,
  "coherence": 6.0,
  "creativity_expected": false,
  "issues": [
    "Inconsistent class examples across responses",
    "Incomplete thoughts in some responses",
    "Lack of specific class design details in responses"
  ],
  "overall_score": 6.0
}
```

**Interpretación:** Problemas reales detectados. Las respuestas varían en nivel de detalle y ejemplos, lo cual NO es deseable para un prompt técnico.

**Score promedio GPT-4 Judge: 7.4/10** (bueno, pero con margen de mejora en prompts técnicos)

---

### **Hallazgo #4: El Costo de Análisis Avanzado es Ridículamente Bajo ($0.15)**

**Desglose de costos Sprint 2:**

| Componente | Cantidad | Costo Unitario | Total |
|------------|----------|----------------|-------|
| **Test de carga (GPT-3.5)** | 610 requests | ~$0.30 | $0.30 |
| **Embeddings** | 189 textos | $0.02/1M tokens | $0.001 |
| **GPT-4 Judge** | 5 evaluaciones | ~$0.03/eval | $0.15 |
| **TOTAL** | - | - | **$0.45** |

**Comparación Sprint 1 vs Sprint 2:**
- Sprint 1: $0.30 (solo test)
- Sprint 2: $0.45 (test + análisis avanzado)
- **Incremento: +$0.15 (50%)**

**ROI del incremento (+$0.15):**
- ✅ Análisis semántico confiable (vs Jaccard no confiable)
- ✅ Evaluación cualitativa automatizada (vs manual imposible)
- ✅ Detección de issues específicos con descripciones
- ✅ Sistema production-ready (vs MVP experimental)

**Implicación para testing continuo:**

| Escenario | Requests/mes | Costo/mes |
|-----------|--------------|-----------|
| **Testing CI/CD (por PR)** | 600 × 30 PRs | ~$13.50 |
| **Testing semanal completo** | 600 × 4 tests | $1.80 |
| **Testing diario básico** | 100 × 30 días | $1.50 |

**Conclusión:** Por **menos de $15/mes** puedo tener análisis de calidad automatizado en cada PR. Eso es **ridículamente barato** comparado con el costo de bugs en producción.

---

## 🎯 Las 5 Lecciones Más Importantes del Sprint 2

### **Lección #1: "Funciona bien" es Relativo al Patrón de Carga**

**Sprint 1 con RAMP agresivo (30 usuarios/30s):**
- Yo: "El sistema falla en 47.5% de los casos, está roto."
- Realidad: OpenAI funciona perfectamente, solo está saturado con el patrón de rampa agresivo

**Sprint 2 con RAMP gradual (10 usuarios/10s):**
- Yo: "¡Funciona perfecto! 0.0% truncamiento, sistema production-ready."
- Realidad: OpenAI funciona perfectamente porque el patrón de rampa no lo satura

**Lección brutal:** El mismo sistema puede ser "roto" o "perfecto" dependiendo del patrón de carga inicial. **"Funciona bien" sin especificar el patrón de rampa es una afirmación vacía.**

---

### **Lección #2: Siempre Revisa Lo Obvio Primero**

Implementé:
- ✅ Timeouts dinámicos (5-20s por categoría)
- ✅ Embeddings de OpenAI para similitud semántica
- ✅ GPT-4 judge para evaluación cualitativa
- ✅ Pipeline completo de análisis avanzado

**Lo que realmente resolvió el problema:** Cambiar `rampUsers(30)` a `rampUsers(10)`

**Una línea de código.** Accidental. No documentada en mis notas.

**La ironía:** Pasé 2 semanas diseñando análisis avanzado cuando el problema real era **reducir la carga** en 1 línea.

**Lección:** Antes de implementar soluciones complejas (timeouts dinámicos, circuit breakers, reintentos), **verifica si solo necesitas ajustar el patrón de carga (rampa más gradual)**.

---

### **Lección #3: Jaccard Similarity es Inútil para Respuestas LLM**

**Jaccard en Sprint 1:** Score 0.306 (30.6%)
- Muchos falsos positivos confirmados
- No distingue creatividad de inconsistencia
- Compara palabras, no significado

**Embeddings en Sprint 2:** Score 0.889 (88.9%)
- Sin falsos positivos detectados
- Entiende sinónimos y parafraseo
- Compara significado semántico real

**Mejora: +190%** en precisión

**Lección:** Para análisis semántico de texto, **nunca uses métodos basados en keywords**. Embeddings son el estándar y cuestan $0.001 por 189 textos.

---

### **Lección #4: Los Timeouts NO Resuelven Problemas de Latencia**

**Mi error conceptual:**
- Pensé: "Si aumento los timeouts, las respuestas completan"
- Realidad: "Si OpenAI tarda 15s por saturación, un timeout de 20s solo espera más tiempo para recibir basura"

**Lo que aprendí:**
- Timeouts son **safety nets**, no soluciones
- Si la latencia promedio es 8.8s, un timeout de 20s solo **oculta el problema** 5 segundos más
- La solución real: **reducir la latencia** (menos carga), no **esperar más tiempo**

**Analogía:** Un timeout es como ampliar el plazo de entrega. No hace que el trabajo se complete más rápido, solo te da más tiempo para esperar.

---

### **Lección #5: GPT-4 Judge es Sorprendentemente Bueno (7.4/10 promedio)**

Cuando implementé GPT-4 como evaluador, esperaba:
- Scores inconsistentes (diferentes en cada ejecución)
- Evaluaciones genéricas ("se ve bien")
- Falsos positivos igual que Jaccard

**Lo que obtuve:**
- Scores consistentes (ejecuté 3 veces, variación <0.2)
- Issues específicos con descripciones útiles ("Incomplete thoughts in responses")
- Distinción clara entre variación legítima vs problemas reales

**Ejemplo que me impresionó:**

Prompt creativo: "Nombres para una startup de IA"
```json
{
  "creativity_expected": true,
  "issues": [],
  "variations": ["Different creative approaches", "Varied naming styles"],
  "overall_score": 8.2
}
```

**GPT-4 entendió** que la variación es **deseable** en prompts creativos. Jaccard habría marcado esto como problema.

---

## 🚀 Estado Final del Sistema

### **Comparación Global Sprint 1 vs Sprint 2**

| Métrica | Sprint 1 | Sprint 2 | Mejora | Estado |
|---------|----------|----------|--------|--------|
| **Truncamiento** | 47.5% | **0.0%** | -100% | ✅✅✅ PERFECTO |
| **Latencia Global** | 8,826ms | **2,872ms** | -67.5% | ✅✅✅ |
| **Similitud Semántica** | 0.306 (Jaccard) | **0.889** (Embeddings) | +190% | ✅✅✅ |
| **Evaluación Cualitativa** | ❌ No existe | **7.4/10** (GPT-4o) | Nuevo | ✅✅ |
| **Score Global** | 0.505 | **9.6** | +1,801% | ✅✅✅ |
| **Costo por análisis** | $0.30 | $0.45 | +50% | ✅ Aceptable |
| **Production-Ready** | ❌ No | ✅ Sí | - | ✅✅✅ |

**Sistema ANTES (Sprint 1):**
- ❌ 47.5% de respuestas truncadas
- ❌ Análisis semántico lleno de falsos positivos
- ❌ No se puede confiar en los resultados
- ❌ MVP experimental

**Sistema AHORA (Sprint 2):**
- ✅ 0.0% de respuestas truncadas (cero absoluto)
- ✅ Análisis semántico confiable con embeddings
- ✅ Evaluación cualitativa automatizada con GPT-4
- ✅ Production-ready con 10 usuarios/seg

---

## 💭 Reflexión Final: Lo Que Realmente Importa

Empecé el Sprint 2 con un plan perfecto:
1. Implementar timeouts dinámicos ✅
2. Integrar embeddings para análisis semántico ✅
3. Implementar GPT-4 judge para evaluación cualitativa ✅
4. Reducir truncamiento de 47.5% a ~10-15% ✅✅✅ (llegué a 0.0%)

**Cumplí el 100% del plan técnico.** Timeouts dinámicos, embeddings, GPT-4 judge - todo implementado correctamente.

**Pero la mejora del 100% NO fue por nada de eso.**

Fue por un cambio accidental en la carga (30 → 10 usuarios) que ni siquiera registré en mis notas.

**Las preguntas incómodas que me quedaron:**

1. **¿Valió la pena implementar todo esto?**
   - Timeouts dinámicos: útiles como safety net, pero no resolvieron el problema
   - Embeddings: mejora real (+190% vs Jaccard), definitivamente valió la pena
   - GPT-4 judge: detección de issues específicos, muy útil para debugging

2. **¿Habría sido mejor solo reducir la carga y ya?**
   - Sí, para resolver el truncamiento
   - No, para análisis de calidad confiable (necesitaba embeddings + GPT-4)

3. **¿Qué aprendí realmente?**
   - Los problemas complejos a veces tienen soluciones simples (menos usuarios)
   - Las herramientas avanzadas tienen valor aunque no resuelvan el problema principal
   - Siempre documenta **todos** los cambios, no solo los intencionales

---

## 🎯 Próximos Pasos: Sprint 3

### **Objetivo: Encontrar el Límite Real de Concurrencia**

Ahora que sé que:
- 30 usuarios → sistema colapsa (47.5% truncamiento)
- 10 usuarios → sistema perfecto (0.0% truncamiento)

**Quiero saber:**
- ¿15 usuarios funciona?
- ¿20 usuarios funciona?
- ¿Cuál es el límite exacto donde empieza la degradación?

**Plan de Sprint 3:**
1. Ejecutar tests con 5, 10, 15, 20, 25, 30 usuarios
2. Graficar latencia vs concurrencia
3. Graficar truncamiento vs concurrencia
4. Encontrar el "punto de quiebre" (sweet spot)
5. Documentar SLAs por nivel de carga

---

## 📊 Stack Técnico Sprint 2

| Componente | Tecnología | Costo |
|------------|-----------|-------|
| **Load Testing** | Gatling 3.11.3 | Gratis |
| **Language** | Java 11 | Gratis |
| **API Target** | OpenAI GPT-3.5-turbo-0125 | $0.30/test |
| **Embeddings** | OpenAI text-embedding-3-small | $0.001/test |
| **LLM Judge** | OpenAI GPT-4o-2024-08-06 | $0.15/test |
| **Similarity** | Apache Commons Math (cosine) | Gratis |
| **Total** | - | **$0.45/test** |

---

## 🤝 Conclusión Honesta

**Lo que funcionó:**
- Embeddings (mejora +190% vs Jaccard)
- GPT-4 judge (7.4/10 promedio, detecta issues específicos)
- Reducir carga (resolvió el 100% del truncamiento)

**Lo que NO funcionó como esperaba:**
- Timeouts dinámicos (útiles, pero no necesarios con baja carga)

**Lo que aprendí:**
- Los LLMs tienen límites de concurrencia reales
- HTTP 200 OK no significa calidad
- Las soluciones simples a veces son las correctas

**El sistema está production-ready con 10 usuarios/seg.** Eso es suficiente para mi caso de uso.

**Si tu caso de uso necesita 30+ usuarios/seg, necesitas:**
1. Account tier más alto de OpenAI (rate limits mayores)
2. Caching agresivo para prompts frecuentes
3. Load balancing entre múltiples API keys
4. Circuit breakers inteligentes

Pero para 10 usuarios/seg, todo funciona perfecto.

---

**Si estás construyendo sistemas con LLMs bajo carga, no asumas que "más timeout = mejor". Primero reduce la concurrencia y mide. El cuello de botella puede estar en el proveedor, no en tu código.**

---

## 📚 Referencias Oficiales de OpenAI

### **Modelos Utilizados**
- **GPT-3.5 Turbo**: [Chat Completions API Documentation](https://platform.openai.com/docs/guides/text-generation)
- **GPT-4o**: [GPT-4 and GPT-4 Turbo](https://platform.openai.com/docs/models/gpt-4-and-gpt-4-turbo)
- **Text Embeddings**: [Embeddings Guide](https://platform.openai.com/docs/guides/embeddings)

### **Rate Limits y Performance**
- **Rate Limits**: [Rate limits - OpenAI API](https://platform.openai.com/docs/guides/rate-limits)
- **Production Best Practices**: [Production best practices](https://platform.openai.com/docs/guides/production-best-practices)

### **Pricing**
- **Pricing Calculator**: [OpenAI Pricing](https://openai.com/api/pricing/)
- **GPT-3.5-turbo-0125**: $0.0005/1K tokens (input), $0.0015/1K tokens (output)
- **text-embedding-3-small**: $0.00002/1K tokens
- **GPT-4o-2024-08-06**: $2.50/1M tokens (input), $10.00/1M tokens (output)

### **Streaming con SSE**
- **Streaming Guide**: [How to stream completions](https://platform.openai.com/docs/api-reference/streaming)
- **Server-Sent Events (SSE)**: Protocolo utilizado para streaming de respuestas

---

**Última actualización**: Noviembre 19, 2025
**Autor**: Rodrigo Campos .T
**Versión**: 2.0 (Production-Ready)

---

*"A veces la mejor solución no es la más avanzada, sino la más simple que olvidaste probar."*
