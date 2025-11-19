package ssellm.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Represents metadata and content of a single LLM response captured during load testing.
 *
 * This class stores all relevant information needed for consistency analysis including:
 * - Response content and identifiers
 * - Performance metrics (latency, TTFT, chunk count)
 * - Test context (category, prompt, test phase)
 * - Quality indicators (truncation flags)
 */
public class ResponseMetadata {

    // Identifiers
    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("chunk_id")
    private String chunkId;

    @JsonProperty("user_id")
    private long userId;

    // Test Context
    @JsonProperty("category")
    private String category;

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("max_tokens")
    private int maxTokens;

    @JsonProperty("temperature")
    private double temperature;

    // Response Content
    @JsonProperty("response")
    private String response;

    @JsonProperty("response_length")
    private int responseLength;

    // Performance Metrics
    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("response_time_ms")
    private long responseTimeMs;

    @JsonProperty("ttft_ms")
    private long ttftMs;  // Time To First Token

    @JsonProperty("total_chunks")
    private int totalChunks;

    // Quality Indicators
    @JsonProperty("truncated")
    private boolean truncated;

    @JsonProperty("truncation_reason")
    private String truncationReason;  // TIMEOUT, BUFFER_OVERFLOW, NONE

    @JsonProperty("test_phase")
    private String testPhase;  // RAMP, STEADY

    // Sprint 2: Track timeout used for this request
    @JsonProperty("timeout_used_ms")
    private long timeoutUsedMs;

    // Constructors
    public ResponseMetadata() {
        this.truncated = false;
        this.truncationReason = "NONE";
        this.timestamp = Instant.now();
    }

    // Builder Pattern for easier construction
    public static class Builder {
        private ResponseMetadata metadata = new ResponseMetadata();

        public Builder sessionId(String sessionId) {
            metadata.sessionId = sessionId;
            return this;
        }

        public Builder chunkId(String chunkId) {
            metadata.chunkId = chunkId;
            return this;
        }

        public Builder userId(long userId) {
            metadata.userId = userId;
            return this;
        }

        public Builder category(String category) {
            metadata.category = category;
            return this;
        }

        public Builder prompt(String prompt) {
            metadata.prompt = prompt;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            metadata.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            metadata.temperature = temperature;
            return this;
        }

        public Builder response(String response) {
            metadata.response = response;
            metadata.responseLength = response != null ? response.length() : 0;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            metadata.timestamp = timestamp;
            return this;
        }

        public Builder responseTimeMs(long responseTimeMs) {
            metadata.responseTimeMs = responseTimeMs;
            return this;
        }

        public Builder ttftMs(long ttftMs) {
            metadata.ttftMs = ttftMs;
            return this;
        }

        public Builder totalChunks(int totalChunks) {
            metadata.totalChunks = totalChunks;
            return this;
        }

        public Builder truncated(boolean truncated) {
            metadata.truncated = truncated;
            return this;
        }

        public Builder truncationReason(String truncationReason) {
            metadata.truncationReason = truncationReason;
            return this;
        }

        public Builder testPhase(String testPhase) {
            metadata.testPhase = testPhase;
            return this;
        }

        public Builder timeoutUsedMs(long timeoutUsedMs) {
            metadata.timeoutUsedMs = timeoutUsedMs;
            return this;
        }

        public ResponseMetadata build() {
            return metadata;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
        this.responseLength = response != null ? response.length() : 0;
    }

    public int getResponseLength() {
        return responseLength;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public long getTtftMs() {
        return ttftMs;
    }

    public void setTtftMs(long ttftMs) {
        this.ttftMs = ttftMs;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getTruncationReason() {
        return truncationReason;
    }

    public void setTruncationReason(String truncationReason) {
        this.truncationReason = truncationReason;
        if (!"NONE".equals(truncationReason)) {
            this.truncated = true;
        }
    }

    public String getTestPhase() {
        return testPhase;
    }

    public void setTestPhase(String testPhase) {
        this.testPhase = testPhase;
    }

    public long getTimeoutUsedMs() {
        return timeoutUsedMs;
    }

    public void setTimeoutUsedMs(long timeoutUsedMs) {
        this.timeoutUsedMs = timeoutUsedMs;
    }

    @Override
    public String toString() {
        return String.format("ResponseMetadata{sessionId='%s', category='%s', prompt='%s', " +
                "responseTime=%dms, ttft=%dms, chunks=%d, truncated=%s, phase=%s, timeout=%dms}",
                sessionId, category, prompt, responseTimeMs, ttftMs, totalChunks, truncated, testPhase, timeoutUsedMs);
    }
}
