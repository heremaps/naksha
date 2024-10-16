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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.here.naksha.lib.core.IoEventPipeline;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByIdEvent;
import com.here.naksha.lib.core.models.payload.events.info.HealthCheckEvent;
import com.here.naksha.lib.core.models.payload.responses.ErrorResponse;
import com.here.naksha.lib.core.models.payload.responses.HealthStatus;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.core.view.ViewSerialize;
import com.here.naksha.lib.extension.MockHttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpHandlerTest {

  static EventHandler eventHandler;
  static IoEventPipeline eventPipeline;
  static HttpHandler httpHandler;

  @BeforeAll
  static void setup() throws XyzErrorException, IOException {
    eventHandler = new EventHandler("test:http", HttpHandler.class);
    String url = "http://localhost:9999/";
    try {
      // Set the env var below if you want to run the test against a specific endpoint
      final String rawUrl = System.getenv("HTTP_HANDLER_TEST_URI");
      final URL goodURL = new URL(rawUrl);
      url = goodURL.toString();
    } catch (Exception ignore) {
    }
    eventHandler.getProperties().put(HttpHandlerParams.URL, url);
    eventHandler.getProperties().put(HttpHandlerParams.HTTP_METHOD, HttpHandlerParams.HTTP_GET);
    //noinspection ConstantConditions
    eventPipeline = new IoEventPipeline(null);
    httpHandler = new HttpHandler(eventHandler);
    eventPipeline.addEventHandler(httpHandler);
    fakeWebserver = new MockHttpServer(9999);
  }

  @AfterAll
  public static void stopWebServer() {
    if (fakeWebserver != null) {
      fakeWebserver.server.stop(0);
      fakeWebserver = null;
    }
  }

  static MockHttpServer fakeWebserver;

  @Test
  void test_GetFeaturesById() throws IOException {
    final GetFeaturesByIdEvent event = new GetFeaturesByIdEvent();
    final List<String> ids = new ArrayList<>();
    ids.add("a");
    ids.add("b");
    event.setIds(ids);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    eventPipeline.sendEvent(new ByteArrayInputStream(event.toByteArray(ViewSerialize.Internal.class)), out);
    final XyzResponse response = JsonSerializable.deserialize(out.toByteArray(), XyzResponse.class);
    assertNotNull(response);
    final ErrorResponse errorResponse = assertInstanceOf(ErrorResponse.class, response);
    assertSame(XyzError.NOT_IMPLEMENTED, errorResponse.getError());
  }

  @Test
  public void test_HealthCheckEvent() throws IOException {
    final HealthCheckEvent event = new HealthCheckEvent();
    event.setSpace(new Space("ASpaceThatShouldNotExistBecauseWeAreTesting"));
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    eventPipeline.sendEvent(new ByteArrayInputStream(event.toByteArray(ViewSerialize.Internal.class)), out);
    //    eventPipeline.sendEvent(IoHelp.openResource("testevent.json"), out);
    final XyzResponse response = JsonSerializable.deserialize(out.toByteArray(), XyzResponse.class);
    assertNotNull(response);
    assertInstanceOf(HealthStatus.class, response);
  }
}
