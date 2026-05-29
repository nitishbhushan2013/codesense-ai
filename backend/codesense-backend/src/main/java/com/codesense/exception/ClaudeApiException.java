package com.codesense.exception;

import lombok.Getter;

@Getter
public class ClaudeApiException extends RuntimeException {

  public enum Reason {
    AUTH_ERROR(false),
    BAD_REQUEST(false),
    RATE_LIMITED(true),
    SERVER_ERROR(true),
    PARSE_ERROR(true),
    UNAVAILABLE(true);

    private final boolean retryable;

    Reason(boolean retryable) {
      this.retryable = retryable;
    }

    public boolean isRetryable() {
      return retryable;
    }
  }

  private final Reason reason;

  public ClaudeApiException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public ClaudeApiException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }
}
