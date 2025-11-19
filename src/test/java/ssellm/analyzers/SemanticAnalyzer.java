package ssellm.analyzers;

import org.apache.commons.math3.linear.ArrayRealVector;
import ssellm.clients.OpenAIClient;
import ssellm.models.ResponseMetadata;
import ssellm.models.SemanticAnalysisResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic analyzer using OpenAI embeddings and cosine similarity.
 * Sprint 2: Advanced semantic analysis replacing Jaccard similarity.
 */
public class SemanticAnalyzer {

    private final OpenAIClient openAIClient;
    private static final double SIMILARITY_THRESHOLD = 0.70;  // 70% similarity threshold

    public SemanticAnalyzer(String apiKey) {
        this.openAIClient = new OpenAIClient(apiKey);
    }

    /**
     * Analyze semantic similarity between responses to the same prompt
     *
     * @param prompt    The original prompt
     * @param responses List of ResponseMetadata objects
     * @return SemanticAnalysisResult with similarity scores
     */
    public SemanticAnalysisResult analyzeSimilarity(String prompt, List<ResponseMetadata> responses) {
        if (responses == null || responses.isEmpty()) {
            throw new IllegalArgumentException("Responses list cannot be null or empty");
        }

        System.out.println("\n🔍 Analyzing semantic similarity for prompt: " +
            prompt.substring(0, Math.min(60, prompt.length())) + "...");
        System.out.println("   Responses to analyze: " + responses.size());

        try {
            // Extract response texts
            List<String> responseTexts = new ArrayList<>();
            for (ResponseMetadata metadata : responses) {
                // Only analyze non-truncated responses for fair comparison
                if (!metadata.isTruncated() && metadata.getResponse() != null &&
                    !metadata.getResponse().isEmpty()) {
                    responseTexts.add(metadata.getResponse());
                }
            }

            if (responseTexts.size() < 2) {
                System.out.println("   ⚠️ Not enough complete responses for similarity analysis (need >=2, have " +
                    responseTexts.size() + ")");
                return createEmptyResult(prompt, responses.get(0).getCategory(), responses.size());
            }

            System.out.println("   ✓ Analyzing " + responseTexts.size() + " complete responses");

            // Get embeddings from OpenAI (batch processing)
            System.out.println("   🌐 Fetching embeddings from OpenAI...");
            List<List<Double>> embeddings = openAIClient.getEmbeddings(responseTexts);
            System.out.println("   ✓ Embeddings received: " + embeddings.size() + " vectors of " +
                embeddings.get(0).size() + " dimensions");

            // Calculate similarity matrix
            double[][] similarityMatrix = calculateSimilarityMatrix(embeddings);

            // Calculate statistics
            double avgSimilarity = calculateAverageSimilarity(similarityMatrix);
            double minSimilarity = calculateMinSimilarity(similarityMatrix);
            double maxSimilarity = calculateMaxSimilarity(similarityMatrix);

            System.out.println("   📊 Similarity scores:");
            System.out.println("      Average: " + String.format("%.3f", avgSimilarity));
            System.out.println("      Min: " + String.format("%.3f", minSimilarity));
            System.out.println("      Max: " + String.format("%.3f", maxSimilarity));

            // Detect issues
            boolean isConsistent = avgSimilarity >= SIMILARITY_THRESHOLD;
            List<String> issues = new ArrayList<>();

            if (!isConsistent) {
                issues.add("Low average similarity (" + String.format("%.3f", avgSimilarity) +
                    ") below threshold (" + SIMILARITY_THRESHOLD + ")");
            }

            if (minSimilarity < 0.50) {
                issues.add("Very low minimum similarity (" + String.format("%.3f", minSimilarity) +
                    ") indicates outlier responses");
            }

            // Build result
            SemanticAnalysisResult result = SemanticAnalysisResult.builder()
                .prompt(prompt)
                .category(responses.get(0).getCategory())
                .responseCount(responseTexts.size())
                .avgSimilarity(avgSimilarity)
                .minSimilarity(minSimilarity)
                .maxSimilarity(maxSimilarity)
                .similarityMatrix(similarityMatrix)
                .isConsistent(isConsistent)
                .issues(issues)
                .build();

            System.out.println("   " + (isConsistent ? "✅" : "⚠️") + " Consistency: " +
                (isConsistent ? "PASS" : "FAIL") + (issues.isEmpty() ? "" : " - " + issues.size() + " issues"));

            return result;

        } catch (Exception e) {
            System.err.println("   ❌ Error analyzing semantic similarity: " + e.getMessage());
            e.printStackTrace();
            return createEmptyResult(prompt, responses.get(0).getCategory(), responses.size());
        }
    }

