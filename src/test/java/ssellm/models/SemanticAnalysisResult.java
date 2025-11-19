package ssellm.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the result of semantic analysis using embeddings and cosine similarity.
 * Sprint 2: Advanced semantic analysis replacing Jaccard similarity.
 */
public class SemanticAnalysisResult {

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("category")
    private String category;

    @JsonProperty("response_count")
    private int responseCount;

    @JsonProperty("avg_similarity")
    private double avgSimilarity;

    @JsonProperty("min_similarity")
    private double minSimilarity;

    @JsonProperty("max_similarity")
    private double maxSimilarity;

    @JsonProperty("similarity_matrix")
    private double[][] similarityMatrix;

    @JsonProperty("is_consistent")
    private boolean isConsistent;

    @JsonProperty("issues")
    private List<String> issues;

    // Constructors
    public SemanticAnalysisResult() {
        this.isConsistent = true;
    }

    // Builder Pattern
    public static class Builder {
        private SemanticAnalysisResult result = new SemanticAnalysisResult();

        public Builder prompt(String prompt) {
            result.prompt = prompt;
            return this;
        }

        public Builder category(String category) {
            result.category = category;
            return this;
        }

        public Builder responseCount(int responseCount) {
            result.responseCount = responseCount;
            return this;
        }

        public Builder avgSimilarity(double avgSimilarity) {
            result.avgSimilarity = avgSimilarity;
            return this;
        }

        public Builder minSimilarity(double minSimilarity) {
            result.minSimilarity = minSimilarity;
            return this;
        }

        public Builder maxSimilarity(double maxSimilarity) {
            result.maxSimilarity = maxSimilarity;
            return this;
        }

        public Builder similarityMatrix(double[][] similarityMatrix) {
            result.similarityMatrix = similarityMatrix;
            return this;
        }

        public Builder isConsistent(boolean isConsistent) {
            result.isConsistent = isConsistent;
            return this;
        }

        public Builder issues(List<String> issues) {
            result.issues = issues;
            return this;
        }

        public SemanticAnalysisResult build() {
            return result;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getResponseCount() {
        return responseCount;
    }

    public void setResponseCount(int responseCount) {
        this.responseCount = responseCount;
    }

    public double getAvgSimilarity() {
        return avgSimilarity;
    }

    public void setAvgSimilarity(double avgSimilarity) {
        this.avgSimilarity = avgSimilarity;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public double getMaxSimilarity() {
        return maxSimilarity;
    }

    public void setMaxSimilarity(double maxSimilarity) {
        this.maxSimilarity = maxSimilarity;
    }

    public double[][] getSimilarityMatrix() {
        return similarityMatrix;
    }

    public void setSimilarityMatrix(double[][] similarityMatrix) {
        this.similarityMatrix = similarityMatrix;
    }

    public boolean isConsistent() {
        return isConsistent;
    }

    public void setConsistent(boolean consistent) {
        isConsistent = consistent;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    @Override
    public String toString() {
        return String.format("SemanticAnalysisResult{prompt='%s', category='%s', " +
                "responses=%d, avgSimilarity=%.3f, isConsistent=%s}",
                prompt.substring(0, Math.min(50, prompt.length())) + "...",
                category, responseCount, avgSimilarity, isConsistent);
    }
}
