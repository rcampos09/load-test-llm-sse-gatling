package ssellm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ssellm.models.ResponseMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates LLM responses by prompt from JSONL metadata file.
 *
 * This class reads the responses_metadata.jsonl file generated during load tests
 * and groups responses by their prompt text for consistency analysis.
 */
public class ResponseAggregator {

    private final ObjectMapper objectMapper;
    private final Path metadataFile;

    public ResponseAggregator(Path metadataFile) {
        this.metadataFile = metadataFile;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Reads all responses from the JSONL file.
     *
     * @return List of ResponseMetadata objects
     * @throws IOException if file reading fails
     */
    public List<ResponseMetadata> readAllResponses() throws IOException {
        if (!Files.exists(metadataFile)) {
            System.err.println("⚠️ Metadata file not found: " + metadataFile);
            return Collections.emptyList();
        }

        List<ResponseMetadata> responses = new ArrayList<>();
        List<String> lines = Files.readAllLines(metadataFile);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }

            try {
                ResponseMetadata metadata = objectMapper.readValue(line, ResponseMetadata.class);
                responses.add(metadata);
            } catch (IOException e) {
                System.err.println("⚠️ Error parsing line " + (i + 1) + ": " + e.getMessage());
            }
        }

        System.out.println("✅ Loaded " + responses.size() + " responses from " + metadataFile);
        return responses;
    }

    /**
     * Groups responses by their prompt text.
     *
     * @return Map where key is the prompt and value is list of responses to that prompt
     * @throws IOException if file reading fails
     */
    public Map<String, List<ResponseMetadata>> groupByPrompt() throws IOException {
        List<ResponseMetadata> allResponses = readAllResponses();

        Map<String, List<ResponseMetadata>> grouped = allResponses.stream()
                .collect(Collectors.groupingBy(ResponseMetadata::getPrompt));

        System.out.println("📊 Grouped responses:");
        grouped.forEach((prompt, responses) -> {
            System.out.println("  - \"" + truncate(prompt, 60) + "\" → " + responses.size() + " responses");
        });

        return grouped;
    }

    /**
     * Groups responses by category.
     *
     * @return Map where key is the category and value is list of responses in that category
     * @throws IOException if file reading fails
     */
    public Map<String, List<ResponseMetadata>> groupByCategory() throws IOException {
        List<ResponseMetadata> allResponses = readAllResponses();

        Map<String, List<ResponseMetadata>> grouped = allResponses.stream()
                .collect(Collectors.groupingBy(ResponseMetadata::getCategory));

        System.out.println("📊 Grouped by category:");
        grouped.forEach((category, responses) -> {
            System.out.println("  - " + category + " → " + responses.size() + " responses");
        });

        return grouped;
    }

    /**
     * Groups responses by test phase (RAMP or STEADY).
     *
     * @return Map where key is the test phase and value is list of responses in that phase
     * @throws IOException if file reading fails
     */
    public Map<String, List<ResponseMetadata>> groupByTestPhase() throws IOException {
        List<ResponseMetadata> allResponses = readAllResponses();

        Map<String, List<ResponseMetadata>> grouped = allResponses.stream()
                .collect(Collectors.groupingBy(ResponseMetadata::getTestPhase));

        System.out.println("📊 Grouped by test phase:");
        grouped.forEach((phase, responses) -> {
            System.out.println("  - " + phase + " → " + responses.size() + " responses");
        });

        return grouped;
    }

    /**
     * Gets statistics about truncated responses.
     *
     * @return Map with truncation statistics
     * @throws IOException if file reading fails
     */
    public Map<String, Object> getTruncationStats() throws IOException {
        List<ResponseMetadata> allResponses = readAllResponses();

        long totalResponses = allResponses.size();
        long truncatedCount = allResponses.stream()
                .filter(ResponseMetadata::isTruncated)
                .count();

        Map<String, Long> truncationReasons = allResponses.stream()
                .filter(ResponseMetadata::isTruncated)
                .collect(Collectors.groupingBy(
                        ResponseMetadata::getTruncationReason,
                        Collectors.counting()
                ));

        double truncationRate = totalResponses > 0
                ? (double) truncatedCount / totalResponses
                : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_responses", totalResponses);
        stats.put("truncated_count", truncatedCount);
        stats.put("truncation_rate", truncationRate);
        stats.put("truncation_reasons", truncationReasons);

        System.out.println("📊 Truncation Statistics:");
        System.out.println("  - Total responses: " + totalResponses);
        System.out.println("  - Truncated: " + truncatedCount + " (" + String.format("%.2f%%", truncationRate * 100) + ")");
        System.out.println("  - Reasons: " + truncationReasons);

        return stats;
    }

    /**
     * Saves the grouped responses to a JSON file.
     *
     * @param outputFile Path to the output JSON file
     * @throws IOException if file writing fails
     */
    public void saveGroupedResponses(Path outputFile) throws IOException {
        Map<String, List<ResponseMetadata>> grouped = groupByPrompt();

        // Create a structure that's easier to read
        Map<String, Object> output = new HashMap<>();

        for (Map.Entry<String, List<ResponseMetadata>> entry : grouped.entrySet()) {
            String prompt = entry.getKey();
            List<ResponseMetadata> responses = entry.getValue();

            Map<String, Object> promptData = new HashMap<>();
            if (!responses.isEmpty()) {
                ResponseMetadata first = responses.get(0);
                promptData.put("category", first.getCategory());
                promptData.put("max_tokens", first.getMaxTokens());
                promptData.put("temperature", first.getTemperature());
            }
            promptData.put("total_responses", responses.size());
            promptData.put("responses", responses);

            output.put(prompt, promptData);
        }

        // Write to file with pretty printing
        ObjectMapper prettyMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);

        String json = prettyMapper.writeValueAsString(output);
        Files.writeString(outputFile, json);

        System.out.println("💾 Grouped responses saved to: " + outputFile);
    }

    /**
     * Utility method to truncate long strings for display.
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Main method for testing the aggregator independently.
     */
    public static void main(String[] args) {
        try {
            Path metadataFile = Path.of("target/responses_metadata.jsonl");
            Path outputFile = Path.of("target/responses_by_prompt.json");

            ResponseAggregator aggregator = new ResponseAggregator(metadataFile);

            // Read and group responses
            aggregator.groupByPrompt();
            aggregator.groupByCategory();
            aggregator.groupByTestPhase();
            aggregator.getTruncationStats();

            // Save grouped responses
            aggregator.saveGroupedResponses(outputFile);

            System.out.println("\n✅ Response aggregation completed successfully!");

        } catch (IOException e) {
            System.err.println("❌ Error during aggregation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
