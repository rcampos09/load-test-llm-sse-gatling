package ssellm;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.time.Instant;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ssellm.models.ResponseMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class SSELLM extends Simulation {

    String api_key = System.getenv("api_key");
    Path ruta = Path.of("target/sse_chunks.txt");
    Path rutaRespuesta = Path.of("target/llm_response.txt");
    Path rutaMetadata = Path.of("target/responses_metadata.jsonl");
    FeederBuilder<String> promptFeeder = csv("prompts.csv").circular();

    // ObjectMapper for JSON serialization
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Test start time for phase detection
    long testStartTime = System.currentTimeMillis();
    long rampDuration = 10000; // 10 seconds ramp phase

    // Initialize files at simulation start
    {
        try {
            Files.createDirectories(rutaRespuesta.getParent());
            // Clear response file at start of each execution
            Files.writeString(rutaRespuesta, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // Clear chunks file
            Files.writeString(ruta, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // Clear metadata file (JSONL format)
            Files.writeString(rutaMetadata, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("❌ Error initializing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("https://api.openai.com/v1/chat")
            .sseUnmatchedInboundMessageBufferSize(100);

    ScenarioBuilder prompt = scenario("Scenario")
            .feed(promptFeeder)
            .exec(
                    sse("Connect to LLM - #{category}")
                            .post("/completions")
                            .header("Authorization", "Bearer " + api_key)
                            .header("Content-Type", "application/json")
                            .body(StringBody(
                                    "{\"model\": \"gpt-3.5-turbo\"," +
                                            "\"stream\":true," +
                                            "\"max_tokens\":#{max_tokens}," +
                                            "\"temperature\":#{temperature}," +
                                            "\"messages\":[{\"role\":\"user\",\"content\":\"#{prompt}\"}]}"))
                            .asJson())
            .asLongAs("#{stop.isUndefined()}").on(
                    sse.processUnmatchedMessages((messages, session) -> {
                        StringBuilder responseContent = new StringBuilder();
                        final String[] chunkIdHolder = new String[1]; // Array to allow modification in lambda
                        final int[] chunkCounter = new int[1]; // Counter for total chunks

                        // Initialize timing variables
                        long requestStartTime = session.contains("requestStartTime")
                        
                            ? session.getLong("requestStartTime")
                            : System.currentTimeMillis();

                        if (!session.contains("requestStartTime")) {
                            session = session.set("requestStartTime", requestStartTime);
                        }

                        // Track time to first token
                        Long ttft = session.contains("ttft") ? session.getLong("ttft") : null;

                        // Get previous accumulated content from session
                        if (session.contains("llmResponse")) {
                            responseContent.append(session.getString("llmResponse"));
                        }

                        // Get previous chunk ID if exists
                        if (session.contains("chunkId")) {
                            chunkIdHolder[0] = session.getString("chunkId");
                        }

                        // Get previous chunk counter
                        if (session.contains("chunkCount")) {
                            chunkCounter[0] = session.getInt("chunkCount");
                        }

                        final long[] ttftHolder = new long[1];
                        if (ttft != null) {
                            ttftHolder[0] = ttft;
                        }

                        messages.forEach(message -> {
                            String data = message.message();
                            if (data != null && !data.isEmpty() && !data.contains("[DONE]")) {
                                System.out.println("🔹 SSE chunk: " + data);
                                chunkCounter[0]++; // Increment chunk counter

                                // Calculate TTFT on first content chunk
                                if (ttftHolder[0] == 0) {
                                    ttftHolder[0] = System.currentTimeMillis() - requestStartTime;
                                }

                                // Save original chunk to file
                                try {
                                    Files.createDirectories(ruta.getParent());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                try {
                                    Files.writeString(ruta, "🔹 SSE chunk: " + data + System.lineSeparator(),
                                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                // Extract content from chunk
                                try {
                                    JsonObject chunkJson = JsonParser.parseString(data).getAsJsonObject();
                                    if (chunkJson.has("data")) {
                                        String innerData = chunkJson.get("data").getAsString();
                                        JsonObject innerJson = JsonParser.parseString(innerData).getAsJsonObject();

                                        // Extract chunk ID (only first time)
                                        if (innerJson.has("id") && chunkIdHolder[0] == null) {
                                            chunkIdHolder[0] = innerJson.get("id").getAsString();
                                        }

                                        if (innerJson.has("choices") && innerJson.getAsJsonArray("choices").size() > 0) {
                                            JsonObject choice = innerJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                                            if (choice.has("delta")) {
                                                JsonObject delta = choice.getAsJsonObject("delta");
                                                if (delta.has("content")) {
                                                    String content = delta.get("content").getAsString();
                                                    responseContent.append(content);
                                                    System.out.println("✅ Content extracted: " + content);
                                                }
                                            }
                                        }
                                    } else {
                                        System.out.println("⚠️ No 'data' field in chunk");
                                    }
                                } catch (Exception e) {
                                    System.err.println("❌ Error parsing chunk: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        });

                        boolean done = messages.stream()
                                .anyMatch(m -> m.message().contains("[DONE]"));

                        Session updatedSession = session
                            .set("llmResponse", responseContent.toString())
                            .set("chunkCount", chunkCounter[0])
                            .set("ttft", ttftHolder[0]);

                        // Only update chunkId in session if one was found
                        if (chunkIdHolder[0] != null) {
                            updatedSession = updatedSession.set("chunkId", chunkIdHolder[0]);
                        }

                        // Timeout detection (max 10 seconds per request)
                        long currentTime = System.currentTimeMillis();
                        long elapsed = currentTime - requestStartTime;
                        boolean timedOut = elapsed > 10000;

                        // If done or timeout, save complete response
                        if (done || timedOut) {
                            String fullResponse = responseContent.toString();
                            long responseTimeMs = currentTime - requestStartTime;

                            System.out.println("\n📝 Complete LLM Response: " + fullResponse);

                            // Get information from session
                            String sessionId = updatedSession.userId() + "-" + updatedSession.scenario();
                            String category = updatedSession.getString("category");
                            String prompt = updatedSession.getString("prompt");
                            int maxTokens = Integer.parseInt(updatedSession.getString("max_tokens"));
                            double temperature = Double.parseDouble(updatedSession.getString("temperature"));
                            String storedChunkId = updatedSession.contains("chunkId") ? updatedSession.getString("chunkId") : "N/A";

                            // Determine test phase (RAMP or STEADY)
                            long timeSinceTestStart = currentTime - testStartTime;
                            String testPhase = timeSinceTestStart < rampDuration ? "RAMP" : "STEADY";

                            // Detect truncation
                            boolean truncated = timedOut || !done;
                            String truncationReason = "NONE";
                            if (timedOut) {
                                truncationReason = "TIMEOUT";
                            }

                            // Build ResponseMetadata object
                            ResponseMetadata metadata = ResponseMetadata.builder()
                                .sessionId(sessionId)
                                .chunkId(storedChunkId)
                                .userId(updatedSession.userId())
                                .category(category)
                                .prompt(prompt)
                                .maxTokens(maxTokens)
                                .temperature(temperature)
                                .response(fullResponse)
                                .timestamp(Instant.now())
                                .responseTimeMs(responseTimeMs)
                                .ttftMs(ttftHolder[0])
                                .totalChunks(chunkCounter[0])
                                .truncated(truncated)
                                .truncationReason(truncationReason)
                                .testPhase(testPhase)
                                .build();

                            // Save structured metadata as JSONL
                            try {
                                String jsonLine = objectMapper.writeValueAsString(metadata);
                                Files.writeString(rutaMetadata, jsonLine + System.lineSeparator(),
                                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                System.out.println("💾 Metadata saved: " + metadata);
                            } catch (IOException e) {
                                System.err.println("❌ Error saving metadata: " + e.getMessage());
                                e.printStackTrace();
                            }

                            // Format response with metadata (legacy format)
                            StringBuilder formattedResponse = new StringBuilder();
                            formattedResponse.append("================================================================================\n");
                            formattedResponse.append("Session ID: ").append(sessionId).append("\n");
                            formattedResponse.append("Chunk ID: ").append(storedChunkId).append("\n");
                            formattedResponse.append("Timestamp: ").append(Instant.now()).append("\n");
                            formattedResponse.append("Category: ").append(category).append("\n");
                            formattedResponse.append("Prompt: ").append(prompt).append("\n");
                            formattedResponse.append("Response Time (ms): ").append(responseTimeMs).append("\n");
                            formattedResponse.append("TTFT (ms): ").append(ttftHolder[0]).append("\n");
                            formattedResponse.append("Total Chunks: ").append(chunkCounter[0]).append("\n");
                            formattedResponse.append("Test Phase: ").append(testPhase).append("\n");
                            formattedResponse.append("Truncated: ").append(truncated).append("\n");
                            if (truncated) {
                                formattedResponse.append("Truncation Reason: ").append(truncationReason).append("\n");
                            }
                            formattedResponse.append("--------------------------------------------------------------------------------\n");
                            formattedResponse.append("Response: ").append(fullResponse).append("\n");
                            formattedResponse.append("================================================================================\n\n");

                            try {
                                Files.createDirectories(rutaRespuesta.getParent());
                                Files.writeString(rutaRespuesta, formattedResponse.toString(),
                                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                System.out.println("💾 Response saved to: " + rutaRespuesta);
                            } catch (IOException e) {
                                System.err.println("❌ Error saving response: " + e.getMessage());
                                e.printStackTrace();
                            }

                            return updatedSession.set("stop", true);
                        }

                        return updatedSession;
                    }))
            .exec(sse("close").close());

    {
        setUp(prompt.injectOpen(
            rampUsers(10).during(10), // Ramp up to 10 users over 10 seconds
            constantUsersPerSec(10).during(60)) // Maintain 10 users per second for 60 seconds
            ).protocols(httpProtocol);
    }
}
