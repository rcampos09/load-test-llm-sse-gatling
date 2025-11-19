package ssellm.analyzers;

import ssellm.models.ResponseMetadata;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced metrics and statistical analysis for Sprint 2.
 * Provides utilities for detecting anomalies and calculating statistical measures.
 */
public class AdvancedMetrics {

    /**
     * Detect anomalies in response patterns
     *
     * @param responses List of all responses
     * @return List of detected anomalies
     */
    public static List<Anomaly> detectAnomalies(List<ResponseMetadata> responses) {
        List<Anomaly> anomalies = new ArrayList<>();

        // Calculate statistics
        double avgLatency = responses.stream()
            .mapToLong(ResponseMetadata::getResponseTimeMs)
            .average()
            .orElse(0.0);

        double stdDevLatency = calculateStandardDeviation(
            responses.stream().mapToLong(ResponseMetadata::getResponseTimeMs).toArray()
        );

        // Detect latency outliers (> 3 standard deviations)
        double latencyThreshold = avgLatency + (3 * stdDevLatency);

        for (ResponseMetadata response : responses) {
            if (response.getResponseTimeMs() > latencyThreshold) {
                anomalies.add(new Anomaly(
                    "LATENCY_OUTLIER",
                    response.getPrompt(),
                    "Latency " + response.getResponseTimeMs() + "ms exceeds threshold " +
                        String.format("%.0f", latencyThreshold) + "ms",
                    Anomaly.Severity.WARNING
                ));
            }

            // Detect truncation in short prompts (should rarely happen)
            if (response.getCategory().equals("short") && response.isTruncated()) {
                anomalies.add(new Anomaly(
                    "SHORT_PROMPT_TRUNCATED",
                    response.getPrompt(),
                    "Short prompt was truncated after " + response.getResponseTimeMs() + "ms",
                    Anomaly.Severity.ERROR
                ));
            }

            // Detect empty responses
            if (response.getResponse() == null || response.getResponse().isEmpty()) {
                anomalies.add(new Anomaly(
                    "EMPTY_RESPONSE",
                    response.getPrompt(),
                    "Response was empty",
                    Anomaly.Severity.ERROR
                ));
            }
        }

        return anomalies;
    }

    /**
     * Calculate response length statistics
     *
     * @param responses List of responses
     * @return ResponseLengthStats with metrics
     */
    public static ResponseLengthStats calculateResponseLengthStats(List<ResponseMetadata> responses) {
        List<Integer> lengths = responses.stream()
            .map(r -> r.getResponse() != null ? r.getResponse().length() : 0)
            .collect(Collectors.toList());

        if (lengths.isEmpty()) {
            return new ResponseLengthStats(0, 0, 0, 0, 0.0);
        }

        Collections.sort(lengths);

        int minLength = lengths.get(0);
        int maxLength = lengths.get(lengths.size() - 1);
        double avgLength = lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int medianLength = lengths.get(lengths.size() / 2);
        double stdDev = calculateStandardDeviation(
            lengths.stream().mapToLong(Integer::longValue).toArray()
        );

        return new ResponseLengthStats(minLength, maxLength, avgLength, medianLength, stdDev);
    }

