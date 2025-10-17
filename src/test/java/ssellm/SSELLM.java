package ssellm;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SSELLM extends Simulation {

    String api_key = System.getenv("api_key");
    Path ruta = Path.of("target/sse_chunks.txt");
    Path rutaRespuesta = Path.of("target/llm_response.txt");
    FeederBuilder<String> promptFeeder = csv("prompts.csv").circular();

    // Inicializar archivos al inicio de la simulación
    {
        try {
            Files.createDirectories(rutaRespuesta.getParent());
            // Limpiar el archivo de respuestas al inicio de cada ejecución
            Files.writeString(rutaRespuesta, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // Limpiar el archivo de chunks
            Files.writeString(ruta, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
                        final String[] chunkIdHolder = new String[1]; // Array para permitir modificación en lambda

                        // Obtener el contenido acumulado previo de la sesión
                        if (session.contains("llmResponse")) {
                            responseContent.append(session.getString("llmResponse"));
                        }

                        // Obtener el chunk ID previo si existe
                        if (session.contains("chunkId")) {
                            chunkIdHolder[0] = session.getString("chunkId");
                        }

                        messages.forEach(message -> {
                            String data = message.message();
                            if (data != null && !data.isEmpty() && !data.contains("[DONE]")) {
                                System.out.println("🔹 SSE chunk: " + data);

                                // Guarda chunk original en archivo
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

                                // Extraer contenido del chunk
                                try {
                                    JsonObject chunkJson = JsonParser.parseString(data).getAsJsonObject();
                                    if (chunkJson.has("data")) {
                                        String innerData = chunkJson.get("data").getAsString();
                                        JsonObject innerJson = JsonParser.parseString(innerData).getAsJsonObject();

                                        // Extraer el ID del chunk (solo la primera vez)
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

                        Session updatedSession = session.set("llmResponse", responseContent.toString());
                        // Solo actualizar chunkId en la sesión si se encontró uno
                        if (chunkIdHolder[0] != null) {
                            updatedSession = updatedSession.set("chunkId", chunkIdHolder[0]);
                        }

                        // Si está terminado, guardar la respuesta completa
                        if (done) {
                            String fullResponse = responseContent.toString();
                            System.out.println("\n📝 Complete LLM Response: " + fullResponse);

                            // Obtener información de la sesión
                            String sessionId = updatedSession.userId() + "-" + updatedSession.scenario();
                            String category = updatedSession.getString("category");
                            String prompt = updatedSession.getString("prompt");
                            String storedChunkId = updatedSession.contains("chunkId") ? updatedSession.getString("chunkId") : "N/A";

                            // Formatear respuesta con metadatos
                            StringBuilder formattedResponse = new StringBuilder();
                            formattedResponse.append("================================================================================\n");
                            formattedResponse.append("Session ID: ").append(sessionId).append("\n");
                            formattedResponse.append("Chunk ID: ").append(storedChunkId).append("\n");
                            formattedResponse.append("Category: ").append(category).append("\n");
                            formattedResponse.append("Prompt: ").append(prompt).append("\n");
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
            rampUsers(10).during(10), // Ajusta usuarios y duración según necesidad
            constantUsersPerSec(10).during(60)) // Mantener 10 usuarios por segundo durante 60 minuto,
            ).protocols(httpProtocol);
    }
}
