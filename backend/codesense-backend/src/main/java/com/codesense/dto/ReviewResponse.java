package com.codesense.dto;

import java.time.LocalDateTime;
import java.util.List;

/** API-facing review payload returned by ReviewController. */
public record ReviewResponse(
    String id,
    String submissionType,
    String language,
    String prUrl,
    int score,
    String summary,
    String status,
    List<FindingDto> findings,
    LocalDateTime createdAt) {}
