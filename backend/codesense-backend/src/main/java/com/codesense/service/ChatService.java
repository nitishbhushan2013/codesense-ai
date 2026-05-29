package com.codesense.service;

import com.codesense.dto.ChatMessageDto;
import com.codesense.exception.ChatException;
import com.codesense.exception.ChatException.Reason;
import com.codesense.model.ChatMessage;
import com.codesense.model.Review;
import com.codesense.repository.ChatMessageRepository;
import com.codesense.repository.ReviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * Follow-up chat against a persisted review. Streams Claude tokens via SSE and persists both the
 * user message and the assembled assistant reply. Mirrors the WebClient + typed-Reason + retry
 * pattern used by {@link ClaudeService}.
 */
@Service
@Slf4j
public class ChatService {

  private static final int MAX_RETRIES = 2;

  private static final String SYSTEM_PROMPT =
      """
      You are an expert code reviewer helping a developer understand and act on \
      a code review you previously produced. Answer follow-up questions about the \
      reviewed code, the findings, and how to fix them. Be concise, concrete, and \
      reference specific lines or snippets when helpful. The original reviewed code \
      is provided below as context.
      """;

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final ReviewRepository reviewRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final StorageService storageService;
  private final String apiKey;
  private final String model;
  private final int maxTokens;
  private final long retryBackoffMs;

  public ChatService(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      ReviewRepository reviewRepository,
      ChatMessageRepository chatMessageRepository,
      StorageService storageService,
      @Value("${claude.api.url}") String url,
      @Value("${claude.api.key:}") String apiKey,
      @Value("${claude.api.model}") String model,
      @Value("${claude.api.max-tokens:4096}") int maxTokens,
      @Value("${claude.api.retry-backoff-ms:500}") long retryBackoffMs) {
    this.webClient = webClientBuilder.baseUrl(url).build();
    this.objectMapper = objectMapper;
    this.reviewRepository = reviewRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.storageService = storageService;
    this.apiKey = apiKey;
    this.model = model;
    this.maxTokens = maxTokens;
    this.retryBackoffMs = retryBackoffMs;
  }

  /**
   * Loads a review and verifies it belongs to {@code userId}. Throws {@link ChatException} with a
   * mappable reason if missing or not owned, so the controller can translate it like any other
   * failure mode.
   */
  public Review requireOwnedReview(String reviewId, String userId) {
    UUID id;
    try {
      id = UUID.fromString(reviewId);
    } catch (IllegalArgumentException e) {
      throw new ChatException(Reason.BAD_REQUEST, "Invalid review id: " + reviewId);
    }
    Review review =
        reviewRepository
            .findById(id)
            .orElseThrow(
                () -> new ChatException(Reason.NOT_FOUND, "Review not found: " + reviewId));
    if (review.getUser() == null || !review.getUser().getId().toString().equals(userId)) {
      throw new ChatException(Reason.FORBIDDEN, "Review does not belong to the current user");
    }
    return review;
  }

  /** Returns the persisted chat history for a review, oldest first. */
  public List<ChatMessageDto> history(Review review) {
    return chatMessageRepository.findByReviewIdOrderByCreatedAtAsc(review.getId()).stream()
        .map(m -> new ChatMessageDto(m.getRole(), m.getContent(), m.getCreatedAt()))
        .toList();
  }

