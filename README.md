# Cómo Hacer Pruebas de Rendimiento a APIs de LLM: Guía Práctica con Gatling - Rodrigo Campos - Performance Test Engineer 

## ¿Por qué necesitas probar el rendimiento de tu API de LLM?

Si estás integrando ChatGPT, Claude o cualquier LLM en tu aplicación, seguramente te has preguntado: **¿Cuántos usuarios simultáneos puede manejar mi sistema?** ¿Qué pasa cuando 100 personas hacen preguntas al mismo tiempo?

Las APIs de LLM son fundamentalmente diferentes a las APIs REST tradicionales. **No devuelven una respuesta instantánea**, sino que transmiten el texto palabra por palabra durante varios segundos usando **Server-Sent Events (SSE)**.

Esto crea desafíos únicos:
- ⏱️ Latencias extremadamente variables (1-30 segundos)
- 💸 Cada test tiene un costo real
- 📊 Necesitas validar calidad, no solo velocidad
- 🔄 Las respuestas llegan en fragmentos que debes ensamblar correctamente

---

## ¿Qué debes medir en tus pruebas?

### 1. Métricas Tradicionales (Siguen siendo importantes)

**Error Rate (Tasa de Error)**
- ¿Qué es? Porcentaje de requests que fallan
- Objetivo: < 1%
- Errores comunes: `429 Too Many Requests`, `503 Service Unavailable`, timeouts

**Throughput (Capacidad)**
- ¿Qué es? Cuántos requests por segundo puede manejar tu sistema
- Ejemplo: 10 usuarios concurrentes, cada request tarda 5 seg → 2 req/seg

**Response Time Percentiles (p50, p95, p99)**
- ¿Por qué importan? El promedio puede engañar
- Ejemplo: Promedio = 4s (parece bueno), pero p99 = 60s significa que el 1% de usuarios espera 1 minuto

### 2. Métricas Específicas de LLMs (Lo nuevo)

**TTFB (Time To First Byte) - La Métrica de Percepción**
- ¿Qué es? Cuánto tarda en llegar el PRIMER fragmento de respuesta
- Por qué importa: Es lo que el usuario percibe como "velocidad"
- Objetivos:
  - Excelente: < 200ms
  - Bueno: < 500ms
  - Aceptable: < 1000ms
  - Malo: > 1000ms

**Tokens por Segundo - La Métrica de Fluidez**
- ¿Qué es? Velocidad de generación de texto
- Velocidades de referencia:
  - 30 tok/seg = Lento (tipeo lento)
  - 50 tok/seg = Bueno (lectura fluida)
  - 100 tok/seg = Rápido (casi instantáneo)
- Por modelo:
  - GPT-3.5-turbo: 50-100 tok/seg
  - GPT-4: 20-40 tok/seg (más preciso, más lento)

**Completitud de Respuesta - La Métrica de Confiabilidad**
- ¿Qué es? ¿Llegó la respuesta COMPLETA hasta el marcador `[DONE]`?
- Objetivo: 100%
- Causas de respuestas incompletas:
  - Buffer muy pequeño
  - Conexión cerrada prematuramente
  - Timeout del cliente

**Duración Total del Streaming**
- ¿Qué es? Tiempo desde el request hasta que termina el streaming
- Objetivos por categoría de prompt:

| Tipo de Prompt | max_tokens | Duración Esperada |
|----------------|------------|-------------------|
| Corto          | 100-200    | 1-2 seg          |
| Medio          | 500-800    | 5-10 seg         |
| Largo          | 1500-2000  | 20-30 seg        |
| Muy Largo      | 3000-4000  | 40-60 seg        |

**Calidad de Respuesta - La Métrica de Negocio**
- No solo velocidad. También valida:
  - ✓ Coherencia: ¿Tiene sentido?
  - ✓ Relevancia: ¿Responde la pregunta?
  - ✓ Idioma correcto
  - ✓ Formato: ¿Respeta markdown/código si se pidió?
  - ✓ Completitud: ¿Está terminada?

