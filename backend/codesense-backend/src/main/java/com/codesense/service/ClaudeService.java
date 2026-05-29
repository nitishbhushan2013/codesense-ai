package com.codesense.service;

import com.codesense.dto.ReviewResponse;
import com.codesense.exception.ClaudeApiException;
import com.codesense.exception.ClaudeApiException.Reason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@Slf4j
public class ClaudeService {

  private static final int MAX_RETRIES = 2;

  private static final String SYSTEM_PROMPT =
      """
      You are an expert code reviewer with deep knowledge of software \
      engineering best practices, security vulnerabilities, and performance \
      optimisation.

      Analyse the provided code and return ONLY a valid JSON object with \
      this exact structure — no preamble, no markdown, just the JSON:

      {
        "score": <integer 0-100>,
        "summary": "<2-3 sentence overall assessment>",
        "findings": [
          {
            "category": "<bug|security|performance|quality>",
            "severity": "<critical|warning|info>",
            "lineReference": "<e.g. Line 23 or Lines 45-52>",
            "description": "<clear explanation of the issue>",
            "suggestedFix": "<corrected code snippet>"
          }
        ]
      }
      """;

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;
  private final int maxTokens;
  private final long retryBackoffMs;

  public ClaudeService(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      @Value("${claude.api.url}") String url,
      @Value("${claude.api.key:}") String apiKey,
      @Value("${claude.api.model}") String model,
      @Value("${claude.api.max-tokens:4096}") int maxTokens,
      @Value("${claude.api.retry-backoff-ms:500}") long retryBackoffMs) {
    this.webClient = webClientBuilder.baseUrl(url).build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.model = model;
    this.maxTokens = maxTokens;
    this.retryBackoffMs = retryBackoffMs;
  }

  public ReviewResponse analyzeCode(String code, String language) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new ClaudeApiException(
          Reason.AUTH_ERROR, "Claude API key is not configured — set CLAUDE_API_KEY env var");
    }

    String userPrompt = buildUserPrompt(code, language);
    Map<String, Object> body = buildRequestBody(userPrompt);

    ClaudeApiException lastError = null;
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        String responseText = callClaude(body);
        return parseReview(responseText);
      } catch (ClaudeApiException e) {
        lastError = e;
        if (!e.getReason().isRetryable() || attempt == MAX_RETRIES) {
          throw e;
        }
        log.warn(
            "Claude call failed ({}), retry {}/{}: {}",
            e.getReason(),
            attempt + 1,
            MAX_RETRIES,
            e.getMessage());
        sleepBackoff(attempt);
      }
    }
    throw lastError;
  }

  private String buildUserPrompt(String code, String language) {
    String langLabel = (language == null || language.isBlank()) ? "code" : language + " code";
    return "Review the following " + langLabel + ":\n\n" + code;
  }

  private Map<String, Object> buildRequestBody(String userPrompt) {
    return Map.of(
        "model", model,
        "max_tokens", maxTokens,
        "system", SYSTEM_PROMPT,
        "messages", List.of(Map.of("role", "user", "content", userPrompt)));
  }

  private String callClaude(Map<String, Object> body) {
    try {
      String response =
          webClient
              .post()
              .headers(this::applyHeaders)
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(body)
              .retrieve()
              .bodyToMono(String.class)
              .block();
      return extractText(response);
    } catch (WebClientResponseException e) {
      throw mapHttpError(e);
    } catch (ClaudeApiException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ClaudeApiException(
          Reason.UNAVAILABLE, "Failed to contact Claude API: " + e.getMessage(), e);
    }
  }

  private void applyHeaders(HttpHeaders headers) {
    headers.set("x-api-key", apiKey);
    headers.set("anthropic-version", "2023-06-01");
  }

  private String extractText(String rawResponse) {
    try {
      JsonNode root = objectMapper.readTree(rawResponse);
      JsonNode content = root.path("content");
      if (!content.isArray() || content.isEmpty()) {
        throw new ClaudeApiException(Reason.PARSE_ERROR, "Claude response had no content array");
      }
      String text = content.get(0).path("text").asText("");
      if (text.isBlank()) {
        throw new ClaudeApiException(Reason.PARSE_ERROR, "Claude response text was empty");
      }
      return text;
    } catch (JsonProcessingException e) {
      throw new ClaudeApiException(
          Reason.PARSE_ERROR, "Failed to parse Claude API envelope: " + e.getMessage(), e);
    }
  }

  private ReviewResponse parseReview(String text) {
    String cleaned = stripCodeFences(text).trim();
    try {
      return objectMapper.readValue(cleaned, ReviewResponse.class);
    } catch (JsonProcessingException e) {
      throw new ClaudeApiException(
          Reason.PARSE_ERROR,
          "Claude did not return valid review JSON: " + e.getOriginalMessage(),
          e);
    }
  }

  private String stripCodeFences(String text) {
    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      if (firstNewline > 0) {
        trimmed = trimmed.substring(firstNewline + 1);
      }
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length() - 3);
      }
    }
    return trimmed;
  }

  private ClaudeApiException mapHttpError(WebClientResponseException e) {
    int status = e.getStatusCode().value();
    if (status == 401 || status == 403) {
      return new ClaudeApiException(Reason.AUTH_ERROR, "Claude API auth failed (" + status + ")");
    }
    if (status == 429) {
      return new ClaudeApiException(Reason.RATE_LIMITED, "Claude API rate limited");
    }
    if (status >= 500) {
      return new ClaudeApiException(Reason.SERVER_ERROR, "Claude API server error: " + status);
    }
    return new ClaudeApiException(
        Reason.BAD_REQUEST,
        "Claude API rejected request (" + status + "): " + e.getResponseBodyAsString());
  }

  private void sleepBackoff(int attempt) {
    if (retryBackoffMs <= 0) {
      return;
    }
    try {
      Thread.sleep((long) (retryBackoffMs * Math.pow(2, attempt)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
