package com.here.naksha.lib.core;

import naksha.base.NakshaError;
import naksha.model.request.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapper for an error response.
 * @since 3.0
 */
public class Err extends Exception {
  /**
   * Create a new error exception for the given error response.
   * @param errorResponse the error response.
   * @since 3.0
   */
  public Err(@NotNull ErrorResponse errorResponse) {
    // Note: This makes the exception much cheaper, it is only to control flow!
    super(null, null, false, false);
    this.errorResponse = errorResponse;
  }

  /**
   * Create a new error exception for the given error code (see {@link NakshaError}) and the given message.
   * @param code the error code.
   * @param message the error message.
   * @since 3.0
   */
  public Err(@NotNull String code, @NotNull String message) {
    super(null, null, false, false);
    this.errorResponse = new ErrorResponse(code, message);
  }

  /**
   * Create a new error exception for the given error code (see {@link NakshaError}) and the given message.
   * @param code the error code.
   * @param message the error message.
   * @param cause the cause.
   * @since 3.0
   */
  public Err(@NotNull String code, @NotNull String message, @Nullable Throwable cause) {
    super(null, cause, false, false);
    this.errorResponse = new ErrorResponse(code, message, cause);
  }

  /**
   * The error response linked to this exception.
   * @since 3.0
   */
  public final @NotNull ErrorResponse errorResponse;

  /**
   * Returns the {@link ErrorResponse}.
   * @return the {@link ErrorResponse}.
   * @since 3.0
   */
  public @NotNull ErrorResponse get() {
    return errorResponse;
  }

  private Boolean notFound = null;

  /**
   * Tests if the error is {@link NakshaError#NOT_FOUND}, so the requested object was simply not found.
   * @return <i>true</i> if there is an {@link #errorResponse} and it is {@link NakshaError#NOT_FOUND}; <i>false</i> otherwise.
   * @since 3.0
   */
  public boolean isNotFound() {
    var nf = notFound;
    if (nf != null) return nf;
    notFound = NakshaError.NOT_FOUND.equals(errorResponse.getError().getCode());
    return notFound;
  }
}
