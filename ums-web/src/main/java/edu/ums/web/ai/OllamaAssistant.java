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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Complexity: time O(m) per call, m = message size, plus one bounded local HTTP round trip; the availability probe is
// cached for 30 s so it costs O(1) amortised; space O(m) — at most 9 short turns are sent.

/**
 * Talks to a local Ollama server (default http://localhost:11434) with java.net.http. If no model name is configured the
 * first installed model whose name starts with "qwen" is used. Nothing leaves the machine.
 */
@Component
public class OllamaAssistant implements AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(OllamaAssistant.class);
    private static final long PROBE_TTL_MS = 30_000;
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private volatile Probe probe = new Probe(false, "", 0);

    private record Probe(boolean up, String model, long at) {
    }

    public OllamaAssistant(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public boolean available() {
        return currentProbe().up();
    }

    @Override
    public String model() {
        return currentProbe().model();
    }

    private Probe currentProbe() {
        Probe p = probe;
        if (System.currentTimeMillis() - p.at() < PROBE_TTL_MS) {
            return p;
        }
        Probe fresh = probeServer();
        probe = fresh; // immutable snapshot; a benign race at worst runs the probe twice
        return fresh;
    }

    private Probe probeServer() {
        long now = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/api/tags")).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new Probe(false, "", now);
            }
            String wanted = props.ollamaModel() == null ? "" : props.ollamaModel().trim();
            String pick = "";
            for (JsonNode m : mapper.readTree(response.body()).path("models")) {
                String n = m.path("name").asText();
                if (!wanted.isEmpty() ? n.equals(wanted) || n.startsWith(wanted + ":") : n.toLowerCase().startsWith("qwen")) {
                    pick = n;
                    break;
                }
            }
            return new Probe(!pick.isEmpty(), pick, now);
        } catch (IOException e) {
            return new Probe(false, "", now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Probe(false, "", now);
        }
    }

    private String base() {
        String u = props.ollamaUrl() == null || props.ollamaUrl().isBlank() ? "http://localhost:11434" : props.ollamaUrl().trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    @Override
    public String complete(String system, List<Turn> turns) {
        Probe p = currentProbe();
        if (!p.up()) {
            throw new UmsException(ErrorCode.AI_UNAVAILABLE, "The local Ollama model is not reachable.");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", p.model());
            body.put("stream", false);
            body.put("think", false); // newer Ollama: skip Qwen3 "thinking"; older versions ignore the field
            body.putObject("options").put("num_predict", props.maxTokens()).put("temperature", 0.2).put("num_ctx", 8192); // room for the schema prompt
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            for (Turn t : turns) {
                messages.addObject().put("role", t.role()).put("content", t.text());
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(10, props.ollamaTimeoutSeconds())))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Ollama answered HTTP {}", response.statusCode());
                throw new UmsException(ErrorCode.AI_UNAVAILABLE);
            }
            String text = THINK.matcher(mapper.readTree(response.body()).path("message").path("content").asText()).replaceAll("").trim();
            if (text.isEmpty()) {
                throw new UmsException(ErrorCode.AI_UNAVAILABLE, "The local model returned an empty answer.");
            }
            return text;
        } catch (IOException e) {
            probe = new Probe(false, "", System.currentTimeMillis());
            throw new UmsException(ErrorCode.AI_UNAVAILABLE, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UmsException(ErrorCode.AI_UNAVAILABLE, null, e);
        }
    }
}
