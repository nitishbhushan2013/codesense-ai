package com.codesense.exception;

import lombok.Getter;

@Getter
public class GitHubFetchException extends RuntimeException {

  public enum Reason {
    INVALID_URL,
    NOT_FOUND,
    FORBIDDEN,
    RATE_LIMITED,
    UNAVAILABLE
  }

  private final Reason reason;

  public GitHubFetchException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public GitHubFetchException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }
}
