package com.codesense.controller;

import com.codesense.dto.ReviewRequest;
import com.codesense.dto.ReviewResponse;
import com.codesense.exception.ClaudeApiException;
import com.codesense.exception.GitHubFetchException;
import com.codesense.model.Review;
import com.codesense.repository.ReviewRepository;
import com.codesense.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@Slf4j
public class ReviewController {

  private final ReviewService reviewService;
  private final ReviewRepository reviewRepository;

  @PostMapping
  public ResponseEntity<?> submit(
      @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails != null ? userDetails.getUsername() : null;
    try {
      ReviewResponse response = reviewService.submit(request, userId);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    } catch (GitHubFetchException e) {
      log.warn("GitHub fetch failed: {} - {}", e.getReason(), e.getMessage());
      return ResponseEntity.status(mapGitHubStatus(e)).body(Map.of("message", e.getMessage()));
    } catch (ClaudeApiException e) {
      log.warn("Claude call failed: {} - {}", e.getReason(), e.getMessage());
      return ResponseEntity.status(mapClaudeStatus(e)).body(Map.of("message", e.getMessage()));
    }
  }

  private HttpStatus mapGitHubStatus(GitHubFetchException e) {
    return switch (e.getReason()) {
      case INVALID_URL -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
    };
  }

  private HttpStatus mapClaudeStatus(ClaudeApiException e) {
    return switch (e.getReason()) {
      case AUTH_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
      case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case SERVER_ERROR, UNAVAILABLE, PARSE_ERROR -> HttpStatus.BAD_GATEWAY;
    };
  }

  @GetMapping
  public ResponseEntity<?> getReviews(@AuthenticationPrincipal UserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
    }
    UUID userId = UUID.fromString(userDetails.getUsername());
    List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    return ResponseEntity.ok(reviews);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteReview(
      @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
    }

    UUID userId = UUID.fromString(userDetails.getUsername());
    Review review = reviewRepository.findById(id).orElse(null);

    if (review == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("message", "Review not found"));
    }

    if (!review.getUser().getId().equals(userId)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(Map.of("message", "You do not have permission to delete this review"));
    }

    reviewRepository.delete(review);
    return ResponseEntity.ok(Map.of("message", "Review deleted successfully"));
  }
}
