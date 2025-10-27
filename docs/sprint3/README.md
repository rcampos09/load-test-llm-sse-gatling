# 📊 Sprint 3: Visualización y Automatización

**Estado**: 📝 Planificado
**Duración estimada**: 3-4 días hábiles
**Dependencias**: Sprint 2 completado ✅

---

## 🎯 Objetivos

Sprint 3 se enfoca en **visualización** y **automatización** del proceso de análisis de calidad.

**Principales entregables:**
1. **Dashboard HTML interactivo** con gráficos de calidad
2. **Script automatizado** para ejecutar test + análisis + reporte
3. **Sistema de umbrales configurables** para alertas
4. **Documentación actualizada** con nuevos flujos

---

## 📋 Tareas Sprint 3

### ✅ **Tarea 3.3: Dashboard HTML Interactivo**

**Objetivo**: Generar dashboard visual para explorar resultados del análisis de calidad.

**Implementación:**

```java
public class DashboardGenerator {

    public void generateDashboard(QualityReport report) {
        String htmlContent = buildDashboard(report);
        Files.writeString(
            Paths.get("target/quality_dashboard.html"),
            htmlContent
        );
    }

    private String buildDashboard(QualityReport report) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>LLM Quality Analysis Dashboard</title>
            <script src="https://cdn.plot.ly/plotly-2.26.0.min.js"></script>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                .metric-card {
                    border: 1px solid #ddd;
                    padding: 15px;
                    margin: 10px;
                    border-radius: 8px;
                }
                .critical { border-left: 5px solid #e74c3c; }
                .warning { border-left: 5px solid #f39c12; }
                .success { border-left: 5px solid #27ae60; }
            </style>
        </head>
        <body>
            <h1>🧠 LLM Load Test Quality Report</h1>

            <!-- Global Metrics -->
            <div class="metrics-summary">
                """ + buildGlobalMetrics(report) + """
            </div>

            <!-- Charts -->
            <div id="truncationByCategory"></div>
            <div id="latencyByPhase"></div>
            <div id="qualityByPrompt"></div>
            <div id="timeSeriesQuality"></div>

            <script>
                """ + buildChartScripts(report) + """
            </script>
        </body>
        </html>
        """;
    }
}
```

**Gráficos incluidos:**

1. **Truncation Rate by Category** (bar chart)
   - Comparar short (8.3%) vs medium (53.3%) vs long (70.4%)

2. **Latency Distribution by Phase** (box plot)
   - RAMP (1,009ms) vs STEADY (8,826ms)

3. **Quality Score by Prompt** (horizontal bar chart)
   - Ordenar por similarity_score + technical_correctness

4. **Quality Over Time** (line chart)
   - Mostrar degradación temporal durante el test

**Output esperado:**
- `target/quality_dashboard.html` (archivo standalone)
- Interactivo (zoom, hover tooltips)
- Responsive (mobile-friendly)

**Estimado**: 1.5 días

---

### ✅ **Tarea 4.1: Script Automatizado de Test Completo**

**Objetivo**: Ejecutar todo el flujo con un solo comando.

**Implementación:**

```bash
#!/bin/bash
# run_quality_test.sh

set -e  # Exit on error

echo "🚀 Starting LLM Load Test with Quality Analysis..."

# Step 1: Clean previous results
echo "📁 Cleaning previous results..."
rm -rf target/gatling target/*.json target/*.jsonl target/*.html

# Step 2: Run Gatling load test
echo "⚡ Running Gatling load test..."
./mvnw gatling:test -Dgatling.simulationClass=ssellm.LoadTestSimulation

# Step 3: Parse responses
echo "📊 Parsing responses..."
java -cp target/test-classes ssellm.ResponseParser

# Step 4: Validate responses
echo "✅ Validating response quality..."
java -cp target/test-classes ssellm.ResponseValidator

# Step 5: Run consistency analysis (Sprint 1)
echo "🔍 Running consistency analysis..."
java -cp target/test-classes ssellm.ConsistencyAnalyzer

# Step 6: Run LLM analysis (Sprint 2 - sampling 20%)
echo "🧠 Running LLM semantic analysis (20% sampling)..."
java -cp target/test-classes ssellm.LLMAnalyzer --sample-rate=0.2

# Step 7: Generate quality report
echo "📈 Generating quality report..."
java -cp target/test-classes ssellm.QualityReportGenerator

# Step 8: Generate dashboard
echo "🎨 Generating HTML dashboard..."
java -cp target/test-classes ssellm.DashboardGenerator

# Step 9: Check thresholds
echo "🚨 Checking quality thresholds..."
java -cp target/test-classes ssellm.ThresholdChecker

echo ""
echo "✅ Test completed successfully!"
echo "📊 Dashboard: target/quality_dashboard.html"
echo "📄 Report: target/quality_report.json"
echo ""
```

