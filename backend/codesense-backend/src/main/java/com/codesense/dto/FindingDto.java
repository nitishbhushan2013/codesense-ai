package com.codesense.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FindingDto(
    String category,
    String severity,
    String lineReference,
    String description,
    String suggestedFix) {}
