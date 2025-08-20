package com.here.naksha.lib.core;

import naksha.model.request.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper to simplify returning response payloads, or {@link ErrorResponse}, when an error happened.
 * @param <VALUE> The type of the value to return.
 * @since 3.0
 * @see Err
 */
public final class ValueOrErr<VALUE> {
  /**
   * The value was found.
   * @param value The value.
   * @since 3.0
   */
  public ValueOrErr(@NotNull VALUE value) {
    this.value = requireNonNull(value);
    this.errorResponse = null;
  }

  /**
   * When acquiring the value failed due to an error.
   * @param errorCode The error code, see {@link naksha.base.NakshaError}.
   * @param errorMessage The human-readable error message.
   * @since 3.0
   */
  public ValueOrErr(@NotNull String errorCode, @NotNull String errorMessage) {
    this.value = null;
    this.errorResponse = new ErrorResponse(requireNonNull(errorCode), requireNonNull(errorMessage));
  }

  /**
   * When acquiring the value failed due to an error.
   * @param errorCode The error code, see {@link naksha.base.NakshaError}.
   * @param errorMessage The human-readable error message.
   * @param cause The exception causing this error response.
   * @since 3.0
   */
  public ValueOrErr(@NotNull String errorCode, @NotNull String errorMessage, @Nullable Throwable cause) {
    this.value = null;
    this.errorResponse = new ErrorResponse(requireNonNull(errorCode), requireNonNull(errorMessage), cause);
  }

  /**
   * When acquiring the value failed due to an error.
   * @param errorResponse The error response.
   * @since 3.0
   */
  public ValueOrErr(@NotNull ErrorResponse errorResponse) {
    this.value = null;
    this.errorResponse = requireNonNull(errorResponse);
  }

  /**
   * The value or <code>null</code>, if not found and no error.
   * @since 3.0
   */
  private final @Nullable VALUE value;

  /**
   * The error response, if acquiring the value failed.
   * @since 3.0
   */
  private final @Nullable ErrorResponse errorResponse;

  /**
   * Returns the value or throws an {@link Err} exception.
   * @return the value.
   * @throws Err if no value is available, but an error response.
   */
  public @NotNull VALUE get() throws Err {
    if (errorResponse != null) throw new Err(errorResponse);
    final var v = value;
    assert v != null;
    return v;
  }
}
