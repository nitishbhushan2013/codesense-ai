package com.codesense.service;

import com.codesense.dto.ChatMessageDto;
import com.codesense.model.ChatMessage;
import com.codesense.model.Review;
import com.codesense.repository.ChatMessageRepository;
import com.codesense.repository.ReviewRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

  private final ChatMessageRepository chatMessageRepository;
  private final ReviewRepository reviewRepository;
  private final ClaudeService claudeService;

  /**
   * Sends a user message and streams the assistant reply token by token. Persists both the user
   * message and the full assistant response once streaming completes.
   */
  public Flux<String> sendMessage(UUID reviewId, String userMessage, String userId) {
    Review review = loadAndAuthorise(reviewId, userId);

    chatMessageRepository.save(
        ChatMessage.builder().review(review).role("user").content(userMessage).build());

    List<Map<String, String>> messages = buildMessageHistory(reviewId, userMessage);
    String systemPrompt = buildSystemPrompt(review);

    StringBuilder collected = new StringBuilder();

    return claudeService
        .streamChat(messages, systemPrompt)
        .doOnNext(collected::append)
        .doOnComplete(
            () -> {
              String assistantReply = collected.toString();
              if (!assistantReply.isBlank()) {
                chatMessageRepository.save(
                    ChatMessage.builder()
                        .review(review)
                        .role("assistant")
                        .content(assistantReply)
                        .build());
                log.debug("Persisted assistant reply for review {}", reviewId);
              }
            });
  }

  /** Returns the full chat history for a review, oldest first. */
  public List<ChatMessageDto> getHistory(UUID reviewId, String userId) {
    loadAndAuthorise(reviewId, userId);
    return chatMessageRepository.findByReviewIdOrderByCreatedAtAsc(reviewId).stream()
        .map(m -> new ChatMessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
        .toList();
  }

  private Review loadAndAuthorise(UUID reviewId, String userId) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

    if (review.getUser() == null || !review.getUser().getId().toString().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    return review;
  }

  private String buildSystemPrompt(Review review) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "You are an expert code reviewer helping a developer understand and act on a code review.\n\n");
    sb.append("Original review summary:\n").append(review.getSummary()).append("\n\n");

    if (review.getFindings() != null && !review.getFindings().isEmpty()) {
      sb.append("Findings identified in the review:\n");
      review
          .getFindings()
          .forEach(
              f ->
                  sb.append("- [")
                      .append(f.getSeverity().toUpperCase())
                      .append("] ")
                      .append(f.getCategory())
                      .append(": ")
                      .append(f.getDescription())
                      .append("\n"));
      sb.append("\n");
    }

    sb.append(
        "Answer the developer's questions concisely and helpfully. "
            + "Reference specific findings when relevant. "
            + "Provide corrected code snippets when asked.");
    return sb.toString();
  }

  private List<Map<String, String>> buildMessageHistory(UUID reviewId, String newUserMessage) {
    List<Map<String, String>> messages = new ArrayList<>();

    chatMessageRepository
        .findByReviewIdOrderByCreatedAtAsc(reviewId)
        .forEach(m -> messages.add(Map.of("role", m.getRole(), "content", m.getContent())));

    messages.add(Map.of("role", "user", "content", newUserMessage));
    return messages;
  }
}
