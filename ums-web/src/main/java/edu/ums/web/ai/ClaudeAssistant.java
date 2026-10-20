package edu.ums.web.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Complexity: time O(m) per call, m = message size, plus one bounded HTTPS round trip (timeout from configuration);
// space O(m) — at most 9 short turns are ever sent.

/** Calls the Anthropic Messages API with java.net.http; no SDK dependency. The key is read from configuration only. */
@Component
public class ClaudeAssistant implements AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAssistant.class);
    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ClaudeAssistant(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public boolean available() {
        return props.apiKey() != null && !props.apiKey().isBlank();
    }

    @Override
    public String model() {
        return props.model();
    }

    @Override
    public String complete(String system, List<Turn> turns) {
        if (!available()) {
            throw new UmsException(ErrorCode.AI_UNAVAILABLE, "No AI key is configured.");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", props.model());
            body.put("max_tokens", props.maxTokens());
            body.put("system", system);
            ArrayNode messages = body.putArray("messages");
            for (Turn t : turns) {
                ObjectNode m = messages.addObject();
                m.put("role", t.role());
                m.put("content", t.text());
            }
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                    .header("content-type", "application/json")
                    .header("x-api-key", props.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Claude API answered HTTP {}", response.statusCode());
                throw new UmsException(ErrorCode.AI_UNAVAILABLE);
            }
            JsonNode root = mapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new UmsException(ErrorCode.AI_UNAVAILABLE, "The AI returned an empty answer.");
            }
            return text.toString().trim();
        } catch (IOException e) {
            throw new UmsException(ErrorCode.AI_UNAVAILABLE, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UmsException(ErrorCode.AI_UNAVAILABLE, null, e);
        }
    }
}
