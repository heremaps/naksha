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
package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventHandlerProperties;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(ViewHandler.class);

  private @NotNull EventHandler eventHandler;
  private @NotNull EventTarget<?> eventTarget;
  private @NotNull EventHandlerProperties properties;

  public ViewHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties = JsonSerializable.convert(eventHandler.getProperties(), EventHandlerProperties.class);
  }

  @Override
  public @NotNull Result processEvent(@NotNull IEvent event) {

    final NakshaContext ctx = NakshaContext.currentContext();
    final Request<?> request = event.getRequest();
    logger.info("Handler received request {}", request.getClass().getSimpleName());

    final String storageId = properties.getStorageId();

    if (storageId == null) {
      logger.error("No storageId configured");
      return new ErrorResult(XyzError.NOT_FOUND, "No storageId configured for handler.");
    }
    logger.info("Against Storage id={}", storageId);
    addStorageIdToStreamInfo(storageId, ctx);

    final IStorage storageImpl = nakshaHub().getStorageById(storageId);
    logger.info("Using storage implementation [{}]", storageImpl.getClass().getName());
//Validate ? It need to be iview ?




    return event.sendUpstream();
  }
}
