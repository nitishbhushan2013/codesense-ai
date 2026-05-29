package com.codesense.service;

import com.codesense.dto.ClaudeReviewResult;
import com.codesense.dto.FindingDto;
import com.codesense.dto.PullRequestFetch;
import com.codesense.dto.ReviewRequest;
import com.codesense.dto.ReviewResponse;
import com.codesense.model.Finding;
import com.codesense.model.Review;
import com.codesense.model.User;
import com.codesense.repository.ReviewRepository;
import com.codesense.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

  private final GitHubService gitHubService;
  private final StorageService storageService;
  private final ClaudeService claudeService;
  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;

  public ReviewResponse submit(ReviewRequest request, String userId) {
    String content;
    String blobKey;
    String language = request.language();

    if ("pr_url".equals(request.submissionType())) {
      if (request.prUrl() == null || request.prUrl().isBlank()) {
        throw new IllegalArgumentException("prUrl is required for pr_url submissions");
      }
      PullRequestFetch fetch = gitHubService.fetchPullRequestDiff(request.prUrl());
      content = fetch.diff();
      blobKey = fetch.blobKey();
      if (language == null || language.isBlank()) {
        language = "diff";
      }
    } else {
      if (request.code() == null || request.code().isBlank()) {
        throw new IllegalArgumentException("code is required for paste submissions");
      }
      if (language == null || language.isBlank()) {
        throw new IllegalArgumentException("language is required for paste submissions");
      }
      content = request.code();
      blobKey = storageService.store(content);
    }

    ClaudeReviewResult aiResult = claudeService.analyzeCode(content, language);

    if (userId != null) {
      return persistReview(request, aiResult, blobKey, language, userId);
    }
    return ephemeralResponse(request, aiResult, language);
  }

  private ReviewResponse persistReview(
      ReviewRequest request,
      ClaudeReviewResult ai,
      String blobKey,
      String language,
      String userId) {
    User user =
        userRepository
            .findById(UUID.fromString(userId))
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    Review review =
        Review.builder()
            .user(user)
            .submissionType(request.submissionType())
            .language(language)
            .prUrl(request.prUrl())
            .blobKey(blobKey)
            .score(ai.score())
            .summary(ai.summary())
            .status("completed")
            .build();

    if (ai.findings() != null) {
      for (FindingDto dto : ai.findings()) {
        Finding f =
            Finding.builder()
                .category(dto.category())
                .severity(dto.severity())
                .lineReference(dto.lineReference())
                .description(dto.description())
                .suggestedFix(dto.suggestedFix())
                .build();
        review.addFinding(f);
      }
    }

    Review saved = reviewRepository.save(review);
    log.info("Persisted review {} for user {}", saved.getId(), userId);
    return toResponse(saved);
  }

  private ReviewResponse ephemeralResponse(
      ReviewRequest request, ClaudeReviewResult ai, String language) {
    return new ReviewResponse(
        null,
        request.submissionType(),
        language,
        request.prUrl(),
        ai.score(),
        ai.summary(),
        "completed",
        ai.findings() == null ? List.of() : ai.findings(),
        null);
  }

  private ReviewResponse toResponse(Review r) {
    List<FindingDto> dtos =
        r.getFindings().stream()
            .map(
                f ->
                    new FindingDto(
                        f.getCategory(),
                        f.getSeverity(),
                        f.getLineReference(),
                        f.getDescription(),
                        f.getSuggestedFix()))
            .toList();
    return new ReviewResponse(
        r.getId().toString(),
        r.getSubmissionType(),
        r.getLanguage(),
        r.getPrUrl(),
        r.getScore() == null ? 0 : r.getScore(),
        r.getSummary(),
        r.getStatus(),
        dtos,
        r.getCreatedAt());
  }
}
