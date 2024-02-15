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
package com.here.naksha.storage.http;

import com.here.naksha.lib.core.exceptions.UncheckedException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HttpStorageReadExecute {

  private static final Logger log = LoggerFactory.getLogger(HttpStorageReadExecute.class);

  private final ReadFeaturesProxyWrapper readRequest;
  private final HttpStorage.RequestSender requestSender;

  private final String environment;
  private final String store;

  public HttpStorageReadExecute(ReadFeaturesProxyWrapper readRequest, HttpStorage.RequestSender requestSender) {
    this.readRequest = readRequest;
    this.requestSender = requestSender;

    String[] splitCollectionName = readRequest.getCollections().get(0).split("/");
    this.environment = splitCollectionName[0];
    this.store = splitCollectionName[1];
  }

  @NotNull
  Result execute() {
    try {
      return switch (readRequest.getReadRequestType()) {
        case GET_BY_ID -> executeFeatureById();
        case GET_BY_IDS -> throw new NotImplementedException();
        case GET_BY_BBOX -> executeFeatureByBBox();
        case GET_BY_TILE -> throw new NotImplementedException();
      };
    } catch (Exception e) {
      log.warn("", e);
      return new ErrorResult(XyzError.EXCEPTION, e.getMessage());
    }
  }

  private Result executeFeatureById() throws IOException, InterruptedException {
    String featureId = readRequest.getQueryParameter("featureId");

    HttpResponse<String> response =
        requestSender.sendRequest(String.format("/%s/%s/features/%s", environment, store, featureId));
    XyzFeature resultFeature = JsonSerializable.deserialize(response.body(), XyzFeature.class);
    return createHttpResultFromFeatureList(List.of(resultFeature));
  }

  private Result executeFeatureByBBox() {
    // To be replaced
    return testExecute(readRequest);
  }

  /* To be deleted */
  private Result testExecute(ReadFeaturesProxyWrapper proxyRequest) {
    try {
      String getBy = proxyRequest.getReadRequestType().toString();

      HttpStorageReadSession.testLog.add(getBy);
      HttpStorageReadSession.testLog.add("north = " + proxyRequest.<Double>getQueryParameter("north"));
      HttpStorageReadSession.testLog.add("east = " + proxyRequest.<Double>getQueryParameter("east"));

      HttpResponse<String> httpResponse = requestSender.sendRequest("");

      log.info(httpResponse.body());
      return new SuccessResult();
    } catch (Exception e) {
      throw new UncheckedException(e);
    }
  }

  public static HttpSuccessResult<XyzFeature, XyzFeatureCodec> createHttpResultFromFeatureList(
      final @NotNull List<XyzFeature> features) {
    // Create ForwardCursor with input features
    final List<XyzFeatureCodec> codecs = new ArrayList<>();
    final XyzFeatureCodecFactory codecFactory = XyzFeatureCodecFactory.get();
    for (final XyzFeature feature : features) {
      final XyzFeatureCodec codec = codecFactory.newInstance();
      codec.setOp(EExecutedOp.READ);
      codec.setFeature(feature);
      codecs.add(codec);
    }
    final ListBasedForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
        new ListBasedForwardCursor<>(codecFactory, codecs);

    // Create ContextResult with cursor, context and violations
    final HttpSuccessResult<XyzFeature, XyzFeatureCodec> ctxResult = new HttpSuccessResult(cursor);
    return ctxResult;
  }
}
