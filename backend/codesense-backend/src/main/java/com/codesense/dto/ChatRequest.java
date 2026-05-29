package com.codesense.dto;

import jakarta.validation.constraints.NotBlank;

/** Inbound payload for POST /api/reviews/{id}/chat. */
public record ChatRequest(@NotBlank(message = "message is required") String message) {}