**Características:**
- Exit on error (detiene si algo falla)
- Logging claro de cada paso
- Tiempo estimado de ejecución: ~3 minutos (sin LLM) / ~8 minutos (con LLM sampling)

**Estimado**: 0.5 días

---

### ✅ **Tarea 4.2: Sistema de Umbrales Configurables**

**Objetivo**: Definir SLAs y generar alertas automáticas si se violan.

**Implementación:**

```java
public class ThresholdChecker {

    public static class Thresholds {
        // Availability
        double minAvailability = 0.95;  // 95% requests must succeed

        // Quality
        double minGlobalConsistency = 0.70;  // 70% overall quality
        double maxTruncationRate = 0.20;     // Max 20% truncated

        // Semantic (LLM)
        double minSemanticSimilarity = 0.75;  // 75% similarity
        double minTechnicalCorrectness = 8.0; // 8/10 score

        // Latency
        double maxP95Latency = 10000;  // 10s P95
        double maxP99Latency = 15000;  // 15s P99

        // By category
        Map<String, Double> maxTruncationByCategory = Map.of(
            "short", 0.10,   // Max 10% for short prompts
            "medium", 0.30,  // Max 30% for medium
            "long", 0.50     // Max 50% for long
        );
    }

    public ThresholdReport check(QualityReport report, Thresholds thresholds) {
        List<Violation> violations = new ArrayList<>();

        // Check availability
        double availability = (double) report.getSuccessfulRequests() / report.getTotalRequests();
        if (availability < thresholds.minAvailability) {
            violations.add(new Violation(
                "CRITICAL",
                "Availability",
                String.format("%.2f%% < %.2f%%", availability * 100, thresholds.minAvailability * 100)
            ));
        }

        // Check truncation rate
        if (report.getTruncationRate() > thresholds.maxTruncationRate) {
            violations.add(new Violation(
                "CRITICAL",
                "Truncation Rate",
                String.format("%.2f%% > %.2f%%", report.getTruncationRate() * 100, thresholds.maxTruncationRate * 100)
            ));
        }

        // Check semantic similarity (LLM)
        if (report.getAvgSimilarity() < thresholds.minSemanticSimilarity) {
            violations.add(new Violation(
                "WARNING",
                "Semantic Similarity",
                String.format("%.2f < %.2f", report.getAvgSimilarity(), thresholds.minSemanticSimilarity)
            ));
        }

        // Check by category
        for (Map.Entry<String, CategoryStats> entry : report.getCategoryStats().entrySet()) {
            String category = entry.getKey();
            double truncationRate = entry.getValue().getTruncationRate();
            double threshold = thresholds.maxTruncationByCategory.get(category);

            if (truncationRate > threshold) {
                violations.add(new Violation(
                    "WARNING",
                    "Category: " + category,
                    String.format("Truncation %.2f%% > %.2f%%", truncationRate * 100, threshold * 100)
                ));
            }
        }

        return new ThresholdReport(violations);
    }
}
```

**Configuración (thresholds.yaml):**

```yaml
thresholds:
  availability:
    min: 0.95  # 95% success rate

  quality:
    global_consistency_min: 0.70  # 70%
    truncation_rate_max: 0.20     # 20%

  semantic:
    similarity_min: 0.75           # 75%
    technical_correctness_min: 8.0 # 8/10

  latency:
    p95_max_ms: 10000  # 10s
    p99_max_ms: 15000  # 15s

  by_category:
    short:
      truncation_max: 0.10  # 10%
    medium:
      truncation_max: 0.30  # 30%
    long:
      truncation_max: 0.50  # 50%
```

**Output de ejemplo:**

```json
{
  "timestamp": "2025-10-26T10:30:00Z",
  "status": "FAILED",
  "violations": [
    {
      "severity": "CRITICAL",
      "metric": "Truncation Rate",
      "message": "47.50% > 20.00%",
      "actual_value": 0.475,
      "threshold": 0.20
    },
    {
      "severity": "WARNING",
      "metric": "Category: long",
      "message": "Truncation 70.37% > 50.00%",
      "actual_value": 0.7037,
      "threshold": 0.50
    }
  ],
  "exit_code": 1
}
```

