package com.codesense.exception;

import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {

  public enum Reason {
    AUTH_ERROR(false),
    BAD_REQUEST(false),
    NOT_FOUND(false),
    FORBIDDEN(false),
    RATE_LIMITED(true),
    SERVER_ERROR(true),
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

  public ChatException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public ChatException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }
}
