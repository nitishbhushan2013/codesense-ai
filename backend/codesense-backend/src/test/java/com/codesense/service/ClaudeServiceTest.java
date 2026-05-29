package com.codesense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codesense.dto.ClaudeReviewResult;
import com.codesense.exception.ClaudeApiException;
import com.codesense.exception.ClaudeApiException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ClaudeServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String VALID_INNER_JSON =
      """
      {
        "score": 75,
        "summary": "Code is generally well-structured but has a few concerns.",
        "findings": [
          {
            "category": "security",
            "severity": "warning",
            "lineReference": "Line 12",
            "description": "User input concatenated into SQL query.",
            "suggestedFix": "Use a parameterised query."
          }
        ]
      }
      """;

  private ClaudeService buildService(ExchangeFunction exchange) {
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    return new ClaudeService(
        builder,
        MAPPER,
        "https://api.anthropic.com/v1/messages",
        "test-api-key",
        "claude-sonnet-4-20250514",
        4096,
        0L);
  }

  private static String envelope(String innerText) {
    return """
        {
          "id": "msg_test",
          "type": "message",
          "role": "assistant",
          "content": [
            {"type": "text", "text": %s}
          ],
          "stop_reason": "end_turn"
        }
        """
        .formatted(MAPPER.valueToTree(innerText).toString());
  }

  private static ClientResponse jsonOk(String body) {
    return ClientResponse.create(HttpStatus.OK)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .build();
  }

  private static ClientResponse status(HttpStatus code) {
    return ClientResponse.create(code).body("").build();
  }

  @Test
  void parsesValidResponse() {
    ClaudeService svc = buildService(req -> Mono.just(jsonOk(envelope(VALID_INNER_JSON))));

    ClaudeReviewResult review = svc.analyzeCode("System.out.println(\"hi\");", "Java");

    assertThat(review.score()).isEqualTo(75);
    assertThat(review.summary()).contains("well-structured");
    assertThat(review.findings()).hasSize(1);
    assertThat(review.findings().get(0).category()).isEqualTo("security");
    assertThat(review.findings().get(0).severity()).isEqualTo("warning");
  }

  @Test
  void parsesResponseWrappedInMarkdownFences() {
    String fenced = "```json\n" + VALID_INNER_JSON.trim() + "\n```";
    ClaudeService svc = buildService(req -> Mono.just(jsonOk(envelope(fenced))));

    ClaudeReviewResult review = svc.analyzeCode("code", "Java");

    assertThat(review.score()).isEqualTo(75);
    assertThat(review.findings()).hasSize(1);
  }

  @Test
  void retriesOnRateLimitThenSucceeds() {
    AtomicInteger calls = new AtomicInteger();
    ExchangeFunction exchange =
        req -> {
          int n = calls.incrementAndGet();
          if (n <= 2) {
            return Mono.just(status(HttpStatus.TOO_MANY_REQUESTS));
          }
          return Mono.just(jsonOk(envelope(VALID_INNER_JSON)));
        };

    ClaudeService svc = buildService(exchange);
    ClaudeReviewResult review = svc.analyzeCode("code", "Java");

    assertThat(calls.get()).isEqualTo(3);
    assertThat(review.score()).isEqualTo(75);
  }

  @Test
  void givesUpAfterMaxRetries() {
    AtomicInteger calls = new AtomicInteger();
    ExchangeFunction exchange =
        req -> {
          calls.incrementAndGet();
          return Mono.just(status(HttpStatus.TOO_MANY_REQUESTS));
        };
    ClaudeService svc = buildService(exchange);

    assertThatThrownBy(() -> svc.analyzeCode("code", "Java"))
        .isInstanceOf(ClaudeApiException.class)
        .extracting("reason")
        .isEqualTo(Reason.RATE_LIMITED);
    assertThat(calls.get()).isEqualTo(3); // 1 initial + 2 retries
  }

  @Test
  void doesNotRetryOnAuthError() {
    AtomicInteger calls = new AtomicInteger();
    ExchangeFunction exchange =
        req -> {
          calls.incrementAndGet();
          return Mono.just(status(HttpStatus.UNAUTHORIZED));
        };
    ClaudeService svc = buildService(exchange);

    assertThatThrownBy(() -> svc.analyzeCode("code", "Java"))
        .isInstanceOf(ClaudeApiException.class)
        .extracting("reason")
        .isEqualTo(Reason.AUTH_ERROR);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void throwsParseErrorWhenInnerJsonIsInvalid() {
    ClaudeService svc = buildService(req -> Mono.just(jsonOk(envelope("this is not JSON at all"))));

    assertThatThrownBy(() -> svc.analyzeCode("code", "Java"))
        .isInstanceOf(ClaudeApiException.class)
        .extracting("reason")
        .isEqualTo(Reason.PARSE_ERROR);
  }

  @Test
  void throwsAuthErrorWhenApiKeyMissing() {
    WebClient.Builder builder =
        WebClient.builder().exchangeFunction(req -> Mono.just(jsonOk(envelope(VALID_INNER_JSON))));
    ClaudeService svc =
        new ClaudeService(
            builder,
            MAPPER,
            "https://api.anthropic.com/v1/messages",
            "", // blank key
            "claude-sonnet-4-20250514",
            4096,
            0L);

    assertThatThrownBy(() -> svc.analyzeCode("code", "Java"))
        .isInstanceOf(ClaudeApiException.class)
        .extracting("reason")
        .isEqualTo(Reason.AUTH_ERROR);
  }

  // Unused helper kept for clarity in case future tests want to inspect the request body
  @SuppressWarnings("unused")
  private static Function<ClientRequest, ClientRequest> identity() {
    return Function.identity();
  }
}
