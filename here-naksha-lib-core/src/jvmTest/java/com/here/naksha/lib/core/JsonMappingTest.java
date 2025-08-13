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
package com.here.naksha.lib.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import naksha.base.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class JsonMappingTest {

  @Test
  public void testDeserializeFeature() {
    final String json = "{\"type\":\"Feature\", \"id\": \"xyz123\", \"properties\":{\"x\":5}, \"otherProperty\": \"123\"}";
    final var obj = Platform.fromJson(json, NakshaFeature.TYPE);
    assertNotNull(obj);

    assertEquals(5, (int) obj.getProperties().get("x"));
    assertEquals("123", obj.get("otherProperty"));
  }

  @Test
  public void testSerializeFeature() throws Exception {
      final String raw = "{\"type\":\"Feature\", \"id\": \"xyz123\", \"properties\":{\"x\":5}}";
      final NakshaFeature obj = Platform.fromJson(raw, NakshaFeature.TYPE);
      assertNotNull(obj);

      obj.getProperties().put("y", 7);
      String result = Platform.toJson(obj, ToJsonOptions.DEFAULT);

      final String expected = "{\"type\":\"Feature\",\"id\":\"xyz123\",\"properties\":{\"x\":5,\"y\":7}}";
      assertTrue(jsonCompare(expected, result));
  }

  private boolean jsonCompare(@SuppressWarnings("SameParameterValue") String string1, String string2)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode tree1 = mapper.readTree(string1);
    JsonNode tree2 = mapper.readTree(string2);
    return tree1.equals(tree2);
  }

  @Test
  public void testResponseParsing() {
    // TODO: This should work without initializing the ErrorResponse, we need to add the same hack into lib-model, that we did in lib-geo !!!
    ErrorResponse.TYPE.initialize();
    // final var json = "{\"type\":\"ErrorResponse\",\"error\":\"NotImplemented\",\"errorMessage\":\"Hello World!\"}";
    final var json = "{\"type\":\"ErrorResponse\",\"error\":{\"code\":\"NotImplemented\",\"msg\":\"Hello World!\"}}";
    final var some = Platform.fromJson(json);
    assertNotNull(some);
    final var response = assertInstanceOf(ErrorResponse.class, some);
    final var error = response.getError();
    assertEquals(NakshaError.NOT_IMPLEMENTED, error.getCode());
    assertEquals("Hello World!", error.getMsg());
  }

//  @Test
//  public void testNativeAWSLambdaErrorMessage() throws Exception {
//    final String json =
//        "{\"error\":{\"message\":\"2018-09-15T07:12:25.013Z a368c0ea-b8b6-11e8-b894-eb5a7755e998 Task timed out after 25.01 seconds\"}}";
//    ErrorResponse obj = new ErrorResponse(new NakshaError(NakshaErrorCode.EXCEPTION,"",null,null));
//    obj = new ObjectMapper().readerForUpdating(obj).readValue(json);
//    assertNotNull(obj);
//    obj = JsonSerializable.fixAWSLambdaResponse(obj);
//    assertSame(NakshaErrorCode.TIMEOUT, obj.error.code);
//    assertEquals(
//        "2018-09-15T07:12:25.013Z a368c0ea-b8b6-11e8-b894-eb5a7755e998 Task timed out after 25.01 seconds",
//        obj.error.message);
//  }
}
