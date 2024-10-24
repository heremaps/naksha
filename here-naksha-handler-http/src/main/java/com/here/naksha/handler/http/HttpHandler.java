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
package com.here.naksha.handler.http;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.Typed;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.Payload;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.payload.responses.ErrorResponse;
import com.here.naksha.lib.core.models.payload.responses.ModifiedEventResponse;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.core.view.ViewSerialize;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Scanner;
import org.jetbrains.annotations.NotNull;

/** The HTTP handler that sends events to a foreign host. */
public class HttpHandler implements IEventHandler {

  public static final String ID = "naksha:http"; // com.here.naksha.http.HttpHandler

  /**
   * Creates a new HTTP handler.
   *
   * @param eventHandler The connector configuration.
   * @throws XyzErrorException If any error occurred.
   */
  public HttpHandler(@NotNull EventHandler eventHandler) throws XyzErrorException {
    try {
      this.params = new HttpHandlerParams(eventHandler.getProperties());
    } catch (Exception e) {
      throw new XyzErrorException(XyzError.ILLEGAL_ARGUMENT, e.getMessage());
    }
  }

  final @NotNull HttpHandlerParams params;

  @Override
  public @NotNull XyzResponse processEvent(@NotNull IEvent eventContext) throws XyzErrorException {
    final Event event = eventContext.getRequest();
    try {
      byte @NotNull [] bytes = event.toByteArray(ViewSerialize.Internal.class);
      final HttpURLConnection conn = (HttpURLConnection) params.getUrl().openConnection();
      conn.setConnectTimeout((int) params.getConnTimeout());
      conn.setReadTimeout((int) params.getReadTimeout());
      conn.setRequestMethod(params.getHttpMethod());
      conn.setRequestProperty("Content-type", "application/json");
      if (Boolean.TRUE.equals(params.getGzip()) || params.getGzip() == null && bytes.length >= 16384) {
        bytes = Payload.compress(bytes);
        conn.setRequestProperty("Content-encoding", "gzip");
      }
      conn.setFixedLengthStreamingMode(bytes.length);
      conn.setUseCaches(false);
      conn.setDoInput(true);
      conn.setDoOutput(true);
      final OutputStream out = conn.getOutputStream();
      out.write(bytes);
      out.flush();
      out.close();
      final InputStream in = Payload.prepareInputStream(conn.getInputStream());
      final String rawEvent;
      try (final Scanner scanner = new Scanner(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        rawEvent = scanner.useDelimiter("\\A").next();
      }
      // conn.getResponseCode() == 200
      //noinspection RedundantClassCall
      final Typed deserialized = JsonSerializable.deserialize(rawEvent);
      try {
        return Objects.requireNonNull((XyzResponse) deserialized);
      } catch (ClassCastException e) {
        final Event castedEvent = Objects.requireNonNull((Event) deserialized);
        eventContext.sendUpstream(castedEvent);
        return new ModifiedEventResponse().withEvent(castedEvent);
      }
    } catch (Exception e) {
      return new ErrorResponse()
          .withStreamId(event.getStreamId())
          .withError(XyzError.ILLEGAL_ARGUMENT)
          .withErrorMessage(e.toString());
    }
  }
}
