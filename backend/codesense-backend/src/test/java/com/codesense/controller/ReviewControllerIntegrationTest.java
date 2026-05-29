package com.codesense.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codesense.dto.ClaudeReviewResult;
import com.codesense.dto.FindingDto;
import com.codesense.dto.PullRequestFetch;
import com.codesense.dto.ReviewRequest;
import com.codesense.exception.ClaudeApiException;
import com.codesense.exception.GitHubFetchException;
import com.codesense.model.User;
import com.codesense.repository.ReviewRepository;
import com.codesense.repository.UserRepository;
import com.codesense.service.ClaudeService;
import com.codesense.service.GitHubService;
import com.codesense.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ReviewControllerIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private ReviewRepository reviewRepository;

  @MockBean private GitHubService gitHubService;
  @MockBean private ClaudeService claudeService;
  @MockBean private StorageService storageService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    reviewRepository.deleteAll();
    userRepository.deleteAll();
  }

  private ClaudeReviewResult sampleAiResult() {
    return new ClaudeReviewResult(
        85,
        "Code is solid with minor polish opportunities.",
        List.of(
            new FindingDto(
                "quality",
                "info",
                "Line 5",
                "Variable name could be clearer.",
                "Rename foo to userCount.")));
  }

  @Test
  void anonymousPasteSubmissionReturnsReviewWithoutPersisting() throws Exception {
    when(storageService.store(any())).thenReturn("blob-anon");
    when(claudeService.analyzeCode(any(), any())).thenReturn(sampleAiResult());

    ReviewRequest req = new ReviewRequest("paste", null, "System.out.println('hi');", "Java");

    mockMvc
        .perform(
            post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(85))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.findings.length()").value(1))
        .andExpect(jsonPath("$.findings[0].category").value("quality"));

    assertThat(reviewRepository.count()).isZero();
  }

  @Test
  void loggedInPasteSubmissionPersistsReview() throws Exception {
    User saved =
        userRepository.save(
            User.builder().name("Test User").email("test@example.com").password("dummy").build());

    when(storageService.store(any())).thenReturn("blob-user");
    when(claudeService.analyzeCode(any(), any())).thenReturn(sampleAiResult());

    ReviewRequest req = new ReviewRequest("paste", null, "let x = 1;", "JavaScript");

    mockMvc
        .perform(
            post("/api/reviews")
                .with(user(saved.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.score").value(85))
        .andExpect(jsonPath("$.findings.length()").value(1));

    assertThat(reviewRepository.count()).isEqualTo(1);
  }

  @Test
  void prUrlSubmissionUsesGitHubService() throws Exception {
    when(gitHubService.fetchPullRequestDiff(any()))
        .thenReturn(new PullRequestFetch("diff content", "blob-pr"));
    when(claudeService.analyzeCode(any(), any())).thenReturn(sampleAiResult());

    ReviewRequest req =
        new ReviewRequest("pr_url", "https://github.com/owner/repo/pull/1", null, null);

    mockMvc
        .perform(
            post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submissionType").value("pr_url"))
        .andExpect(jsonPath("$.prUrl").value("https://github.com/owner/repo/pull/1"));
  }

  @Test
  void invalidSubmissionTypeReturns400() throws Exception {
    ReviewRequest req = new ReviewRequest("invalid_type", null, "code", "Java");

    mockMvc
        .perform(
            post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void pasteWithoutLanguageReturns400() throws Exception {
    ReviewRequest req = new ReviewRequest("paste", null, "let x = 1;", null);

    mockMvc
        .perform(
            post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void gitHubNotFoundReturns404() throws Exception {
    when(gitHubService.fetchPullRequestDiff(any()))
        .thenThrow(new GitHubFetchException(GitHubFetchException.Reason.NOT_FOUND, "PR not found"));

    ReviewRequest req =
        new ReviewRequest("pr_url", "https://github.com/owner/repo/pull/999", null, null);

    mockMvc
        .perform(
            post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isNotFound());
  }

  @Test
  void claudeAuthErrorReturns500() throws Exception {
    when(storageService.store(any())).thenReturn("blob-x");
    when(claudeService.analyzeCode(any(), any()))
        .thenThrow(new ClaudeApiException(ClaudeApiException.Reason.AUTH_ERROR, "API key invalid"));

    ReviewRequest req = new ReviewRequest("paste", null, "code", "Java");

    mockMvc
        .perform(
            post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isInternalServerError());
  }
}
