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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import naksha.base.FromJsonOptions;
import naksha.base.JvmProxyUtil;
import naksha.base.Platform;
import naksha.base.Proxy;
import naksha.model.NakshaError;
import naksha.model.Tuple;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.ResultTuple;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link Response} from {@link HttpResponse}
 */
class PrepareResult {

    static Response prepareResult(
        HttpResponse<byte[]> httpResponse,
        Function<ResultTuple, List<Tuple>> typedResponseToTupleList) {

      String error = mapHttpStatusToErrorOrNull(httpResponse.statusCode());
      if (error != null)
        return new ErrorResponse(
            new NakshaError(error, "Response http status code: " + httpResponse.statusCode(), null, null));

      Object tuples = Platform.fromJSON(prepareBody(httpResponse), FromJsonOptions.getDEFAULT());
      ResultTuple resultTuples = JvmProxyUtil.box(tuples, ResultTuple.class);
      return new SuccessResponse(resultTuples);
      return createHttpResultFromFeatureList(typedResponseToTupleList.apply(resultTuples));
    }

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

//    static SuccessResponse createHttpResultFromFeatureList(final @NotNull List<Tuple> features) {
//      final List<ResultTuple> tuples = new ArrayList<>();
//      for (final NakshaFeature feature : features) {
//        tuples.add(new ResultTuple());
//        codec.setOp(EExecutedOp.READ);
//        codec.setFeature(feature);
//        codec.setId(feature.getId());
//        codecs.add(codec);
//      }
//
//      final HeapCacheCursor<XyzFeature, XyzFeatureCodec> cursor = new HeapCacheCursor<>(codecFactory, codecs, null);
//      return new SuccessResponse(cursor);
//    }

  /**
   * @return null if http status is success (200-299)
   */
  private static @Nullable String mapHttpStatusToErrorOrNull(final int httpStatus) {
    if (httpStatus >= 200 && httpStatus <= 299) return null;
    return switch (httpStatus) {
      case HttpURLConnection.HTTP_INTERNAL_ERROR -> NakshaError.EXCEPTION;
      case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> NakshaError.NOT_IMPLEMENTED;
      case HttpURLConnection.HTTP_BAD_REQUEST -> NakshaError.ILLEGAL_ARGUMENT;
      case HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> NakshaError.PAYLOAD_TOO_LARGE;
      case HttpURLConnection.HTTP_BAD_GATEWAY -> NakshaError.BAD_GATEWAY;
      case HttpURLConnection.HTTP_CONFLICT -> NakshaError.CONFLICT;
      case HttpURLConnection.HTTP_UNAUTHORIZED -> NakshaError.UNAUTHORIZED;
      case HttpURLConnection.HTTP_FORBIDDEN -> NakshaError.FORBIDDEN;
      case 429 -> NakshaError.TOO_MANY_REQUESTS;
      case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> NakshaError.TIMEOUT;
      case HttpURLConnection.HTTP_NOT_FOUND -> NakshaError.NOT_FOUND;
      default -> throw new IllegalArgumentException("Not known http error status returned: " + httpStatus);
    };
  }
}
