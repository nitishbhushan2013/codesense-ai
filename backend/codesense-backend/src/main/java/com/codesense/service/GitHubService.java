package com.codesense.service;

import com.codesense.dto.PullRequestFetch;
import com.codesense.dto.PullRequestRef;
import com.codesense.exception.GitHubFetchException;
import com.codesense.exception.GitHubFetchException.Reason;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@Slf4j
public class GitHubService {

  private static final Pattern PR_URL_PATTERN =
      Pattern.compile("^https?://github\\.com/([\\w.-]+)/([\\w.-]+)/pull/(\\d+)/?$");

  private static final String DIFF_ACCEPT = "application/vnd.github.v3.diff";

  private final WebClient webClient;
  private final StorageService storageService;
  private final String token;

  public GitHubService(
      WebClient.Builder webClientBuilder,
      StorageService storageService,
      @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
      @Value("${github.api.token:}") String token) {
    this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    this.storageService = storageService;
    this.token = token;
  }

  public PullRequestFetch fetchPullRequestDiff(String prUrl) {
    PullRequestRef ref = parsePrUrl(prUrl);
    String diff = fetchDiff(ref);
    String blobKey = storageService.store(diff);
    log.info("Fetched PR diff for {} -> blob {}", prUrl, blobKey);
    return new PullRequestFetch(diff, blobKey);
  }

  PullRequestRef parsePrUrl(String prUrl) {
    if (prUrl == null) {
      throw new GitHubFetchException(Reason.INVALID_URL, "PR URL is required");
    }
    Matcher m = PR_URL_PATTERN.matcher(prUrl.trim());
    if (!m.matches()) {
      throw new GitHubFetchException(
          Reason.INVALID_URL,
          "Invalid GitHub PR URL — expected https://github.com/{owner}/{repo}/pull/{n}");
    }
    return new PullRequestRef(m.group(1), m.group(2), Integer.parseInt(m.group(3)));
  }

  private String fetchDiff(PullRequestRef ref) {
    try {
      return webClient
          .get()
          .uri("/repos/{owner}/{repo}/pulls/{n}", ref.owner(), ref.repo(), ref.number())
          .headers(this::applyHeaders)
          .retrieve()
          .bodyToMono(String.class)
          .block();
    } catch (WebClientResponseException e) {
      throw mapWebClientError(ref, e);
    } catch (RuntimeException e) {
      throw new GitHubFetchException(
          Reason.UNAVAILABLE, "Failed to contact GitHub: " + e.getMessage(), e);
    }
  }

  private void applyHeaders(HttpHeaders headers) {
    headers.set(HttpHeaders.ACCEPT, DIFF_ACCEPT);
    headers.set("X-GitHub-Api-Version", "2022-11-28");
    if (token != null && !token.isBlank()) {
      headers.setBearerAuth(token);
    }
  }

  private GitHubFetchException mapWebClientError(PullRequestRef ref, WebClientResponseException e) {
    HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
    if (status == HttpStatus.NOT_FOUND) {
      return new GitHubFetchException(
          Reason.NOT_FOUND,
          "Pull request not found — check the URL is correct and the repo is public");
    }
    if (status == HttpStatus.FORBIDDEN) {
      String remaining = e.getHeaders().getFirst("X-RateLimit-Remaining");
      if ("0".equals(remaining)) {
        return new GitHubFetchException(
            Reason.RATE_LIMITED,
            "GitHub API rate limit exceeded — try again later or configure a PAT");
      }
      return new GitHubFetchException(Reason.FORBIDDEN, "Access denied — the repo may be private");
    }
    return new GitHubFetchException(
        Reason.UNAVAILABLE,
        "GitHub returned " + e.getStatusCode() + " for " + ref.owner() + "/" + ref.repo());
  }
}
