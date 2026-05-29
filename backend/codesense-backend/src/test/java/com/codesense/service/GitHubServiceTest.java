package com.codesense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codesense.dto.PullRequestFetch;
import com.codesense.dto.PullRequestRef;
import com.codesense.exception.GitHubFetchException;
import com.codesense.exception.GitHubFetchException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class GitHubServiceTest {

  private static final String SAMPLE_DIFF =
      "diff --git a/foo.java b/foo.java\n"
          + "--- a/foo.java\n"
          + "+++ b/foo.java\n"
          + "@@ -1 +1 @@\n"
          + "-old\n"
          + "+new\n";

  private StorageService storageService;

  @BeforeEach
  void setUp() {
    storageService = mock(StorageService.class);
    when(storageService.store(any())).thenReturn("stored-blob-key");
  }

  private GitHubService buildService(ExchangeFunction exchange) {
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    return new GitHubService(builder, storageService, "https://api.github.com", "");
  }

  @Test
  void parsesValidPrUrl() {
    GitHubService svc = buildService(req -> Mono.empty());
    PullRequestRef ref = svc.parsePrUrl("https://github.com/owner/repo/pull/42");
    assertThat(ref.owner()).isEqualTo("owner");
    assertThat(ref.repo()).isEqualTo("repo");
    assertThat(ref.number()).isEqualTo(42);
  }

  @Test
  void rejectsInvalidPrUrl() {
    GitHubService svc = buildService(req -> Mono.empty());
    assertThatThrownBy(() -> svc.parsePrUrl("https://example.com/not/a/pr"))
        .isInstanceOf(GitHubFetchException.class)
        .extracting("reason")
        .isEqualTo(Reason.INVALID_URL);
  }

  @Test
  void returnsDiffAndBlobKeyOnSuccess() {
    GitHubService svc =
        buildService(
            req ->
                Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .body(SAMPLE_DIFF)
                        .build()));

    PullRequestFetch fetch = svc.fetchPullRequestDiff("https://github.com/owner/repo/pull/1");

    assertThat(fetch.diff()).isEqualTo(SAMPLE_DIFF);
    assertThat(fetch.blobKey()).isEqualTo("stored-blob-key");
  }

  @Test
  void mapsNotFoundToNotFoundReason() {
    GitHubService svc =
        buildService(
            req -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).body("").build()));

    assertThatThrownBy(() -> svc.fetchPullRequestDiff("https://github.com/owner/repo/pull/1"))
        .isInstanceOf(GitHubFetchException.class)
        .extracting("reason")
        .isEqualTo(Reason.NOT_FOUND);
  }

  @Test
  void mapsRateLimitedForbiddenToRateLimited() {
    GitHubService svc =
        buildService(
            req ->
                Mono.just(
                    ClientResponse.create(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .body("")
                        .build()));

    assertThatThrownBy(() -> svc.fetchPullRequestDiff("https://github.com/owner/repo/pull/1"))
        .isInstanceOf(GitHubFetchException.class)
        .extracting("reason")
        .isEqualTo(Reason.RATE_LIMITED);
  }

  @Test
  void mapsRegularForbiddenToForbidden() {
    GitHubService svc =
        buildService(
            req ->
                Mono.just(
                    ClientResponse.create(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "59")
                        .body("")
                        .build()));

    assertThatThrownBy(() -> svc.fetchPullRequestDiff("https://github.com/owner/repo/pull/1"))
        .isInstanceOf(GitHubFetchException.class)
        .extracting("reason")
        .isEqualTo(Reason.FORBIDDEN);
  }
}
