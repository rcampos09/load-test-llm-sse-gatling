package ssellm.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents an evaluation performed by GPT-4 as a judge of response quality.
 * Sprint 2: LLM-as-a-judge for qualitative analysis.
 */
public class LLMJudgeEvaluation {

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("category")
    private String category;

    @JsonProperty("response_count")
    private int responseCount;

    @JsonProperty("similarity_score")
    private double similarityScore;  // 0-10 scale

    @JsonProperty("technical_correctness")
    private double technicalCorrectness;  // 0-10 scale

    @JsonProperty("coherence_score")
    private double coherenceScore;  // 0-10 scale

    @JsonProperty("creativity_expected")
    private boolean creativityExpected;

    @JsonProperty("issues_detected")
    private List<String> issuesDetected;

    @JsonProperty("legitimate_variations")
    private List<String> legitimateVariations;

    @JsonProperty("raw_llm_response")
    private String rawLLMResponse;

    // Constructors
    public LLMJudgeEvaluation() {
    }

    // Builder Pattern
    public static class Builder {
        private LLMJudgeEvaluation evaluation = new LLMJudgeEvaluation();

        public Builder prompt(String prompt) {
            evaluation.prompt = prompt;
            return this;
        }

        public Builder category(String category) {
            evaluation.category = category;
            return this;
        }

        public Builder responseCount(int responseCount) {
            evaluation.responseCount = responseCount;
            return this;
        }

        public Builder similarityScore(double similarityScore) {
            evaluation.similarityScore = similarityScore;
            return this;
        }

        public Builder technicalCorrectness(double technicalCorrectness) {
            evaluation.technicalCorrectness = technicalCorrectness;
            return this;
        }

        public Builder coherenceScore(double coherenceScore) {
            evaluation.coherenceScore = coherenceScore;
            return this;
        }

        public Builder creativityExpected(boolean creativityExpected) {
            evaluation.creativityExpected = creativityExpected;
            return this;
        }

        public Builder issuesDetected(List<String> issuesDetected) {
            evaluation.issuesDetected = issuesDetected;
            return this;
        }

        public Builder legitimateVariations(List<String> legitimateVariations) {
            evaluation.legitimateVariations = legitimateVariations;
            return this;
        }

        public Builder rawLLMResponse(String rawLLMResponse) {
            evaluation.rawLLMResponse = rawLLMResponse;
            return this;
        }

        public LLMJudgeEvaluation build() {
            return evaluation;
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

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public double getTechnicalCorrectness() {
        return technicalCorrectness;
    }

    public void setTechnicalCorrectness(double technicalCorrectness) {
        this.technicalCorrectness = technicalCorrectness;
    }

    public double getCoherenceScore() {
        return coherenceScore;
    }

    public void setCoherenceScore(double coherenceScore) {
        this.coherenceScore = coherenceScore;
    }

    public boolean isCreativityExpected() {
        return creativityExpected;
    }

    public void setCreativityExpected(boolean creativityExpected) {
        this.creativityExpected = creativityExpected;
    }

    public List<String> getIssuesDetected() {
        return issuesDetected;
    }

    public void setIssuesDetected(List<String> issuesDetected) {
        this.issuesDetected = issuesDetected;
    }

    public List<String> getLegitimateVariations() {
        return legitimateVariations;
    }

    public void setLegitimateVariations(List<String> legitimateVariations) {
        this.legitimateVariations = legitimateVariations;
    }

    public String getRawLLMResponse() {
        return rawLLMResponse;
    }

    public void setRawLLMResponse(String rawLLMResponse) {
        this.rawLLMResponse = rawLLMResponse;
    }

    /**
     * Calculate overall quality score (0-10) as weighted average
     */
    public double getOverallScore() {
        return (similarityScore * 0.4 +
                technicalCorrectness * 0.4 +
                coherenceScore * 0.2);
    }

    @Override
    public String toString() {
        return String.format("LLMJudgeEvaluation{prompt='%s', category='%s', " +
                "overallScore=%.2f, similarity=%.2f, technical=%.2f, coherence=%.2f}",
                prompt.substring(0, Math.min(50, prompt.length())) + "...",
                category, getOverallScore(), similarityScore, technicalCorrectness, coherenceScore);
    }
}
