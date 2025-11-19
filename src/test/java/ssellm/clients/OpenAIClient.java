package ssellm.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for OpenAI API to perform embeddings and GPT-4 evaluations.
 * Sprint 2: Advanced semantic analysis using OpenAI embeddings and LLM-as-judge.
 */
public class OpenAIClient {

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String BASE_URL = "https://api.openai.com/v1";

    public OpenAIClient(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get embedding vector for a text using text-embedding-3-small model
     *
     * @param text Text to embed
     * @return Embedding vector as list of doubles
     */
    public List<Double> getEmbedding(String text) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "text-embedding-3-small");
        requestBody.put("input", text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        ArrayNode embeddingArray = (ArrayNode) responseJson.get("data").get(0).get("embedding");

        List<Double> embedding = new ArrayList<>();
        for (JsonNode value : embeddingArray) {
            embedding.add(value.asDouble());
        }

        return embedding;
    }

    /**
     * Get embeddings for multiple texts in a single API call (batch processing)
     *
     * @param texts List of texts to embed
     * @return List of embedding vectors
     */
    public List<List<Double>> getEmbeddings(List<String> texts) throws Exception {
        if (texts.isEmpty()) {
            return new ArrayList<>();
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "text-embedding-3-small");

        ArrayNode inputArray = requestBody.putArray("input");
        for (String text : texts) {
            inputArray.add(text);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        ArrayNode dataArray = (ArrayNode) responseJson.get("data");

        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode dataItem : dataArray) {
            ArrayNode embeddingArray = (ArrayNode) dataItem.get("embedding");
            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : embeddingArray) {
                embedding.add(value.asDouble());
            }
            embeddings.add(embedding);
        }

        return embeddings;
    }

    /**
     * Call GPT-4 for LLM-as-judge evaluation
     *
     * @param systemPrompt System prompt for the judge
     * @param userPrompt   User prompt containing the evaluation task
     * @return GPT-4 response as string
     */
    public String evaluateWithGPT4(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-4o");
        requestBody.put("temperature", 0.0);  // Deterministic for evaluation
        requestBody.put("max_tokens", 1500);

        ArrayNode messagesArray = requestBody.putArray("messages");

        ObjectNode systemMessage = messagesArray.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        ObjectNode userMessage = messagesArray.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.get("choices").get(0).get("message").get("content").asText();
    }

    /**
     * Call GPT-4 with JSON mode for structured output
     *
     * @param systemPrompt System prompt
     * @param userPrompt   User prompt
     * @return Parsed JSON response
     */
    public JsonNode evaluateWithGPT4JSON(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-4o");
        requestBody.put("temperature", 0.0);
        requestBody.put("max_tokens", 1500);

        // Enable JSON mode
        ObjectNode responseFormat = requestBody.putObject("response_format");
        responseFormat.put("type", "json_object");

        ArrayNode messagesArray = requestBody.putArray("messages");

        ObjectNode systemMessage = messagesArray.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        ObjectNode userMessage = messagesArray.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        String jsonContent = responseJson.get("choices").get(0).get("message").get("content").asText();

        return objectMapper.readTree(jsonContent);
    }

    /**
     * Test connection to OpenAI API
     *
     * @return true if connection is successful
     */
    public boolean testConnection() {
        try {
            getEmbedding("test");
            return true;
        } catch (Exception e) {
            System.err.println("❌ OpenAI API connection test failed: " + e.getMessage());
            return false;
        }
    }
}
