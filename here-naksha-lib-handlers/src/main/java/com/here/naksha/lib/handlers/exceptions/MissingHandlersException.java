/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.lib.handlers.exceptions;

import static com.here.naksha.lib.core.models.XyzError.NOT_FOUND;

import com.here.naksha.lib.core.models.storage.ErrorResult;
import java.util.List;
import java.util.Objects;

public class MissingHandlersException extends RuntimeException {

  private final String spaceId;
  private final List<String> missingHandlerIds;

  public MissingHandlersException(String spaceId, List<String> missingHandlerIds) {
    super("Following handlers defined for Space %s don't exist: %s"
        .formatted(spaceId, String.join(",", missingHandlerIds)));
    this.spaceId = spaceId;
    this.missingHandlerIds = missingHandlerIds;
  }

  public ErrorResult toErrorResult() {
    return new ErrorResult(NOT_FOUND, getMessage(), this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MissingHandlersException that = (MissingHandlersException) o;
    return Objects.equals(spaceId, that.spaceId) && Objects.equals(missingHandlerIds, that.missingHandlerIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spaceId, missingHandlerIds);
  }
}
