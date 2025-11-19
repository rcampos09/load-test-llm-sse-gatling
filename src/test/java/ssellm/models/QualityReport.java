package ssellm.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive quality report combining all analysis dimensions from Sprint 2.
 * Sprint 2: Aggregated quality metrics with advanced semantic analysis.
 */
public class QualityReport {

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("global_consistency_score")
    private double globalConsistencyScore;

    @JsonProperty("total_requests")
    private int totalRequests;

    @JsonProperty("summary")
    private Summary summary;

    @JsonProperty("by_prompt")
    private List<PromptQualityScore> byPrompt;

    @JsonProperty("by_category")
    private Map<String, CategoryStats> byCategory;

    @JsonProperty("by_phase")
    private PhaseComparison byPhase;

    @JsonProperty("sprint_comparison")
    private SprintComparison sprintComparison;

    // Inner Classes
    public static class Summary {
        @JsonProperty("truncation_rate")
        public double truncationRate;

        @JsonProperty("avg_similarity_jaccard")
        public double avgSimilarityJaccard;

        @JsonProperty("avg_similarity_embeddings")
        public double avgSimilarityEmbeddings;

        @JsonProperty("avg_llm_judge_score")
        public double avgLLMJudgeScore;

        @JsonProperty("false_positive_rate")
        public double falsePositiveRate;
    }

    public static class PromptQualityScore {
        @JsonProperty("prompt")
        public String prompt;

        @JsonProperty("category")
        public String category;

        @JsonProperty("responses_count")
        public int responsesCount;

        @JsonProperty("truncation_rate")
        public double truncationRate;

        @JsonProperty("avg_response_time")
        public double avgResponseTime;

        @JsonProperty("similarity_jaccard")
        public double similarityJaccard;

        @JsonProperty("similarity_embeddings")
        public double similarityEmbeddings;

        @JsonProperty("llm_judge_score")
        public Double llmJudgeScore;  // Nullable - only for sampled prompts

        @JsonProperty("issues")
        public List<String> issues;
    }

    public static class CategoryStats {
        @JsonProperty("response_count")
        public int responseCount;

        @JsonProperty("truncation_rate")
        public double truncationRate;

        @JsonProperty("avg_response_time")
        public double avgResponseTime;

        @JsonProperty("avg_similarity")
        public double avgSimilarity;

        @JsonProperty("score")
        public double score;
    }

    public static class PhaseComparison {
        @JsonProperty("ramp")
        public PhaseStats ramp;

        @JsonProperty("steady")
        public PhaseStats steady;

        @JsonProperty("degradation_magnitude")
        public double degradationMagnitude;
    }

    public static class PhaseStats {
        @JsonProperty("response_count")
        public int responseCount;

        @JsonProperty("avg_response_time")
        public double avgResponseTime;

        @JsonProperty("truncation_rate")
        public double truncationRate;

        @JsonProperty("avg_similarity")
        public double avgSimilarity;
    }

    public static class SprintComparison {
        @JsonProperty("sprint1_truncation")
        public double sprint1Truncation;

        @JsonProperty("sprint2_truncation")
        public double sprint2Truncation;

        @JsonProperty("improvement_percentage")
        public double improvementPercentage;

        @JsonProperty("sprint1_score")
        public double sprint1Score;

        @JsonProperty("sprint2_score")
        public double sprint2Score;
    }

    // Constructors
    public QualityReport() {
        this.timestamp = Instant.now();
        this.summary = new Summary();
    }

    // Getters and Setters
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public double getGlobalConsistencyScore() {
        return globalConsistencyScore;
    }

    public void setGlobalConsistencyScore(double globalConsistencyScore) {
        this.globalConsistencyScore = globalConsistencyScore;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<PromptQualityScore> getByPrompt() {
        return byPrompt;
    }

    public void setByPrompt(List<PromptQualityScore> byPrompt) {
        this.byPrompt = byPrompt;
    }

    public Map<String, CategoryStats> getByCategory() {
        return byCategory;
    }

    public void setByCategory(Map<String, CategoryStats> byCategory) {
        this.byCategory = byCategory;
    }

    public PhaseComparison getByPhase() {
        return byPhase;
    }

    public void setByPhase(PhaseComparison byPhase) {
        this.byPhase = byPhase;
    }

    public SprintComparison getSprintComparison() {
        return sprintComparison;
    }

    public void setSprintComparison(SprintComparison sprintComparison) {
        this.sprintComparison = sprintComparison;
    }

    @Override
    public String toString() {
        return String.format("QualityReport{timestamp=%s, globalScore=%.3f, totalRequests=%d}",
                timestamp, globalConsistencyScore, totalRequests);
    }
}