    /**
     * Calculate percentile value
     */
    public static double calculatePercentile(List<Double> values, double percentile) {
        if (values.isEmpty()) return 0.0;

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));

        return sorted.get(index);
    }

    /**
     * Calculate standard deviation
     */
    private static double calculateStandardDeviation(long[] values) {
        if (values.length == 0) return 0.0;

        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Calculate truncation rate by category
     */
    public static Map<String, Double> calculateTruncationByCategory(List<ResponseMetadata> responses) {
        Map<String, List<ResponseMetadata>> byCategory = responses.stream()
            .collect(Collectors.groupingBy(ResponseMetadata::getCategory));

        Map<String, Double> truncationRates = new HashMap<>();

        for (Map.Entry<String, List<ResponseMetadata>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<ResponseMetadata> categoryResponses = entry.getValue();

            long truncatedCount = categoryResponses.stream()
                .filter(ResponseMetadata::isTruncated)
                .count();

            double truncationRate = (truncatedCount * 100.0) / categoryResponses.size();
            truncationRates.put(category, truncationRate);
        }

        return truncationRates;
    }

    /**
     * Calculate latency statistics by category
     */
    public static Map<String, LatencyStats> calculateLatencyByCategory(List<ResponseMetadata> responses) {
        Map<String, List<ResponseMetadata>> byCategory = responses.stream()
            .collect(Collectors.groupingBy(ResponseMetadata::getCategory));

        Map<String, LatencyStats> latencyStats = new HashMap<>();

        for (Map.Entry<String, List<ResponseMetadata>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<ResponseMetadata> categoryResponses = entry.getValue();

            long[] latencies = categoryResponses.stream()
                .mapToLong(ResponseMetadata::getResponseTimeMs)
                .toArray();

            double avg = Arrays.stream(latencies).average().orElse(0.0);
            double min = latencies.length > 0 ? Arrays.stream(latencies).min().orElse(0) : 0;
            double max = latencies.length > 0 ? Arrays.stream(latencies).max().orElse(0) : 0;
            double stdDev = calculateStandardDeviation(latencies);

            latencyStats.put(category, new LatencyStats(avg, min, max, stdDev));
        }

        return latencyStats;
    }

    // ========== Data Classes ==========

    public static class Anomaly {
        public enum Severity { INFO, WARNING, ERROR, CRITICAL }

        private final String type;
        private final String prompt;
        private final String description;
        private final Severity severity;

        public Anomaly(String type, String prompt, String description, Severity severity) {
            this.type = type;
            this.prompt = prompt;
            this.description = description;
            this.severity = severity;
        }

        // Getters
        public String getType() { return type; }
        public String getPrompt() { return prompt; }
        public String getDescription() { return description; }
        public Severity getSeverity() { return severity; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (prompt: %s)",
                severity, type, description,
                prompt.substring(0, Math.min(40, prompt.length())));
        }
    }

    public static class ResponseLengthStats {
        private final int minLength;
        private final int maxLength;
        private final double avgLength;
        private final int medianLength;
        private final double stdDev;

        public ResponseLengthStats(int minLength, int maxLength, double avgLength,
                                  int medianLength, double stdDev) {
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.avgLength = avgLength;
            this.medianLength = medianLength;
            this.stdDev = stdDev;
        }

        // Getters
        public int getMinLength() { return minLength; }
        public int getMaxLength() { return maxLength; }
        public double getAvgLength() { return avgLength; }
        public int getMedianLength() { return medianLength; }
        public double getStdDev() { return stdDev; }

        @Override
        public String toString() {
            return String.format("Length Stats: min=%d, max=%d, avg=%.0f, median=%d, stdDev=%.0f",
                minLength, maxLength, avgLength, medianLength, stdDev);
        }
    }

    public static class LatencyStats {
        private final double avg;
        private final double min;
        private final double max;
        private final double stdDev;

        public LatencyStats(double avg, double min, double max, double stdDev) {
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.stdDev = stdDev;
        }

        // Getters
        public double getAvg() { return avg; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getStdDev() { return stdDev; }

        @Override
        public String toString() {
            return String.format("Latency: avg=%.0fms, min=%.0fms, max=%.0fms, stdDev=%.0fms",
                avg, min, max, stdDev);
        }
    }

    /**
     * Print anomaly report
     */
    public static void printAnomalies(List<Anomaly> anomalies) {
        if (anomalies.isEmpty()) {
            System.out.println("\n✅ No anomalies detected");
            return;
        }

        System.out.println("\n⚠️ Anomalies Detected: " + anomalies.size());
        System.out.println("=".repeat(100));

        Map<Anomaly.Severity, List<Anomaly>> bySeverity = anomalies.stream()
            .collect(Collectors.groupingBy(Anomaly::getSeverity));

        for (Anomaly.Severity severity : Anomaly.Severity.values()) {
            List<Anomaly> severityAnomalies = bySeverity.getOrDefault(severity, Collections.emptyList());
            if (!severityAnomalies.isEmpty()) {
                System.out.println("\n" + severity + ": " + severityAnomalies.size());
                severityAnomalies.forEach(a -> System.out.println("  " + a));
            }
        }

        System.out.println("=".repeat(100));
    }

    /**
     * Print truncation rates by category
     */
    public static void printTruncationByCategory(Map<String, Double> truncationRates) {
        System.out.println("\n📊 Truncation Rates by Category:");
        System.out.println("=".repeat(60));

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(truncationRates.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        for (Map.Entry<String, Double> entry : sorted) {
            String icon = entry.getValue() < 30 ? "✅" : entry.getValue() < 50 ? "⚠️" : "❌";
            System.out.println(String.format("%s %-20s: %5.1f%%",
                icon, entry.getKey(), entry.getValue()));
        }

        System.out.println("=".repeat(60));
    }

    /**
     * Print latency statistics by category
     */
    public static void printLatencyByCategory(Map<String, LatencyStats> latencyStats) {
        System.out.println("\n⏱️ Latency Statistics by Category:");
        System.out.println("=".repeat(100));

        List<Map.Entry<String, LatencyStats>> sorted = new ArrayList<>(latencyStats.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getValue().getAvg()));

        for (Map.Entry<String, LatencyStats> entry : sorted) {
            System.out.println(String.format("%-20s: %s",
                entry.getKey(), entry.getValue()));
        }

        System.out.println("=".repeat(100));
    }
}
