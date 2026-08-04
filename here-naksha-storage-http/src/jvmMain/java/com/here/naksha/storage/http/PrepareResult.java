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
package com.here.naksha.storage.http;

import naksha.base.PAnyMap;
import naksha.base.FromJsonOptions;
import naksha.base.JvmBoxingUtil;
import naksha.base.Base;
import naksha.base.NakshaError;
import naksha.model.XyzFeatureCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.Nullable;
import static naksha.base.Base.getLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

/**
 * Builds a {@link Response} from {@link HttpResponse}
 */
public class PrepareResult {

  private static final String TYPE_KEY = "type";
  private static final String ERROR_RESPONSE = "ErrorResponse";
  private static final String ERROR_KEY = "error";
  private static final String ERROR_MSG_KEY = "errorMessage";
  private static final String EMPTY_LIST_MSG = "head of empty list";
  private static final String ERROR_MSG_PREFIX = "Error response : ";
  private static final String UNKNOWN_ERROR = "Unknown error";

  public static Response prepareResult(HttpResponse<byte[]> httpResponse,
                                    Function<Object, List<NakshaFeature>> tuplesToFeatureList) {

    String error = mapHttpStatusToErrorOrNull(httpResponse.statusCode());
    if (error != null)
      return new ErrorResponse(new NakshaError(error, "Response http status code: " + httpResponse.statusCode()));

    Object tuples = Base.fromJSON(prepareBody(httpResponse), FromJsonOptions.DEFAULT);
    PAnyMap any = JvmBoxingUtil.box(tuples, PAnyMap.class);
    // Some storages may return success status code but contain ErrorResponse.
    if(isSuccessStatusErrorResponse(any)){
      NakshaError successStatusError = extractNakshaError(any);
      if(isEmptyListError(successStatusError)){
        return new SuccessResponse().withObjects(new NakshaFeatureList());
      }
      return new ErrorResponse(successStatusError);
    }
    List<NakshaFeature> featuresList = tuplesToFeatureList.apply(tuples);
    NakshaFeatureList nakshaFeatures = NakshaFeatureList.fromList(featuresList);
    return new SuccessResponse().withObjects(nakshaFeatures);
  }

  private static boolean isSuccessStatusErrorResponse(PAnyMap any) {
    if (any == null) return false;
    Object type = any.getRaw(TYPE_KEY);
    if (ERROR_RESPONSE.equals(type)) return true;
    return any.getRaw(ERROR_KEY) != null || any.getRaw(ERROR_MSG_KEY) != null;
  }

  private static NakshaError extractNakshaError(PAnyMap any) {
    String codeStr = asString(any.getRaw(ERROR_KEY));
    String msgStr  = asString(any.getRaw(ERROR_MSG_KEY));

    if (codeStr != null || msgStr != null) {
      return new NakshaError(
              defaultIfEmpty(codeStr, NakshaError.EXCEPTION),
              ERROR_MSG_PREFIX + defaultIfEmpty(msgStr,  UNKNOWN_ERROR)
      );
    }
    Object rawErr = any.getRaw(ERROR_KEY);
    try {
      NakshaError boxed = JvmBoxingUtil.box(rawErr, NakshaError.class);
      if (boxed != null) return boxed;
    } catch (IllegalStateException ex) {
      getLogger().warn("Unsuccessful boxing DH response to NakshaError. Error was [{}]", ex.getMessage());
    }
    return new NakshaError(NakshaError.EXCEPTION, UNKNOWN_ERROR);
  }
  /**
   * @return true is the error is the "head of empty list" error known to be thrown by connector.
   */
  private static boolean isEmptyListError(NakshaError err) {
    return (ERROR_MSG_PREFIX + EMPTY_LIST_MSG).equals(err != null ? err.getMsg() : null);
  }

  private static String asString(Object v) { return (v instanceof String) ? (String) v : null; }
  private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }
  private static String defaultIfEmpty(String v, String def) { return isEmpty(v) ? def : v; }


  private static String prepareBody(HttpResponse<byte[]> response) {
    List<String> contentEncodingList = response.headers().allValues("content-encoding");
    if (contentEncodingList.isEmpty()) return new String(response.body(), StandardCharsets.UTF_8);
    if (contentEncodingList.size() > 1)
      throw new IllegalArgumentException("There are more than one Content-Encoding value in response");
    String contentEncoding = contentEncodingList.get(0);

    if (contentEncoding.equalsIgnoreCase("gzip")) return gzipDecode(response.body());
    else throw new IllegalArgumentException("Encoding " + contentEncoding + " not recognized");
  }

  private static String gzipDecode(byte[] encoded) {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(encoded);
        GZIPInputStream gis = new GZIPInputStream(bis);
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      gis.transferTo(bos);
      return bos.toString();
    } catch (IOException e) {
      throw unchecked(e);
    }
  }

  /**
   * @return null if http status is success (200-299)
   */
  private static @Nullable String mapHttpStatusToErrorOrNull(final int httpStatus) {
    if (httpStatus >= 200 && httpStatus <= 299) return null;
    switch (httpStatus) {
      case HttpURLConnection.HTTP_INTERNAL_ERROR:
        return NakshaError.EXCEPTION;
      case HttpURLConnection.HTTP_NOT_IMPLEMENTED:
        return NakshaError.NOT_IMPLEMENTED;
      case HttpURLConnection.HTTP_BAD_REQUEST:
        return NakshaError.ILLEGAL_ARGUMENT;
      case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
        return NakshaError.PAYLOAD_TOO_LARGE;
      case HttpURLConnection.HTTP_BAD_GATEWAY:
        return NakshaError.BAD_GATEWAY;
      case HttpURLConnection.HTTP_CONFLICT:
        return NakshaError.CONFLICT;
      case HttpURLConnection.HTTP_UNAUTHORIZED:
        return NakshaError.UNAUTHORIZED;
      case HttpURLConnection.HTTP_FORBIDDEN:
        return NakshaError.FORBIDDEN;
      case 429:
        return NakshaError.TOO_MANY_REQUESTS;
      case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
        return NakshaError.TIMEOUT;
      case HttpURLConnection.HTTP_NOT_FOUND:
        return NakshaError.NOT_FOUND;
      default:
        throw new IllegalArgumentException("Http status code is not a known error code: " + httpStatus);
    }
  }

  public static final Function<Object, List<NakshaFeature>> collectionMapper = tuples ->
          JvmBoxingUtil.box(tuples, XyzFeatureCollection.class).getFeatures();

  public static final Function<Object, List<NakshaFeature>> singleFeatureMapper = tuples ->
          List.of(JvmBoxingUtil.box(tuples, NakshaFeature.class));
}
