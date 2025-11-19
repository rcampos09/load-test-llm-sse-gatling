package ssellm.analyzers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ssellm.models.LLMJudgeEvaluation;
import ssellm.models.QualityReport;
import ssellm.models.ResponseMetadata;
import ssellm.models.SemanticAnalysisResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates comprehensive quality report for Sprint 2.
 * Combines semantic analysis, LLM-as-judge, and basic metrics.
 */
public class QualityReportGenerator {

    private final SemanticAnalyzer semanticAnalyzer;
    private final LLMJudge llmJudge;
    private final ObjectMapper objectMapper;

    // Configuration
    private static final boolean ENABLE_SEMANTIC_ANALYSIS = true;
    private static final boolean ENABLE_LLM_JUDGE = true;
    private static final int MIN_RESPONSES_FOR_ANALYSIS = 2;
    private static final double SAMPLING_RATE = 0.30; // 30% of prompts

    public QualityReportGenerator(String apiKey) {
        this.semanticAnalyzer = new SemanticAnalyzer(apiKey);
        this.llmJudge = new LLMJudge(apiKey);
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Generate comprehensive quality report from metadata file
     *
     * @param metadataFile Path to responses_metadata.jsonl
     * @param outputFile   Path to output JSON report
     * @return QualityReport object
     */
    public QualityReport generateReport(String metadataFile, String outputFile) throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 SPRINT 2 - QUALITY REPORT GENERATOR");
        System.out.println("=".repeat(80));

        // Step 1: Load and parse metadata
        System.out.println("\n[1/6] 📂 Loading metadata file...");
        List<ResponseMetadata> allResponses = loadMetadata(metadataFile);
        System.out.println("   ✓ Loaded " + allResponses.size() + " responses");

        // Step 2: Calculate basic metrics
        System.out.println("\n[2/6] 📈 Calculating basic metrics...");
        QualityReport.Summary summary = calculateSummary(allResponses);
        System.out.println("   ✓ Total responses: " + allResponses.size());
        System.out.println("   ✓ Truncated: " + (int)(summary.truncationRate * allResponses.size() / 100.0) +
            " (" + String.format("%.1f%%", summary.truncationRate) + ")");

        // Step 3: Group by prompt and category
        System.out.println("\n[3/6] 🗂️ Grouping responses by prompt...");
        Map<String, List<ResponseMetadata>> byPrompt = groupByPrompt(allResponses);
        Map<String, List<ResponseMetadata>> byCategoryMap = groupByCategory(allResponses);
        System.out.println("   ✓ " + byPrompt.size() + " unique prompts");
        System.out.println("   ✓ " + byCategoryMap.size() + " categories");

        // Step 4: Semantic analysis (with sampling)
        List<QualityReport.PromptQualityScore> promptScores = new ArrayList<>();
        if (ENABLE_SEMANTIC_ANALYSIS) {
            System.out.println("\n[4/6] 🔍 Running semantic analysis...");
            promptScores = runSemanticAnalysis(byPrompt);
        } else {
            System.out.println("\n[4/6] ⏭️ Semantic analysis DISABLED (skipping)");
        }

        // Step 5: LLM-as-judge evaluation (with sampling)
        if (ENABLE_LLM_JUDGE) {
            System.out.println("\n[5/6] ⚖️ Running LLM-as-judge evaluation...");
            runLLMJudgeEvaluation(byPrompt, promptScores);
        } else {
            System.out.println("\n[5/6] ⏭️ LLM Judge DISABLED (skipping)");
        }

        // Step 6: Category and phase analysis
        System.out.println("\n[6/6] 📊 Analyzing by category and phase...");
        Map<String, QualityReport.CategoryStats> categoryStats = calculateCategoryStats(byCategoryMap);
        QualityReport.PhaseComparison phaseComparison = calculatePhaseComparison(allResponses);

        // Build final report
        QualityReport report = new QualityReport();
        report.setTimestamp(Instant.now());
        report.setTotalRequests(allResponses.size());
        report.setSummary(summary);
        report.setByPrompt(promptScores);
        report.setByCategory(categoryStats);
        report.setByPhase(phaseComparison);

        // Save to file
        System.out.println("\n💾 Saving report to: " + outputFile);
        objectMapper.writeValue(new File(outputFile), report);
        System.out.println("   ✓ Report saved successfully");

        // Print summary
        printReportSummary(report);

        return report;
    }

