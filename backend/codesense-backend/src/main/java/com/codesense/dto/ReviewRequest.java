package com.codesense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewRequest(
    @NotBlank @Pattern(regexp = "pr_url|paste") @JsonProperty("submissionType")
        String submissionType,
    @JsonProperty("prUrl") String prUrl,
    @JsonProperty("code") String code,
    @JsonProperty("language") String language) {}