---

## Tabla Resumen: ¿Cuál es la métrica más importante?

| Métrica               | Qué mide              | Objetivo  | Crítico para      |
|-----------------------|-----------------------|-----------|-------------------|
| **Error Rate**        | % requests fallidos   | < 1%      | Disponibilidad    |
| **p95 Response Time** | Latencia percibida    | < 10s     | UX                |
| **TTFB**              | Primera impresión     | < 500ms   | Percepción        |
| **Tokens/segundo**    | Velocidad generación  | > 50      | Fluidez           |
| **Completitud**       | Respuestas completas  | 100%      | Confiabilidad     |
| **Calidad**           | Coherencia del texto  | Manual    | Valor de negocio  |

---

## Implementación Práctica con Gatling

### ¿Por qué Gatling?

- ✅ Soporte nativo para Server-Sent Events (SSE)
- ✅ Reportes visuales automáticos
- ✅ Ideal para integración en CI/CD
- ✅ Comunidad activa y documentación sólida

### Estructura del Proyecto

```
load-test-llm-sse/
├── pom.xml
├── src/test/
│   ├── java/ssellm/SSELLM.java      # Simulación principal
│   └── resources/prompts.csv        # Datos de entrada
└── target/
    ├── gatling/                     # Reportes generados
    └── llm_response.txt             # Respuestas capturadas
```

### Paso 1: Preparar los Prompts

**Archivo:** `src/test/resources/prompts.csv`

```csv
category,prompt,max_tokens,temperature
short,¿Qué es la fotosíntesis?,200,0.7
short,Define inteligencia artificial en una frase,150,0.5
medium,Explica las ventajas de microservicios,500,0.7
long,Crea una API REST en Java con Spring Boot,2000,0.8
```

### Paso 2: Configuración Crítica del Buffer SSE

Uno de los errores más comunes es configurar mal el buffer de mensajes SSE:

```java
HttpProtocolBuilder httpProtocol = http
    .baseUrl("https://api.openai.com/v1/chat")
    .sseUnmatchedInboundMessageBufferSize(1000);  // ⚠️ CRÍTICO
```

**¿Por qué 1000?**
- Una respuesta de 200 tokens puede generar 100-200 mensajes SSE
- Con 5 usuarios concurrentes: 5 × 150 mensajes = 750
- 1000 da margen de seguridad

**Problema si es muy pequeño:**
```
Buffer = 10 mensajes
Llegan 150 mensajes
Resultado: Se pierden 140 mensajes → Respuesta incompleta ❌
```

### Paso 3: El Código Principal

