package com.afterme.backend.ai.client;

import com.afterme.backend.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 호출. Structured Outputs(json_schema, strict)로 응답 형식을 강제한다.
 * 프롬프트·응답 원문은 로그에 남기지 않는다.
 */
@Component
public class OpenAiClient {

    public static final String PROVIDER = "openai";
    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final AiProperties.OpenAi props;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.shared();

    /** @param content 스키마를 따르는 응답 JSON, @param model 실제 응답한 모델명 */
    public record Result(JsonNode content, String model) {
    }

    public OpenAiClient(AiProperties properties) {
        this.props = properties.openai();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(props.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public Result request(String systemPrompt, String userPrompt, String schemaName, JsonNode schema) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new AiCallException("AI_NOT_CONFIGURED");
        }
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", schemaName, "strict", true, "schema", schema)
                )
        );
        long startedAt = System.currentTimeMillis();
        String raw = call(body);
        return parse(raw, schemaName, System.currentTimeMillis() - startedAt);
    }

    private String call(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("OpenAI 호출 실패 status={}", status);
            throw new AiCallException(status == 429 ? "AI_RATE_LIMITED" : "AI_HTTP_" + status, e);
        } catch (ResourceAccessException e) {
            String code = e.getCause() instanceof SocketTimeoutException ? "AI_TIMEOUT" : "AI_NETWORK_ERROR";
            log.warn("OpenAI 연결 실패 code={}", code);
            throw new AiCallException(code, e);
        }
    }

    private Result parse(String raw, String schemaName, long elapsedMs) {
        try {
            JsonNode root = jsonMapper.readTree(raw);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String model = text(root.path("model"));
            log.info("OpenAI 호출 완료 schema={} model={} elapsedMs={} totalTokens={}",
                    schemaName, model, elapsedMs, root.path("usage").path("total_tokens").asInt(0));

            if (message.hasNonNull("refusal")) throw new AiCallException("AI_REFUSED");
            if ("length".equals(text(choice.path("finish_reason")))) throw new AiCallException("AI_OUTPUT_TRUNCATED");

            String content = text(message.path("content"));
            if (content == null) throw new AiCallException("AI_OUTPUT_INVALID");
            return new Result(jsonMapper.readTree(content), model != null ? model : props.model());
        } catch (JacksonException e) {
            throw new AiCallException("AI_OUTPUT_INVALID", e);
        }
    }

    private String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }
}