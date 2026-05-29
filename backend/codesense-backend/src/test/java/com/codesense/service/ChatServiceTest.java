package com.codesense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codesense.dto.ChatMessageDto;
import com.codesense.exception.ChatException;
import com.codesense.exception.ChatException.Reason;
import com.codesense.model.ChatMessage;
import com.codesense.model.Review;
import com.codesense.model.User;
import com.codesense.repository.ChatMessageRepository;
import com.codesense.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ChatServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ReviewRepository reviewRepository;
  private ChatMessageRepository chatMessageRepository;
  private StorageService storageService;

  private UUID reviewId;
  private UUID userId;
  private Review review;

  @BeforeEach
  void setUp() {
    reviewRepository = mock(ReviewRepository.class);
    chatMessageRepository = mock(ChatMessageRepository.class);
    storageService = mock(StorageService.class);

    reviewId = UUID.randomUUID();
    userId = UUID.randomUUID();
    User user = User.builder().build();
    user.setId(userId);
    review =
        Review.builder()
            .user(user)
            .blobKey("blob-123")
            .summary("Looks decent.")
            .submissionType("paste")
            .build();
    review.setId(reviewId);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
    when(chatMessageRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)).thenReturn(List.of());
    when(storageService.fetch("blob-123")).thenReturn("System.out.println(\"hi\");");
  }

  private ChatService buildService(ExchangeFunction exchange) {
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    return new ChatService(
        builder,
        MAPPER,
        reviewRepository,
        chatMessageRepository,
        storageService,
        "https://api.anthropic.com/v1/messages",
        "test-api-key",
        "claude-test-model",
        4096,
        0L);
  }

  private static ClientResponse sse(String body) {
    return ClientResponse.create(HttpStatus.OK)
        .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
        .body(body)
        .build();
  }

  private static ClientResponse status(HttpStatus code) {
    return ClientResponse.create(code).body("").build();
  }

  /** Two text deltas plus non-text events that must be filtered out. */
  private static final String STREAM_BODY =
      "data: {\"type\":\"message_start\"}\n\n"
          + "data: {\"type\":\"content_block_start\",\"index\":0}\n\n"
          + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n"
          + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}\n\n"
          + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
          + "data: {\"type\":\"message_stop\"}\n\n";

  @Test
  void streamsTextDeltasAndPersistsUserAndAssistantMessages() {
    ChatService svc = buildService(req -> Mono.just(sse(STREAM_BODY)));

    List<String> tokens = svc.streamReply(review, "What is the bug?").collectList().block();

    assertThat(tokens).containsExactly("Hello", " world");

    ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(chatMessageRepository, times(2)).save(captor.capture());
    List<ChatMessage> saved = captor.getAllValues();
    assertThat(saved.get(0).getRole()).isEqualTo("user");
    assertThat(saved.get(0).getContent()).isEqualTo("What is the bug?");
    assertThat(saved.get(1).getRole()).isEqualTo("assistant");
    assertThat(saved.get(1).getContent()).isEqualTo("Hello world");
  }

  @Test
  void includesOriginalCodeFromBlobInContext() {
    ChatService svc = buildService(req -> Mono.just(sse(STREAM_BODY)));

    svc.streamReply(review, "explain").collectList().block();

    verify(storageService).fetch("blob-123");
  }

  @Test
  void missingApiKeyYieldsAuthErrorAndDoesNotPersist() {
    WebClient.Builder builder =
        WebClient.builder().exchangeFunction(req -> Mono.just(sse(STREAM_BODY)));
    ChatService svc =
        new ChatService(
            builder,
            MAPPER,
            reviewRepository,
            chatMessageRepository,
            storageService,
            "https://api.anthropic.com/v1/messages",
            "",
            "claude-test-model",
            4096,
            0L);

    assertThatThrownBy(() -> svc.streamReply(review, "hi").collectList().block())
        .isInstanceOf(ChatException.class)
        .extracting("reason")
        .isEqualTo(Reason.AUTH_ERROR);

    verify(chatMessageRepository, never()).save(any());
  }

  @Test
  void rateLimitMapsToRetryableReason() {
    ChatService svc = buildService(req -> Mono.just(status(HttpStatus.TOO_MANY_REQUESTS)));

    assertThatThrownBy(() -> svc.streamReply(review, "hi").collectList().block())
        .isInstanceOf(ChatException.class)
        .extracting("reason")
        .isEqualTo(Reason.RATE_LIMITED);
  }

  @Test
  void requireOwnedReviewRejectsForeignUser() {
    ChatService svc = buildService(req -> Mono.just(sse(STREAM_BODY)));

    assertThatThrownBy(
            () -> svc.requireOwnedReview(reviewId.toString(), UUID.randomUUID().toString()))
        .isInstanceOf(ChatException.class)
        .extracting("reason")
        .isEqualTo(Reason.FORBIDDEN);
  }

  @Test
  void requireOwnedReviewMissingReviewIsNotFound() {
    ChatService svc = buildService(req -> Mono.just(sse(STREAM_BODY)));
    UUID unknown = UUID.randomUUID();
    when(reviewRepository.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> svc.requireOwnedReview(unknown.toString(), userId.toString()))
        .isInstanceOf(ChatException.class)
        .extracting("reason")
        .isEqualTo(Reason.NOT_FOUND);
  }

  @Test
  void historyMapsPersistedMessages() {
    ChatService svc = buildService(req -> Mono.just(sse(STREAM_BODY)));
    when(chatMessageRepository.findByReviewIdOrderByCreatedAtAsc(reviewId))
        .thenReturn(
            List.of(
                ChatMessage.builder().role("user").content("q1").build(),
                ChatMessage.builder().role("assistant").content("a1").build()));

    List<ChatMessageDto> history = svc.history(review);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).role()).isEqualTo("user");
    assertThat(history.get(1).content()).isEqualTo("a1");
  }
}