```java
public class SSELLM extends Simulation {
    String api_key = System.getenv("api_key");
    Path rutaRespuesta = Path.of("target/llm_response.txt");
    FeederBuilder<String> promptFeeder = csv("prompts.csv").circular();

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("https://api.openai.com/v1/chat")
        .sseUnmatchedInboundMessageBufferSize(1000);

    ScenarioBuilder prompt = scenario("LLM Load Test")
        .feed(promptFeeder)
        .exec(
            sse("Connect to LLM - #{category}")
                .post("/completions")
                .header("Authorization", "Bearer " + api_key)
                .header("Content-Type", "application/json")
                .body(StringBody(
                    "{\"model\": \"gpt-3.5-turbo\"," +
                    "\"stream\":true," +
                    "\"max_tokens\":#{max_tokens}," +
                    "\"temperature\":#{temperature}," +
                    "\"messages\":[{\"role\":\"user\",\"content\":\"#{prompt}\"}]}"))
                .asJson())
        .asLongAs("#{stop.isUndefined()}").on(
            sse.processUnmatchedMessages((messages, session) -> {
                StringBuilder responseContent = new StringBuilder();

                // Obtener contenido acumulado previo
                if (session.contains("llmResponse")) {
                    responseContent.append(session.getString("llmResponse"));
                }

                // Procesar cada mensaje SSE
                messages.forEach(message -> {
                    String data = message.message();
                    if (data != null && !data.isEmpty() && !data.contains("[DONE]")) {
                        // Extraer contenido del chunk JSON
                        try {
                            JsonObject chunkJson = JsonParser.parseString(data).getAsJsonObject();
                            if (chunkJson.has("data")) {
                                String innerData = chunkJson.get("data").getAsString();
                                JsonObject innerJson = JsonParser.parseString(innerData).getAsJsonObject();

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
                        } catch (Exception e) {
                            System.err.println("Error parsing chunk: " + e.getMessage());
                        }
                    }
                });

                // Detectar fin del streaming
                boolean done = messages.stream()
                    .anyMatch(m -> m.message().contains("[DONE]"));

                Session updatedSession = session.set("llmResponse", responseContent.toString());

                if (done) {
                    // Guardar respuesta completa con metadata
                    String fullResponse = responseContent.toString();
                    String sessionId = updatedSession.userId() + "-" + updatedSession.scenario();
                    String category = updatedSession.getString("category");
                    String prompt = updatedSession.getString("prompt");

                    // Formatear y guardar
                    StringBuilder formattedResponse = new StringBuilder();
                    formattedResponse.append("=".repeat(80)).append("\n");
                    formattedResponse.append("Session ID: ").append(sessionId).append("\n");
                    formattedResponse.append("Category: ").append(category).append("\n");
                    formattedResponse.append("Prompt: ").append(prompt).append("\n");
                    formattedResponse.append("-".repeat(80)).append("\n");
                    formattedResponse.append("Response: ").append(fullResponse).append("\n");
                    formattedResponse.append("=".repeat(80)).append("\n\n");

                    try {
                        Files.writeString(rutaRespuesta, formattedResponse.toString(),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        System.err.println("Error saving response: " + e.getMessage());
                    }

                    return updatedSession.set("stop", true);
                }

                return updatedSession;
            }))
        .exec(sse("close").close());

    {
        setUp(prompt.injectOpen(atOnceUsers(2))).protocols(httpProtocol);
    }
}
```

### Paso 4: Ejecutar las Pruebas

```bash
# Configurar API key
export api_key="sk-tu-clave-de-openai"

# Ejecutar con Maven
./mvnw gatling:test

# Output esperado
[INFO] Simulation ssellm.SSELLM started...
[INFO] Generating reports...
[INFO] Reports generated in: target/gatling/ssellm-20251015123456/index.html
```

### Paso 5: Analizar Resultados

**Reporte de Gatling** (`target/gatling/.../index.html`):
- Response time percentiles (p50, p95, p99)
- Error rate
- Throughput over time
- Gráficas de distribución

**Archivo de Respuestas** (`target/llm_response.txt`):
```
================================================================================
Session ID: 1-Scenario
Category: short
Prompt: ¿Qué es la fotosíntesis?
--------------------------------------------------------------------------------
Response: La fotosíntesis es un proceso químico mediante el cual las plantas...
================================================================================
```

---

## Patrones de Carga Realistas

### Carga Gradual (Ramp-up)
```java
setUp(
    escenario.injectOpen(rampUsers(10).during(30))
).protocols(httpProtocol);
// 10 usuarios inyectados gradualmente en 30 segundos
```

### Carga Constante
```java
setUp(
    escenario.injectOpen(constantUsersPerSec(5).during(60))
).protocols(httpProtocol);
// 5 usuarios/segundo durante 1 minuto = 300 requests
```

### Carga Escalonada (Simular Lanzamiento)
```java
setUp(
    escenario.injectOpen(
        constantUsersPerSec(5).during(120),    // Carga normal: 2 min
        constantUsersPerSec(50).during(60),    // Pico viral: 1 min
        constantUsersPerSec(10).during(120)    // Post-pico: 2 min
    )
).protocols(httpProtocol);
```