  /**
   * Persists the user's message, then streams the assistant reply token-by-token. The accumulated
   * reply is persisted once the stream completes. Returns a {@link Flux} of text deltas suitable
   * for forwarding as SSE.
   */
  public Flux<String> streamReply(Review review, String userMessage) {
    if (apiKey == null || apiKey.isBlank()) {
      return Flux.error(
          new ChatException(
              Reason.AUTH_ERROR, "Claude API key is not configured — set CLAUDE_API_KEY env var"));
    }

    chatMessageRepository.save(
        ChatMessage.builder().review(review).role("user").content(userMessage).build());

    Map<String, Object> body = buildRequestBody(review, userMessage);
    StringBuilder assembled = new StringBuilder();

    return webClient
        .post()
        .headers(this::applyHeaders)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)
        // Retry connection/transport failures before any tokens flow. Retryable failures
        // (rate-limit, 5xx, transport) surface at connection time, so nothing has been
        // appended yet; reset the buffer on each attempt to stay safe.
        .doOnSubscribe(s -> assembled.setLength(0))
        .retryWhen(retrySpec())
        .mapNotNull(this::extractDelta)
        .doOnNext(assembled::append)
        .onErrorMap(this::mapStreamError)
        .doOnComplete(() -> persistAssistant(review, assembled.toString()));
  }

  private void persistAssistant(Review review, String content) {
    if (content == null || content.isBlank()) {
      log.warn(
          "Chat stream for review {} produced no assistant text; skipping persist", review.getId());
      return;
    }
    chatMessageRepository.save(
        ChatMessage.builder().review(review).role("assistant").content(content).build());
    log.debug(
        "Persisted assistant reply ({} chars) for review {}", content.length(), review.getId());
  }

  private Map<String, Object> buildRequestBody(Review review, String userMessage) {
    List<Map<String, Object>> messages = new ArrayList<>();

    String code = loadCode(review);
    String contextSystem =
        SYSTEM_PROMPT
            + "\n\nReview summary: "
            + (review.getSummary() == null ? "(none)" : review.getSummary())
            + "\n\nOriginal reviewed code:\n\n"
            + code;

    for (ChatMessage prior :
        chatMessageRepository.findByReviewIdOrderByCreatedAtAsc(review.getId())) {
      messages.add(Map.of("role", prior.getRole(), "content", prior.getContent()));
    }

    return Map.of(
        "model", model,
        "max_tokens", maxTokens,
        "stream", true,
        "system", contextSystem,
        "messages", messages);
  }

  private String loadCode(Review review) {
    if (review.getBlobKey() == null || review.getBlobKey().isBlank()) {
      return "(original code unavailable)";
    }
    try {
      return storageService.fetch(review.getBlobKey());
    } catch (RuntimeException e) {
      log.warn("Could not load blob {} for chat context: {}", review.getBlobKey(), e.getMessage());
      return "(original code unavailable)";
    }
  }

  /**
   * Extracts the text fragment from one Anthropic SSE {@code data:} payload. Non-text events
   * (message_start, ping, content_block_start/stop, message_delta/stop) yield {@code null} and are
   * filtered out.
   */
  private String extractDelta(String data) {
    if (data == null || data.isBlank() || "[DONE]".equals(data.trim())) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(data);
      if (!"content_block_delta".equals(node.path("type").asText())) {
        return null;
      }
      JsonNode delta = node.path("delta");
      if (!"text_delta".equals(delta.path("type").asText())) {
        return null;
      }
      String text = delta.path("text").asText("");
      return text.isEmpty() ? null : text;
    } catch (Exception e) {
      // A malformed fragment should not kill the stream; skip it.
      log.debug("Skipping unparseable SSE fragment: {}", e.getMessage());
      return null;
    }
  }

  private void applyHeaders(HttpHeaders headers) {
    headers.set("x-api-key", apiKey);
    headers.set("anthropic-version", "2023-06-01");
  }

  private Retry retrySpec() {
    return Retry.backoff(MAX_RETRIES, Duration.ofMillis(Math.max(retryBackoffMs, 1)))
        .filter(t -> reasonOf(t).isRetryable())
        // Surface the original failure (not Reactor's RetryExhaustedException) once retries run
        // out.
        .onRetryExhaustedThrow((spec, signal) -> signal.failure());
  }

  private Reason reasonOf(Throwable t) {
    if (t instanceof ChatException ce) {
      return ce.getReason();
    }
    if (t instanceof WebClientResponseException w) {
      return mapStatus(w.getStatusCode().value());
    }
    return Reason.UNAVAILABLE;
  }

  private ChatException mapStreamError(Throwable t) {
    if (t instanceof ChatException ce) {
      return ce;
    }
    if (t instanceof WebClientResponseException w) {
      int status = w.getStatusCode().value();
      return new ChatException(mapStatus(status), "Claude API error (" + status + ")", t);
    }
    return new ChatException(
        Reason.UNAVAILABLE, "Failed to contact Claude API: " + t.getMessage(), t);
  }

  private Reason mapStatus(int status) {
    if (status == 401 || status == 403) {
      return Reason.AUTH_ERROR;
    }
    if (status == 429) {
      return Reason.RATE_LIMITED;
    }
    if (status >= 500) {
      return Reason.SERVER_ERROR;
    }
    return Reason.BAD_REQUEST;
  }
}
