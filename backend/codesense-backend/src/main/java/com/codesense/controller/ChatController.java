package com.codesense.controller;

import com.codesense.dto.ChatMessageDto;
import com.codesense.dto.ChatRequest;
import com.codesense.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/reviews/{id}/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

  private final ChatService chatService;

  @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter sendMessage(
      @PathVariable UUID id,
      @Valid @RequestBody ChatRequest request,
      @AuthenticationPrincipal UserDetails userDetails,
      HttpServletResponse response) {
    response.setHeader("X-Accel-Buffering", "no");
    response.setHeader("Cache-Control", "no-cache");

    if (userDetails == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    SseEmitter emitter = new SseEmitter(120_000L);

    chatService
        .sendMessage(id, request.message(), userDetails.getUsername())
        .subscribe(
            chunk -> {
              try {
                emitter.send(SseEmitter.event().data(chunk));
              } catch (IOException e) {
                emitter.completeWithError(e);
              }
            },
            error -> {
              log.warn("Chat stream error for review {}: {}", id, error.getMessage());
              try {
                emitter.send(
                    SseEmitter.event().name("error").data(Map.of("message", error.getMessage())));
              } catch (IOException ignored) {
              }
              emitter.completeWithError(error);
            },
            emitter::complete);

    return emitter;
  }

  @GetMapping
  public ResponseEntity<List<ChatMessageDto>> getHistory(
      @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {

    if (userDetails == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    List<ChatMessageDto> history = chatService.getHistory(id, userDetails.getUsername());
    return ResponseEntity.ok(history);
  }
}