    /**
     * Load metadata from JSONL file
     */
    private List<ResponseMetadata> loadMetadata(String filePath) throws IOException {
        List<ResponseMetadata> responses = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    ResponseMetadata metadata = objectMapper.readValue(line, ResponseMetadata.class);
                    responses.add(metadata);
                }
            }
        }

        return responses;
    }

    /**
     * Calculate summary metrics
     */
    private QualityReport.Summary calculateSummary(List<ResponseMetadata> responses) {
        int totalResponses = responses.size();
        long truncatedCount = responses.stream().filter(ResponseMetadata::isTruncated).count();
        double truncatedPercentage = (truncatedCount * 100.0) / totalResponses;

        QualityReport.Summary summary = new QualityReport.Summary();
        summary.truncationRate = truncatedPercentage;
        summary.avgSimilarityJaccard = 0.0; // Will be calculated if needed
        summary.avgSimilarityEmbeddings = 0.0; // Will be calculated from semantic analysis
        summary.avgLLMJudgeScore = 0.0; // Will be calculated from LLM judge
        summary.falsePositiveRate = 0.0; // Will be calculated if comparing with Sprint 1

        return summary;
    }

    /**
     * Group responses by prompt
     */
    private Map<String, List<ResponseMetadata>> groupByPrompt(List<ResponseMetadata> responses) {
        return responses.stream()
            .collect(Collectors.groupingBy(ResponseMetadata::getPrompt));
    }

    /**
     * Group responses by category
     */
    private Map<String, List<ResponseMetadata>> groupByCategory(List<ResponseMetadata> responses) {
        return responses.stream()
            .collect(Collectors.groupingBy(ResponseMetadata::getCategory));
    }

    /**
     * Run semantic analysis on sampled prompts
     */
    private List<QualityReport.PromptQualityScore> runSemanticAnalysis(
        Map<String, List<ResponseMetadata>> byPrompt) {

        List<QualityReport.PromptQualityScore> scores = new ArrayList<>();

        // Sample prompts for analysis
        List<String> sampledPrompts = samplePrompts(new ArrayList<>(byPrompt.keySet()));
        System.out.println("   📊 Analyzing " + sampledPrompts.size() + " prompts (sampled at " +
            String.format("%.0f%%", SAMPLING_RATE * 100) + ")");

        int processed = 0;
        for (String prompt : sampledPrompts) {
            List<ResponseMetadata> responses = byPrompt.get(prompt);

            // Skip if not enough responses
            if (responses.size() < MIN_RESPONSES_FOR_ANALYSIS) {
                continue;
            }

            String category = responses.get(0).getCategory();

            try {
                // Run semantic analysis
                SemanticAnalysisResult semanticResult = semanticAnalyzer.analyzeSimilarity(prompt, responses);

                // Calculate truncation rate for this prompt
                long truncated = responses.stream().filter(ResponseMetadata::isTruncated).count();
                double truncationRate = (truncated * 100.0) / responses.size();

                // Calculate avg response time
                double avgResponseTime = responses.stream()
                    .mapToLong(ResponseMetadata::getResponseTimeMs)
                    .average()
                    .orElse(0.0);

                // Create score entry
                QualityReport.PromptQualityScore score = new QualityReport.PromptQualityScore();
                score.prompt = prompt;
                score.category = category;
                score.responsesCount = responses.size();
                score.truncationRate = truncationRate;
                score.avgResponseTime = avgResponseTime;
                score.similarityJaccard = 0.0; // Not calculated in Sprint 2
                score.similarityEmbeddings = semanticResult.getAvgSimilarity();
                score.llmJudgeScore = null; // Will be filled by LLM judge if selected
                score.issues = new ArrayList<>(semanticResult.getIssues());

                scores.add(score);

                processed++;
                if (processed % 5 == 0) {
                    System.out.println("   ⏳ Progress: " + processed + "/" + sampledPrompts.size() +
                        " prompts analyzed");
                }

            } catch (Exception e) {
                System.err.println("   ⚠️ Error analyzing prompt: " + prompt.substring(0, Math.min(40, prompt.length())));
                System.err.println("      " + e.getMessage());
            }
        }

        System.out.println("   ✅ Semantic analysis complete: " + scores.size() + " prompts analyzed");
        return scores;
    }

    /**
     * Run LLM judge evaluation on sampled prompts
     */
    private void runLLMJudgeEvaluation(Map<String, List<ResponseMetadata>> byPrompt,
                                      List<QualityReport.PromptQualityScore> promptScores) {

        // Only evaluate prompts that were semantically analyzed
        if (promptScores.isEmpty()) {
            System.out.println("   ⏭️ No prompts to evaluate (semantic analysis empty)");
            return;
        }

        // Further sample for LLM judge (more expensive)
        List<QualityReport.PromptQualityScore> sampledScores = promptScores.stream()
            .limit(Math.max(5, (int) (promptScores.size() * 0.3)))  // At least 5, max 30%
            .collect(Collectors.toList());

        System.out.println("   ⚖️ Evaluating " + sampledScores.size() + " prompts with GPT-4");

        int processed = 0;
        for (QualityReport.PromptQualityScore score : sampledScores) {
            List<ResponseMetadata> responses = byPrompt.get(score.prompt);

            try {
                // Run LLM judge
                LLMJudgeEvaluation judgeResult = llmJudge.evaluateResponses(
                    score.prompt,
                    score.category,
                    responses
                );

                // Update score with LLM judge metrics (use overall score)
                score.llmJudgeScore = judgeResult.getOverallScore();

                // Add LLM issues to existing issues
                if (score.issues == null) {
                    score.issues = new ArrayList<>();
                }
                score.issues.addAll(judgeResult.getIssuesDetected());

                processed++;
                System.out.println("   ⏳ Progress: " + processed + "/" + sampledScores.size() +
                    " prompts evaluated");

            } catch (Exception e) {
                System.err.println("   ⚠️ Error evaluating with LLM judge: " +
                    score.prompt.substring(0, Math.min(40, score.prompt.length())));
                System.err.println("      " + e.getMessage());
            }
        }

        System.out.println("   ✅ LLM judge evaluation complete: " + processed + " prompts evaluated");
    }

    /**
     * Calculate statistics by category
     */
    private Map<String, QualityReport.CategoryStats> calculateCategoryStats(
        Map<String, List<ResponseMetadata>> byCategoryMap) {

        Map<String, QualityReport.CategoryStats> stats = new HashMap<>();

        for (Map.Entry<String, List<ResponseMetadata>> entry : byCategoryMap.entrySet()) {
            String category = entry.getKey();
            List<ResponseMetadata> responses = entry.getValue();

            int totalCount = responses.size();
            long truncatedCount = responses.stream().filter(ResponseMetadata::isTruncated).count();
            double truncatedPercentage = (truncatedCount * 100.0) / totalCount;

            double avgLatency = responses.stream()
                .mapToLong(ResponseMetadata::getResponseTimeMs)
                .average()
                .orElse(0.0);

            QualityReport.CategoryStats categoryStat = new QualityReport.CategoryStats();
            categoryStat.responseCount = totalCount;
            categoryStat.truncationRate = truncatedPercentage;
            categoryStat.avgResponseTime = avgLatency;
            categoryStat.avgSimilarity = 0.0; // Will be calculated from prompt scores if needed
            categoryStat.score = calculateCategoryScore(truncatedPercentage, avgLatency);

            stats.put(category, categoryStat);
        }

        return stats;
    }

    /**
     * Calculate overall score for a category
     */
    private double calculateCategoryScore(double truncationRate, double avgLatency) {
        // Simple scoring: penalize truncation and high latency
        double truncationScore = (100.0 - truncationRate) / 100.0; // 0-1
        double latencyScore = Math.max(0, 1.0 - (avgLatency / 20000.0)); // 0-1 (20s = 0)

        return (truncationScore * 0.7 + latencyScore * 0.3) * 10; // 0-10 scale
    }

    /**
     * Calculate phase comparison (RAMP vs STEADY)
     */
    private QualityReport.PhaseComparison calculatePhaseComparison(List<ResponseMetadata> responses) {
        Map<String, List<ResponseMetadata>> byPhase = responses.stream()
            .collect(Collectors.groupingBy(ResponseMetadata::getTestPhase));

        List<ResponseMetadata> rampResponses = byPhase.getOrDefault("RAMP", new ArrayList<>());
        List<ResponseMetadata> steadyResponses = byPhase.getOrDefault("STEADY", new ArrayList<>());

        QualityReport.PhaseStats rampStats = calculatePhaseStats(rampResponses);
        QualityReport.PhaseStats steadyStats = calculatePhaseStats(steadyResponses);

        double degradation = steadyStats.avgResponseTime > 0 ?
            ((steadyStats.avgResponseTime - rampStats.avgResponseTime) / rampStats.avgResponseTime) * 100 : 0;

        QualityReport.PhaseComparison comparison = new QualityReport.PhaseComparison();
        comparison.ramp = rampStats;
        comparison.steady = steadyStats;
        comparison.degradationMagnitude = degradation;

        return comparison;
    }

    /**
     * Calculate stats for a single phase
     */
    private QualityReport.PhaseStats calculatePhaseStats(List<ResponseMetadata> responses) {
        QualityReport.PhaseStats stats = new QualityReport.PhaseStats();

        if (responses.isEmpty()) {
            stats.responseCount = 0;
            stats.avgResponseTime = 0.0;
            stats.truncationRate = 0.0;
            stats.avgSimilarity = 0.0;
            return stats;
        }

        stats.responseCount = responses.size();
        stats.avgResponseTime = responses.stream()
            .mapToLong(ResponseMetadata::getResponseTimeMs)
            .average()
            .orElse(0.0);

        long truncated = responses.stream().filter(ResponseMetadata::isTruncated).count();
        stats.truncationRate = (truncated * 100.0) / responses.size();
        stats.avgSimilarity = 0.0; // Not calculated per phase

        return stats;
    }

    /**
     * Sample prompts for analysis
     */
    private List<String> samplePrompts(List<String> allPrompts) {
        if (SAMPLING_RATE >= 1.0) {
            return allPrompts;
        }

        int sampleSize = Math.max(5, (int) (allPrompts.size() * SAMPLING_RATE));
        Collections.shuffle(allPrompts);

        return allPrompts.stream()
            .limit(sampleSize)
            .collect(Collectors.toList());
    }

    /**
     * Print report summary to console
     */
    private void printReportSummary(QualityReport report) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 QUALITY REPORT SUMMARY");
        System.out.println("=".repeat(80));

        System.out.println("\n📈 Overall Metrics:");
        System.out.println("   Total Responses: " + report.getTotalRequests());
        System.out.println("   Truncation Rate: " + String.format("%.1f%%", report.getSummary().truncationRate));

        System.out.println("\n🔍 Semantic Analysis:");
        System.out.println("   Prompts Analyzed: " + report.getByPrompt().size());

        if (!report.getByPrompt().isEmpty()) {
            double avgSimilarity = report.getByPrompt().stream()
                .mapToDouble(s -> s.similarityEmbeddings)
                .average()
                .orElse(0.0);
            System.out.println("   Avg Similarity: " + String.format("%.3f", avgSimilarity));
        }

        long llmEvaluatedCount = report.getByPrompt().stream()
            .filter(s -> s.llmJudgeScore != null)
            .count();

        if (llmEvaluatedCount > 0) {
            System.out.println("\n⚖️ LLM Judge Evaluation:");
            System.out.println("   Prompts Evaluated: " + llmEvaluatedCount);

            double avgLlmScore = report.getByPrompt().stream()
                .filter(s -> s.llmJudgeScore != null)
                .mapToDouble(s -> s.llmJudgeScore)
                .average()
                .orElse(0.0);

            System.out.println("   Avg LLM Score: " + String.format("%.1f/10", avgLlmScore));
        }

        System.out.println("\n" + "=".repeat(80));
    }

    /**
     * Main method for standalone execution
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java QualityReportGenerator <metadata_file> <output_file> [api_key]");
            System.err.println("Example: java QualityReportGenerator responses_metadata.jsonl quality_report.json");
            System.exit(1);
        }

        String metadataFile = args[0];
        String outputFile = args[1];
        String apiKey = args.length > 2 ? args[2] : System.getenv("api_key");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("❌ Error: API key not found. Set 'api_key' environment variable or pass as argument.");
            System.exit(1);
        }

        try {
            QualityReportGenerator generator = new QualityReportGenerator(apiKey);
            generator.generateReport(metadataFile, outputFile);
            System.out.println("\n✅ Quality report generated successfully!");

        } catch (Exception e) {
            System.err.println("❌ Error generating report: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
