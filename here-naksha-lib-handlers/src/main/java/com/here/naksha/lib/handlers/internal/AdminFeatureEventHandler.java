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
package com.here.naksha.lib.handlers.internal;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.internal.NakshaFeaturePropertiesValidator.nakshaFeatureValidation;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import naksha.model.IReadSession;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.psql.PgStorage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract event handler responsible for processing admin resources (like Storage or EventHandler)
 *
 * @param <FEATURE> type of admin resource handled by this handler
 */
abstract class AdminFeatureEventHandler<FEATURE extends NakshaFeature> extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AdminFeatureEventHandler.class);

  private final Class<FEATURE> featureClass;

  AdminFeatureEventHandler(@NotNull INaksha hub, @NotNull Class<FEATURE> featureClass) {
    super(hub);
    this.featureClass = featureClass;
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (request instanceof ReadRequest || RequestTypesUtil.isOnlyWriteFeatures(request)) {
      return PROCESS;
    }
    return NOT_IMPLEMENTED;
  }

  @Override
  public final @NotNull Response process(@NotNull IEvent event) {
    final NakshaContext ctx = NakshaContext.currentContext();
    final Request request = event.getRequest();
    // process request using Naksha Admin Storage instance
    addStorageIdToStreamInfo(PgStorage.ADMIN_STORAGE_ID, ctx);
    if (request instanceof ReadRequest rr) {
      try (final IReadSession reader =
          nakshaHub().getAdminStorage().newReadSession(SessionOptions.from(ctx, false))) {
        return reader.execute(rr);
      }
    } else if ((request instanceof WriteRequest wr) && (RequestTypesUtil.isOnlyWriteFeatures(request))) {
      // validate the request before persisting
      Response valResult = validateWriteRequest(wr);
      if (valResult instanceof ErrorResponse er) {
        return er;
      }
      // persist in storage
      final IWriteSession writer = nakshaHub().getAdminStorage().newWriteSession(SessionOptions.from(ctx, true));
      final Response result = writer.execute(wr);
      if (result instanceof SuccessResponse) {
        writer.commit();
      } else {
        logger.warn("Failed writing feature request to admin storage, expected success but got: {}", result);
        writer.rollback();
      }
      return result;
    } else {
      return notImplemented(request);
    }
  }

  /**
   * Direct validation of NakshaFeature to be written.
   *
   * @param writeOperation containing the feature to be validated before being written
   * @return validation result
   */
  protected @NotNull Response validateWrite(Write writeOperation) {
    final FEATURE feature = featureClass.cast(writeOperation.getFeature());
    return nakshaFeatureValidation(feature);
  }

  private @NotNull Response validateWriteRequest(final @NotNull WriteRequest wr) {
    for (final Write writeOperation : wr.getWrites()) {
      Response featureValidation = validateWrite(writeOperation);
      if (featureValidation instanceof ErrorResponse) {
        return featureValidation;
      }
    }
    return new SuccessResponse();
  }
}
