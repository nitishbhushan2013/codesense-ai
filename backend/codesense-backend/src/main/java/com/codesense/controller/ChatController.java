package com.codesense.controller;

import com.codesense.dto.ChatMessageDto;
import com.codesense.dto.ChatRequest;
import com.codesense.exception.ChatException;
import com.codesense.model.Review;
import com.codesense.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/reviews/{id}/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@Slf4j
public class ChatController {

  private final ChatService chatService;

  /**
   * Streams a follow-up answer token-by-token as SSE. Each token is one {@code message} event; a
   * terminal {@code done} event signals completion, and failures become an {@code error} event so
   * the client always sees a clean stream end. The review must belong to the requesting user.
   */
  @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> chat(
      @PathVariable("id") String reviewId,
      @Valid @RequestBody ChatRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails != null ? userDetails.getUsername() : null;
    Review review;
    try {
      review = chatService.requireOwnedReview(reviewId, userId);
    } catch (ChatException e) {
      // Ownership / lookup failures happen before streaming; surface as a single error event.
      log.warn("Chat access denied: {} - {}", e.getReason(), e.getMessage());
      return Flux.just(errorEvent(e.getMessage()));
    }

    return chatService
        .streamReply(review, request.message())
        .map(token -> ServerSentEvent.builder(token).event("message").build())
        .concatWith(Flux.just(ServerSentEvent.<String>builder("").event("done").build()))
        .onErrorResume(
            ChatException.class,
            e -> {
              log.warn("Chat stream failed: {} - {}", e.getReason(), e.getMessage());
              return Flux.just(errorEvent(e.getMessage()));
            });
  }

  /** Returns the persisted chat history for a review, oldest first. Owner-only. */
  @GetMapping
  public ResponseEntity<?> history(
      @PathVariable("id") String reviewId, @AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails != null ? userDetails.getUsername() : null;
    try {
      Review review = chatService.requireOwnedReview(reviewId, userId);
      List<ChatMessageDto> messages = chatService.history(review);
      return ResponseEntity.ok(messages);
    } catch (ChatException e) {
      log.warn("Chat history denied: {} - {}", e.getReason(), e.getMessage());
      return ResponseEntity.status(mapChatStatus(e)).body(Map.of("message", e.getMessage()));
    }
  }

  private ServerSentEvent<String> errorEvent(String message) {
    return ServerSentEvent.<String>builder(message).event("error").build();
  }

  private HttpStatus mapChatStatus(ChatException e) {
    return switch (e.getReason()) {
      case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case AUTH_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case SERVER_ERROR, UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
    };
  }
}
