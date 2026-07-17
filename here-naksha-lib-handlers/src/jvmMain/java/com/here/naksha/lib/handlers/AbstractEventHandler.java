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
package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.INaksha;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.model.StreamInfo;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractEventHandler implements IEventHandler {

  protected final @NotNull INaksha nakshaHub;

  protected AbstractEventHandler(final @NotNull INaksha hub) {
    this.nakshaHub = hub;
  }

  protected final @NotNull INaksha nakshaHub() {
    return nakshaHub;
  }

  protected abstract EventProcessingStrategy processingStrategyFor(IEvent event);

  protected abstract @NotNull Response process(@NotNull IEvent event);

  @Override
  public final @NotNull Response processEvent(@NotNull IEvent event) {
    switch (processingStrategyFor(event)) {
      case PROCESS:
        return process(event);
      case SEND_UPSTREAM_WITHOUT_PROCESSING:
        return event.sendUpstream();
      case SUCCEED_WITHOUT_PROCESSING:
        return new SuccessResponse();
      case NOT_IMPLEMENTED:
        return notImplemented(event);
      default:
        throw new IllegalStateException("Unsupported processing strategy");
    }
  }

  protected @NotNull Response notImplemented(@NotNull IEvent event) {
    return notImplemented(event.getRequest());
  }

  protected @NotNull Response notImplemented(@NotNull Request processedRequest) {
    return new ErrorResponse(
        NakshaError.NOT_IMPLEMENTED,
        "Event processing of " + processedRequest.getClass().getSimpleName() + " in "
            + this.getClass().getSimpleName() + " is not supported");
  }

  protected void addStorageIdToStreamInfo(final @Nullable String storageId, final @NotNull NakshaContext context) {
    final StreamInfo streamInfo = context.getStreamInfo();
    if (streamInfo != null) {
      streamInfo.withStorageIdIfMissing(storageId);
    }
  }

  public enum EventProcessingStrategy {
    PROCESS,
    SEND_UPSTREAM_WITHOUT_PROCESSING,
    SUCCEED_WITHOUT_PROCESSING,
    NOT_IMPLEMENTED
  }
}
