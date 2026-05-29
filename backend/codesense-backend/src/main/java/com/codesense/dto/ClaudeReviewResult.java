package com.codesense.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Raw output from ClaudeService — the AI's review of a code blob. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeReviewResult(int score, String summary, List<FindingDto> findings) {}
