package com.codesense.dto;

import java.time.LocalDateTime;

/** API-facing chat message returned by ChatController history endpoint. */
public record ChatMessageDto(String role, String content, LocalDateTime createdAt) {}
