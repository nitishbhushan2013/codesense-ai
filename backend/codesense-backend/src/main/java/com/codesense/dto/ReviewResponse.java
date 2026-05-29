package com.codesense.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewResponse(int score, String summary, List<FindingDto> findings) {}
