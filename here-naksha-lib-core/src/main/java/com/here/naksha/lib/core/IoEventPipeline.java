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
package com.here.naksha.lib.core;

import static naksha.model.NakshaErrorCode.EXCEPTION;

import com.here.naksha.lib.core.models.payload.Payload;
import com.here.naksha.lib.core.util.NanoTime;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import naksha.base.Platform;
import naksha.base.ToJsonOptions;
import naksha.model.NakshaError;
import naksha.model.request.Request;
import naksha.model.response.ErrorResponse;
import naksha.model.response.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for an event pipeline that read the request from an {@link InputStream} and write the result to an
 * {@link OutputStream}; using JSON representation.
 */
public class IoEventPipeline extends EventPipeline {

  private static final Logger log = LoggerFactory.getLogger(IoEventPipeline.class);

  /**
   * The maximal size of uncompressed bytes, exceeding leads to the response getting gzipped.
   */
  protected int GZIP_THRESHOLD_SIZE = 128 * 1024;

  /**
   * The minimal size of uncompressed bytes before starting the if-none-match/e-tag handling. Note that e-tag handling is expensive as it
   * requires to re-encode buffers (so it stresses the garbage collector).
   */
  protected int ETAG_THRESHOLD_SIZE = 32 * 1024;

  /**
   * Create a new IO bound event pipeline. Requires invoking {@link #sendEvent(InputStream, OutputStream)} to start the event processing.
   *
   * @param naksha The naksha host.
   */
  public IoEventPipeline(@NotNull INaksha naksha) {
    super(naksha);
  }

  private final AtomicBoolean sendingEvent = new AtomicBoolean();

  /**
   * To be invoked to process the event. Start reading the event form the given input stream, invoke the
   * {@link EventPipeline#sendEvent(Request)} method and then encode the response and write it to the output.
   *
   * @param input  the input stream to read the event from.
   * @param output the output stream to write the response to.
   * @return the response that was encoded to the output stream.
   * @throws IllegalStateException if this method has already been called.
   */
  public Response sendEvent(@NotNull InputStream input, @Nullable OutputStream output) {
    if (!sendingEvent.compareAndSet(false, true)) {
      throw new IllegalStateException("process must not be called multiple times");
    }
    Response response;
    final Request<?> request;
    try {
      String requestString;
      final long START = NanoTime.now();
      input = Payload.prepareInputStream(input);
      try (final Scanner scanner = new Scanner(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        requestString = scanner.useDelimiter("\\A").next();
      }
      final Object rawRequest = JsonSerializable.deserialize(requestString);
      if (rawRequest == null) {
        throw new NullPointerException();
      }
      final long parsedInMillis = NanoTime.timeSince(START, TimeUnit.MILLISECONDS);
      if (rawRequest instanceof Request<?>) {
        request = (Request<?>) rawRequest;
        log.atInfo()
            .setMessage("Event parsed in {}ms")
            .addArgument(parsedInMillis)
            .log();
        log.atDebug()
            .setMessage("Event raw string: {}")
            .addArgument(requestString)
            .log();
        //noinspection UnusedAssignment
        requestString = null;
        // Note: We explicitly set the rawEvent to null to ensure that the garbage collector can
        // collect the variables.
        //       Not doing so, could theoretically allow the compiler to keep a reference to the
        // memory until the method left.
        response = sendEvent(request);
        if (output != null) {
          writeDataOut(output, response, null);
        }
      } else {
        final String expected = Event.class.getSimpleName();
        final String deserialized = rawRequest.getClass().getSimpleName();
        log.atInfo()
            .setMessage("Event parsed in {}ms, but expected {}, deserialized {}")
            .addArgument(parsedInMillis)
            .addArgument(expected)
            .addArgument(deserialized)
            .log();
        response = new ErrorResponse(new NakshaError(
            EXCEPTION, "Invalid event, expected " + expected + ", but found " + deserialized, null, null));
        if (output != null) {
          writeDataOut(output, response, null);
        }
      }
    } catch (Throwable e) {
      log.atWarn()
          .setMessage("Exception while processing the event")
          .setCause(e)
          .log();
      response = new ErrorResponse(new NakshaError(EXCEPTION, e.getMessage(), null, null));
      if (output != null) {
        writeDataOut(output, response, null);
      }
    } finally {
      sendingEvent.set(false);
    }
    return response;
  }

  /**
   * Write the output object to the output stream.
   */
  private void writeDataOut(@NotNull OutputStream output, @NotNull Response dataOut, @Nullable String ifNoneMatch) {
    try {
      byte @NotNull [] bytes =
          Platform.toJSON(dataOut, ToJsonOptions.getDEFAULT()).getBytes(StandardCharsets.UTF_8);
      log.atInfo()
          .setMessage("Write data out for response with type: {}")
          .addArgument(dataOut.getClass().getSimpleName())
          .log();
      output.write(bytes);
    } catch (Exception e) {
      log.atError()
          .setMessage("Unexpected exception while writing to output stream")
          .setCause(e)
          .log();
    }
  }
}
