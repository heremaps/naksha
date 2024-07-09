/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.core.exceptions;

import naksha.model.NakshaVersion;
import naksha.model.response.NakshaError;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The base class for all exceptions thrown by a storage.
 */
@AvailableSince(NakshaVersion.v2_0_8)
public class StorageException extends RuntimeException {

  /**
   * Wrap the given error result into an exception.
   *
   * @param nakshaError The error to wrap.
   */
  @AvailableSince(NakshaVersion.v2_0_8)
  public StorageException(@NotNull NakshaError nakshaError) {
    super(nakshaError.getMessage());
    this.reason = nakshaError.getMessage();
    this.nakshaError = nakshaError;
  }

  /**
   * Create a new storage exception with the given reason.
   *
   * @param reason The error reason.
   */
  @AvailableSince(NakshaVersion.v2_0_8)
  public StorageException(@NotNull String reason) {
    super(reason);
    this.reason = reason;
  }

  /**
   * Create a new storage exception with the given reason.
   *
   * @param reason  The error reason.
   * @param message An arbitrary error message.
   */
  @AvailableSince(NakshaVersion.v2_0_8)
  public StorageException(@NotNull String reason, @Nullable String message) {
    super(message == null ? reason : message);
    this.reason = reason;
  }

  /**
   * Create a new storage exception with the given reason.
   *
   * @param reason The error reason.
   * @param cause  The cause.
   */
  @AvailableSince(NakshaVersion.v2_0_8)
  public StorageException(@NotNull String reason, @Nullable Throwable cause) {
    super(reason.toString(), cause);
    this.reason = reason;
  }

  /**
   * Create a new storage exception with the given reason.
   *
   * @param reason  The error reason.
   * @param message An arbitrary error message.
   * @param cause   The cause.
   */
  @AvailableSince(NakshaVersion.v2_0_8)
  public StorageException(@NotNull String reason, @Nullable String message, @Nullable Throwable cause) {
    super(message == null ? reason : message, cause);
    this.reason = reason;
  }

  private final @NotNull String reason;
  private @Nullable NakshaError nakshaError;

  /**
   * Convert this exception into an error-result.
   *
   * @return this exception converted into an error-result.
   */
  @AvailableSince(NakshaVersion.v2_0_8)
  public @NotNull NakshaError toNakshaError() {
    if (nakshaError == null) {
      nakshaError = new NakshaError(reason, getMessage());
    }
    return nakshaError;
  }
}