**Integración con CI/CD:**
```bash
# En GitHub Actions / Jenkins
./run_quality_test.sh
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
    echo "❌ Quality thresholds violated!"
    # Send Slack notification
    # Fail the build
    exit 1
fi
```

**Estimado**: 1 día

---

### ✅ **Tarea Sprint 3: Actualizar Documentación**

**Objetivo**: Documentar nuevos flujos automatizados.

**Documentos a actualizar:**

1. **README.md principal**
   - Agregar sección "Quick Start" con `./run_quality_test.sh`
   - Actualizar arquitectura del sistema

2. **docs/sprint3/validation-report.md**
   - Documentar validación del dashboard
   - Screenshots de gráficos
   - Análisis de usabilidad

3. **docs/sprint3/thresholds-guide.md**
   - Guía de configuración de umbrales
   - Ejemplos de diferentes escenarios (dev, staging, prod)
   - Recomendaciones de SLAs por tipo de sistema

**Estimado**: 1 día

---

## 📊 Métricas de Éxito Sprint 3

| Métrica | Sprint 2 (Manual) | Sprint 3 (Objetivo) |
|---------|-------------------|---------------------|
| **Tiempo de análisis completo** | ~15 min manual | <5 min automatizado |
| **Pasos manuales requeridos** | 7-8 comandos | 1 script |
| **Interpretación de resultados** | Leer JSON raw | Dashboard visual |
| **Detección de violaciones** | Manual | Automática + alertas |
| **Configurabilidad de umbrales** | Hardcoded | YAML configurable |

---

## 🔄 Plan de Implementación (3-4 días)

### **Día 1: Dashboard HTML**
- [ ] Implementar `DashboardGenerator.java`
- [ ] Diseño de layout HTML/CSS
- [ ] Integración con Plotly.js
- [ ] 4 gráficos principales
- [ ] Testing con datos reales de Sprint 1

### **Día 2: Script Automatizado**
- [ ] Crear `run_quality_test.sh`
- [ ] Integrar todos los pasos del flujo
- [ ] Manejo de errores y logging
- [ ] Testing end-to-end

### **Día 3: Sistema de Umbrales**
- [ ] Implementar `ThresholdChecker.java`
- [ ] Parser de `thresholds.yaml`
- [ ] Lógica de validación por dimensión
- [ ] Output JSON de violaciones
- [ ] Exit codes para CI/CD

### **Día 4: Documentación y Validación**
- [ ] Actualizar README.md
- [ ] Crear `validation-report.md`
- [ ] Crear `thresholds-guide.md`
- [ ] Screenshots del dashboard
- [ ] Testing final del flujo completo

---

## 🎯 Entregables Sprint 3

1. **Código:**
   - `src/test/java/ssellm/DashboardGenerator.java`
   - `src/test/java/ssellm/ThresholdChecker.java`
   - `run_quality_test.sh`
   - `thresholds.yaml`

2. **Outputs:**
   - `target/quality_dashboard.html`
   - `target/threshold_report.json`

3. **Documentación:**
   - `docs/sprint3/validation-report.md`
   - `docs/sprint3/thresholds-guide.md`
   - README.md actualizado

---

## 🚨 Riesgos y Mitigaciones

### Riesgo 1: Dashboard no renderiza bien en todos los navegadores
**Impacto**: Usuarios no pueden ver gráficos
**Mitigación**: Testing en Chrome, Firefox, Safari; usar Plotly.js (ampliamente compatible)

### Riesgo 2: Script automatizado falla por dependencias no instaladas
**Impacto**: Flujo no se puede ejecutar
**Mitigación**: Validar dependencias al inicio del script, mensajes claros de error

### Riesgo 3: Umbrales muy estrictos generan falsos positivos
**Impacto**: CI/CD falla innecesariamente
**Mitigación**: Calibrar umbrales con datos reales, permitir configuración por entorno (dev/staging/prod)

---

## 💡 Mejoras Futuras (Post-Sprint 3)

- **Alertas automáticas**: Slack/Email cuando se violan umbrales
- **Dashboard en tiempo real**: WebSocket para actualizar durante el test
- **Comparación histórica**: Guardar resultados y comparar con ejecuciones previas
- **Export a CSV/Excel**: Para análisis offline

---

**Estado**: Planificado | **Owner**: Ricardo Campos | **Última actualización**: Octubre 2025
