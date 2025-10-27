package ssellm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ssellm.models.ResponseMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes consistency of LLM responses without requiring external LLM calls.
 *
 * This basic analyzer uses heuristic-based methods to evaluate:
 * - Semantic consistency (keyword overlap, length variation)
 * - Structural consistency (format, language detection)
 * - Completeness (truncation detection)
 * - Temporal patterns (quality degradation over time)
 * - Category-specific patterns
 */
public class ConsistencyAnalyzer {

    private final ResponseAggregator aggregator;
    private final ObjectMapper objectMapper;

    public ConsistencyAnalyzer(Path metadataFile) {
        this.aggregator = new ResponseAggregator(metadataFile);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Performs complete consistency analysis on all responses.
     *
     * @return Map containing the full analysis report
     * @throws IOException if file operations fail
     */
    public Map<String, Object> analyze() throws IOException {
        System.out.println("\n🔍 Starting Consistency Analysis...\n");

        Map<String, Object> report = new HashMap<>();
        report.put("analysis_timestamp", new Date().toString());

        // Load grouped responses
        Map<String, List<ResponseMetadata>> byPrompt = aggregator.groupByPrompt();
        Map<String, List<ResponseMetadata>> byCategory = aggregator.groupByCategory();
        Map<String, List<ResponseMetadata>> byPhase = aggregator.groupByTestPhase();

        // Overall statistics
        List<ResponseMetadata> allResponses = aggregator.readAllResponses();
        report.put("total_responses", allResponses.size());
        report.put("unique_prompts", byPrompt.size());

        // Analyze each dimension
        report.put("completeness_analysis", analyzeCompleteness(allResponses));
        report.put("structural_analysis", analyzeStructuralConsistency(byPrompt));
        report.put("semantic_analysis", analyzeSemanticConsistency(byPrompt));
        report.put("temporal_analysis", analyzeTemporalPatterns(byPhase));
        report.put("category_analysis", analyzeCategoryImpact(byCategory));

        // Calculate global consistency score
        double globalScore = calculateGlobalScore(report);
        report.put("global_consistency_score", globalScore);

        // Generate summary
        report.put("summary", generateSummary(report));

        System.out.println("\n✅ Consistency analysis completed!\n");
        return report;
    }

    /**
     * Analyzes completeness of responses (truncation detection).
     */
    private Map<String, Object> analyzeCompleteness(List<ResponseMetadata> responses) {
        System.out.println("📊 Analyzing completeness...");

        Map<String, Object> analysis = new HashMap<>();

        long truncatedCount = responses.stream()
                .filter(ResponseMetadata::isTruncated)
                .count();

        double completenessScore = responses.isEmpty() ? 1.0
                : 1.0 - ((double) truncatedCount / responses.size());

        Map<String, Long> reasonBreakdown = responses.stream()
                .filter(ResponseMetadata::isTruncated)
                .collect(Collectors.groupingBy(
                        ResponseMetadata::getTruncationReason,
                        Collectors.counting()
                ));

        List<Map<String, Object>> issues = new ArrayList<>();
        if (truncatedCount > 0) {
            Map<String, Object> issue = new HashMap<>();
            issue.put("description", truncatedCount + " responses truncated");
            issue.put("severity", truncatedCount > responses.size() * 0.1 ? "high" : "medium");
            issue.put("affected_count", truncatedCount);
            issue.put("reasons", reasonBreakdown);
            issues.add(issue);
        }

        analysis.put("score", completenessScore);
        analysis.put("truncated_count", truncatedCount);
        analysis.put("truncation_rate", (double) truncatedCount / responses.size());
        analysis.put("issues", issues);

        System.out.println("  ✓ Completeness score: " + String.format("%.3f", completenessScore));
        return analysis;
    }

    /**
     * Analyzes structural consistency (format, length, language).
     */
    private Map<String, Object> analyzeStructuralConsistency(Map<String, List<ResponseMetadata>> byPrompt) {
        System.out.println("📊 Analyzing structural consistency...");

        List<Map<String, Object>> issues = new ArrayList<>();
        double totalScore = 0.0;
        int promptCount = 0;

        for (Map.Entry<String, List<ResponseMetadata>> entry : byPrompt.entrySet()) {
            String prompt = entry.getKey();
            List<ResponseMetadata> responses = entry.getValue();

            if (responses.size() < 2) continue;
            promptCount++;

            double promptScore = 1.0;

            // Analyze length variation
            IntSummaryStatistics lengthStats = responses.stream()
                    .mapToInt(ResponseMetadata::getResponseLength)
                    .summaryStatistics();

            double avgLength = lengthStats.getAverage();
            double maxLength = lengthStats.getMax();
            double minLength = lengthStats.getMin();

            double lengthVariation = avgLength > 0 ? (maxLength - minLength) / avgLength : 0;

            if (lengthVariation > 0.5) { // More than 50% variation
                promptScore -= 0.1;
                Map<String, Object> issue = new HashMap<>();
                issue.put("prompt", truncate(prompt, 60));
                issue.put("description", "High length variation: " + String.format("%.1f%%", lengthVariation * 100));
                issue.put("severity", lengthVariation > 0.8 ? "high" : "medium");
                issue.put("min_length", minLength);
                issue.put("max_length", maxLength);
                issue.put("avg_length", avgLength);
                issues.add(issue);
            }

            // Detect format issues (Markdown, code blocks)
            long markdownCount = responses.stream()
                    .filter(r -> containsMarkdown(r.getResponse()))
                    .count();

            if (markdownCount > 0 && markdownCount < responses.size()) {
                promptScore -= 0.15;
                Map<String, Object> issue = new HashMap<>();
                issue.put("prompt", truncate(prompt, 60));
                issue.put("description", "Inconsistent Markdown formatting");
                issue.put("severity", "medium");
                issue.put("markdown_count", markdownCount);
                issue.put("total_count", responses.size());
                issues.add(issue);
            }

            // Detect language mixing
            Map<String, Long> languages = responses.stream()
                    .collect(Collectors.groupingBy(
                            r -> detectLanguage(r.getResponse()),
                            Collectors.counting()
                    ));

            if (languages.size() > 1) {
                promptScore -= 0.2;
                Map<String, Object> issue = new HashMap<>();
                issue.put("prompt", truncate(prompt, 60));
                issue.put("description", "Multiple languages detected");
                issue.put("severity", "high");
                issue.put("languages", languages);
                issues.add(issue);
            }

            totalScore += Math.max(0.0, promptScore);
        }

        double structuralScore = promptCount > 0 ? totalScore / promptCount : 1.0;

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("score", structuralScore);
        analysis.put("issues", issues);

        System.out.println("  ✓ Structural score: " + String.format("%.3f", structuralScore));
        return analysis;
    }

    /**
     * Analyzes semantic consistency using keyword overlap and similarity heuristics.
     */
    private Map<String, Object> analyzeSemanticConsistency(Map<String, List<ResponseMetadata>> byPrompt) {
        System.out.println("📊 Analyzing semantic consistency...");

        List<Map<String, Object>> issues = new ArrayList<>();
        double totalScore = 0.0;
        int promptCount = 0;

        for (Map.Entry<String, List<ResponseMetadata>> entry : byPrompt.entrySet()) {
            String prompt = entry.getKey();
            List<ResponseMetadata> responses = entry.getValue();

            if (responses.size() < 2) continue;
            promptCount++;

            // Extract keywords from all responses
            List<Set<String>> keywordSets = responses.stream()
                    .map(r -> extractKeywords(r.getResponse()))
                    .collect(Collectors.toList());

            // Calculate average pairwise Jaccard similarity
            double avgSimilarity = calculateAverageJaccardSimilarity(keywordSets);

            double promptScore = avgSimilarity;

            if (avgSimilarity < 0.6) { // Low semantic similarity
                Map<String, Object> issue = new HashMap<>();
                issue.put("prompt", truncate(prompt, 60));
                issue.put("description", "Low semantic similarity between responses");
                issue.put("severity", avgSimilarity < 0.4 ? "high" : "medium");
                issue.put("similarity_score", avgSimilarity);
                issue.put("response_count", responses.size());
                issues.add(issue);
            }

            totalScore += promptScore;
        }

        double semanticScore = promptCount > 0 ? totalScore / promptCount : 1.0;

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("score", semanticScore);
        analysis.put("issues", issues);

        System.out.println("  ✓ Semantic score: " + String.format("%.3f", semanticScore));
        return analysis;
    }

    /**
     * Analyzes temporal patterns (quality degradation over test phases).
     */
    private Map<String, Object> analyzeTemporalPatterns(Map<String, List<ResponseMetadata>> byPhase) {
        System.out.println("📊 Analyzing temporal patterns...");

        Map<String, Object> analysis = new HashMap<>();

        List<ResponseMetadata> rampPhase = byPhase.getOrDefault("RAMP", Collections.emptyList());
        List<ResponseMetadata> steadyPhase = byPhase.getOrDefault("STEADY", Collections.emptyList());

        double rampTruncationRate = calculateTruncationRate(rampPhase);
        double steadyTruncationRate = calculateTruncationRate(steadyPhase);

        double rampAvgResponseTime = calculateAvgResponseTime(rampPhase);
        double steadyAvgResponseTime = calculateAvgResponseTime(steadyPhase);

        double degradation = steadyTruncationRate - rampTruncationRate;
        boolean degradationDetected = degradation > 0.1; // More than 10% increase in truncation

        double temporalScore = 1.0 - Math.max(0.0, degradation);

        analysis.put("score", temporalScore);
        analysis.put("ramp_truncation_rate", rampTruncationRate);
        analysis.put("steady_truncation_rate", steadyTruncationRate);
        analysis.put("ramp_avg_response_time_ms", rampAvgResponseTime);
        analysis.put("steady_avg_response_time_ms", steadyAvgResponseTime);
        analysis.put("degradation_detected", degradationDetected);
        analysis.put("degradation_magnitude", degradation);

        System.out.println("  ✓ Temporal score: " + String.format("%.3f", temporalScore));
        return analysis;
    }

    /**
     * Analyzes impact by category.
     */
    private Map<String, Object> analyzeCategoryImpact(Map<String, List<ResponseMetadata>> byCategory) {
        System.out.println("📊 Analyzing category impact...");

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

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("categories", categoryScores);

        double avgCategoryScore = categoryScores.values().stream()
                .mapToDouble(m -> (double) m.get("score"))
                .average()
                .orElse(1.0);

        analysis.put("score", avgCategoryScore);

        System.out.println("  ✓ Category score: " + String.format("%.3f", avgCategoryScore));
        return analysis;
    }

    /**
     * Calculates global consistency score based on all dimensions.
     */
    @SuppressWarnings("unchecked")
    private double calculateGlobalScore(Map<String, Object> report) {
        Map<String, Object> completeness = (Map<String, Object>) report.get("completeness_analysis");
        Map<String, Object> structural = (Map<String, Object>) report.get("structural_analysis");
        Map<String, Object> semantic = (Map<String, Object>) report.get("semantic_analysis");
        Map<String, Object> temporal = (Map<String, Object>) report.get("temporal_analysis");
        Map<String, Object> category = (Map<String, Object>) report.get("category_analysis");

        double score = ((double) completeness.get("score")) * 0.25 +
                       ((double) structural.get("score")) * 0.25 +
                       ((double) semantic.get("score")) * 0.40 +
                       ((double) temporal.get("score")) * 0.05 +
                       ((double) category.get("score")) * 0.05;

        System.out.println("\n🎯 Global Consistency Score: " + String.format("%.3f", score));
        return score;
    }

    /**
     * Generates a human-readable summary of the analysis.
     */
    @SuppressWarnings("unchecked")
    private String generateSummary(Map<String, Object> report) {
        double score = (double) report.get("global_consistency_score");
        int totalResponses = (int) report.get("total_responses");

        Map<String, Object> completeness = (Map<String, Object>) report.get("completeness_analysis");
        long truncatedCount = (long) completeness.get("truncated_count");

        Map<String, Object> temporal = (Map<String, Object>) report.get("temporal_analysis");
        boolean degradation = (boolean) temporal.get("degradation_detected");

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
        summary.append(String.format("Analizadas %d respuestas. ", totalResponses));

        if (truncatedCount > 0) {
            summary.append(String.format("%d respuestas truncadas. ", truncatedCount));
        }

        if (degradation) {
            summary.append("Degradación detectada bajo carga sostenida. ");
        }

        return summary.toString();
    }

    // Helper Methods

    private boolean containsMarkdown(String text) {
        return text.contains("```") || text.contains("**") || text.contains("##") || text.contains("- ");
    }

    private String detectLanguage(String text) {
        // Simple heuristic: count Spanish vs English common words
        String[] spanishWords = {"el", "la", "los", "las", "de", "que", "es", "un", "una", "para", "con"};
        String[] englishWords = {"the", "is", "are", "of", "to", "and", "a", "in", "that", "have"};

        long spanishCount = Arrays.stream(spanishWords)
                .filter(word -> text.toLowerCase().contains(" " + word + " "))
                .count();

        long englishCount = Arrays.stream(englishWords)
                .filter(word -> text.toLowerCase().contains(" " + word + " "))
                .count();

        return spanishCount > englishCount ? "Spanish" : "English";
    }

    private Set<String> extractKeywords(String text) {
        // Extract words longer than 3 characters, lowercase, remove common words
        Set<String> stopwords = Set.of("the", "is", "are", "and", "or", "but", "with", "for",
                "el", "la", "de", "que", "es", "un", "una", "para", "con", "por");

        return Pattern.compile("\\w+")
                .matcher(text.toLowerCase())
                .results()
                .map(m -> m.group())
                .filter(word -> word.length() > 3)
                .filter(word -> !stopwords.contains(word))
                .collect(Collectors.toSet());
    }

    private double calculateAverageJaccardSimilarity(List<Set<String>> keywordSets) {
        if (keywordSets.size() < 2) return 1.0;

        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < keywordSets.size(); i++) {
            for (int j = i + 1; j < keywordSets.size(); j++) {
                double similarity = jaccardSimilarity(keywordSets.get(i), keywordSets.get(j));
                similarities.add(similarity);
            }
        }

        return similarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double jaccardSimilarity(Set<String> set1, Set<String> set2) {
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double calculateTruncationRate(List<ResponseMetadata> responses) {
        if (responses.isEmpty()) return 0.0;
        long truncated = responses.stream().filter(ResponseMetadata::isTruncated).count();
        return (double) truncated / responses.size();
    }

    private double calculateAvgResponseTime(List<ResponseMetadata> responses) {
        return responses.stream()
                .mapToLong(ResponseMetadata::getResponseTimeMs)
                .average()
                .orElse(0.0);
    }

    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Saves the analysis report to a JSON file.
     */
    public void saveReport(Map<String, Object> report, Path outputFile) throws IOException {
        String json = objectMapper.writeValueAsString(report);
        Files.writeString(outputFile, json);
        System.out.println("💾 Analysis report saved to: " + outputFile);
    }

    /**
     * Main method for running the analyzer independently.
     */
    public static void main(String[] args) {
        try {
            Path metadataFile = Path.of("target/responses_metadata.jsonl");
            Path reportFile = Path.of("target/consistency_analysis.json");

            ConsistencyAnalyzer analyzer = new ConsistencyAnalyzer(metadataFile);
            Map<String, Object> report = analyzer.analyze();
            analyzer.saveReport(report, reportFile);

            System.out.println("\n✅ Consistency analysis completed successfully!");
            System.out.println("📊 Report saved to: " + reportFile);

        } catch (IOException e) {
            System.err.println("❌ Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
