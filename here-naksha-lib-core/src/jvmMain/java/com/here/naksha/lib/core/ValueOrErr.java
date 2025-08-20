package com.here.naksha.lib.core;

import naksha.model.request.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper to simplify returning response payloads, or {@link ErrorResponse}, when an error happened.
 * @param <VALUE> The type of the value to return.
 * @since 3.0
 */
public class ValueOrErr<VALUE> {
  /**
   * The value was found.
   * @param value The value.
   * @since 3.0
   */
  protected ValueOrErr(@NotNull VALUE value) {
    this.value = requireNonNull(value);
    this.errorResponse = null;
  }

  /**
   * When acquiring the value failed due to an error.
   * @param errorResponse The error response.
   * @since 3.0
   */
  protected ValueOrErr(@NotNull ErrorResponse errorResponse) {
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
   * Returns the value or throws an exception.
   * @return the value.
   * @throws Err if no value is available, but an error response.
   */
  public @NotNull VALUE get() throws Err {
    if (errorResponse != null) throw new Err(errorResponse);
    return requireNonNull(value);
  }
}