    /**
     * Calculate cosine similarity between two embedding vectors
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity (0-1)
     */
    public double cosineSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1.size() != embedding2.size()) {
            throw new IllegalArgumentException("Embeddings must have the same dimension");
        }

        // Convert to double arrays
        double[] arr1 = embedding1.stream().mapToDouble(Double::doubleValue).toArray();
        double[] arr2 = embedding2.stream().mapToDouble(Double::doubleValue).toArray();

        // Use Apache Commons Math for vector operations
        ArrayRealVector v1 = new ArrayRealVector(arr1);
        ArrayRealVector v2 = new ArrayRealVector(arr2);

        // Calculate cosine similarity: dot(v1, v2) / (norm(v1) * norm(v2))
        double dotProduct = v1.dotProduct(v2);
        double norm1 = v1.getNorm();
        double norm2 = v2.getNorm();

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (norm1 * norm2);
    }

    /**
     * Calculate similarity matrix for all pairs of embeddings
     *
     * @param embeddings List of embedding vectors
     * @return 2D array with similarity scores
     */
    private double[][] calculateSimilarityMatrix(List<List<Double>> embeddings) {
        int n = embeddings.size();
        double[][] matrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 1.0;  // Self-similarity is always 1
                } else if (i < j) {
                    // Calculate similarity only once per pair
                    double similarity = cosineSimilarity(embeddings.get(i), embeddings.get(j));
                    matrix[i][j] = similarity;
                    matrix[j][i] = similarity;  // Symmetric matrix
                }
            }
        }

        return matrix;
    }

    /**
     * Calculate average similarity from matrix (excluding diagonal)
     */
    private double calculateAverageSimilarity(double[][] matrix) {
        int n = matrix.length;
        if (n <= 1) return 1.0;

        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                sum += matrix[i][j];
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Find minimum similarity (excluding diagonal)
     */
    private double calculateMinSimilarity(double[][] matrix) {
        int n = matrix.length;
        if (n <= 1) return 1.0;

        double min = 1.0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (matrix[i][j] < min) {
                    min = matrix[i][j];
                }
            }
        }

        return min;
    }

    /**
     * Find maximum similarity (excluding diagonal)
     */
    private double calculateMaxSimilarity(double[][] matrix) {
        int n = matrix.length;
        if (n <= 1) return 1.0;

        double max = 0.0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (matrix[i][j] > max) {
                    max = matrix[i][j];
                }
            }
        }

        return max;
    }

    /**
     * Create an empty result when analysis cannot be performed
     */
    private SemanticAnalysisResult createEmptyResult(String prompt, String category, int responseCount) {
        return SemanticAnalysisResult.builder()
            .prompt(prompt)
            .category(category)
            .responseCount(responseCount)
            .avgSimilarity(0.0)
            .minSimilarity(0.0)
            .maxSimilarity(0.0)
            .similarityMatrix(new double[0][0])
            .isConsistent(false)
            .issues(List.of("Insufficient data for analysis"))
            .build();
    }

    /**
     * Test the OpenAI connection
     */
    public boolean testConnection() {
        return openAIClient.testConnection();
    }
}
