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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import com.here.naksha.common.http.apis.ApiParamsConst;
import com.here.naksha.lib.core.exceptions.UncheckedException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
      return new ErrorResult(XyzError.EXCEPTION, e.getMessage(), e);
    }
  }

  private Result executeFeatureById() throws IOException, InterruptedException {
    String featureId = readRequest.getQueryParameter(ApiParamsConst.FEATURE_ID);

    HttpResponse<String> response =
        requestSender.sendRequest(String.format("/%s/%s/features/%s", environment, store, featureId));

    XyzError error = mapHttpStatusToErrorOrNull(response.statusCode());
    if (error != null) return new ErrorResult(error, "Response http status code: " + response.statusCode());

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
      HttpStorageReadSession.testLog.add(
          "north = " + proxyRequest.<Double>getQueryParameter(ApiParamsConst.NORTH));
      HttpStorageReadSession.testLog.add("east = " + proxyRequest.<Double>getQueryParameter(ApiParamsConst.EAST));

      HttpResponse<String> httpResponse = requestSender.sendRequest("");

      log.info(httpResponse.body());
      return new SuccessResult();
    } catch (Exception e) {
      throw new UncheckedException(e);
    }
  }

  private static HttpSuccessResult<XyzFeature, XyzFeatureCodec> createHttpResultFromFeatureList(
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

    return new HttpSuccessResult<>(cursor);
  }

  private @Nullable XyzError mapHttpStatusToErrorOrNull(final int httpStatus) {
    if (httpStatus < 400) return null;
    return switch (httpStatus) {
      case HttpURLConnection.HTTP_INTERNAL_ERROR -> XyzError.EXCEPTION;
      case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> XyzError.NOT_IMPLEMENTED;
      case HttpURLConnection.HTTP_BAD_REQUEST -> XyzError.ILLEGAL_ARGUMENT;
      case HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> XyzError.PAYLOAD_TOO_LARGE;
      case HttpURLConnection.HTTP_BAD_GATEWAY -> XyzError.BAD_GATEWAY;
      case HttpURLConnection.HTTP_CONFLICT -> XyzError.CONFLICT;
      case HttpURLConnection.HTTP_UNAUTHORIZED -> XyzError.UNAUTHORIZED;
      case HttpURLConnection.HTTP_FORBIDDEN -> XyzError.FORBIDDEN;
      case 429 -> XyzError.TOO_MANY_REQUESTS;
      case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> XyzError.TIMEOUT;
      case HttpURLConnection.HTTP_NOT_FOUND -> XyzError.NOT_FOUND;
      default -> throw unchecked(new IllegalAccessException());
    };
  }
}