---

## Troubleshooting: Problemas Comunes

### ❌ Respuestas vacías o incompletas
**Causa:** Buffer muy pequeño
**Solución:**
```java
.sseUnmatchedInboundMessageBufferSize(1000)  // Aumentar a 1000 o más
```

### ❌ Error 429 Too Many Requests
**Causa:** Rate limiting de OpenAI
**Solución:**
- Reducir usuarios concurrentes
- Aumentar duración del ramp-up
- Verificar límites de tu plan OpenAI

### ❌ Error 401 Unauthorized
**Causa:** API key incorrecta o no configurada
**Solución:**
```bash
export api_key="sk-tu-clave-valida-de-openai"
```

### ❌ Timeouts frecuentes
**Causa:** Prompts muy largos o modelo saturado
**Solución:**
- Aumentar timeout en Gatling
- Reducir max_tokens en prompts
- Probar en horarios de menor carga

---

## Checklist: Antes de ir a Producción

**Performance:**
- [ ] Error rate < 1%
- [ ] p95 response time < 10 segundos
- [ ] TTFB < 500ms para el 95% de requests
- [ ] Tokens/segundo > 30
- [ ] 100% de respuestas completas (sin fragmentos perdidos)

**Calidad:**
- [ ] Respuestas coherentes y relevantes
- [ ] Idioma correcto
- [ ] Formato correcto (markdown, código, etc.)

**Estabilidad:**
- [ ] Throughput sostenido sin caídas durante 5+ minutos
- [ ] Sin errores 429 (rate limiting)
- [ ] Sistema se recupera después de picos de carga

**Costos:**
- [ ] Estimación de costo mensual según volumen esperado
- [ ] Estrategia de caching para reducir llamadas repetidas
- [ ] Monitoreo de uso de tokens

---

## ¿Cuánto Cuesta Ejecutar Estas Pruebas?

**Ejemplo con GPT-3.5-turbo:**
```
100 requests
Promedio: 50 tokens input, 200 tokens output

Costo = (100 × 50 / 1000 × $0.0015) + (100 × 200 / 1000 × $0.002)
      = $0.0075 + $0.04
      = ~$0.048 USD (menos de 5 centavos)
```

**Recomendación:** Empieza con pruebas pequeñas (10-50 requests) para validar tu setup antes de escalar.

---

## Conclusión y Próximos Pasos

Las pruebas de rendimiento para APIs de LLM son diferentes a todo lo que conocías. Necesitas:

1. **Entender SSE y streaming** - No es request-response tradicional
2. **Medir las métricas correctas** - TTFB, tokens/seg, completitud
3. **Configurar el buffer adecuadamente** - Evitar pérdida de datos
4. **Validar calidad, no solo velocidad** - Una respuesta rápida pero incorrecta no sirve

Con Gatling y este enfoque, puedes:
- ✅ Validar que tu sistema soporta la carga esperada
- ✅ Identificar cuellos de botella antes de ir a producción
- ✅ Optimizar costos sin sacrificar experiencia de usuario
- ✅ Tomar decisiones basadas en datos

### Recursos Adicionales

- **Código completo:** [GitHub - load-test-llm-sse](https://github.com/tu-usuario/load-test-llm-sse)
- **Documentación Gatling SSE:** https://docs.gatling.io/reference/script/protocols/sse/
- **OpenAI Streaming:** https://platform.openai.com/docs/api-reference/streaming

---

**¿Te resultó útil esta guía?**

📢 Comparte con tu comunidad de QA y Performance Testing
💬 ¿Qué métricas priorizas tú en tus pruebas de LLM?
🔖 Guarda este post para tu próximo proyecto con IA

---

### Propiedad y Derechos de Autor
Este código es propiedad de Rodrigo Campos (Dontester). Todos los derechos de autor están reservados por Rodrigo Campos.

© Rodrigo Campos (Dontester)
