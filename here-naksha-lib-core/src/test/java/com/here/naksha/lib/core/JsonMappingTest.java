///*
// * Copyright (C) 2017-2023 HERE Europe B.V.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// *
// * SPDX-License-Identifier: Apache-2.0
// * License-Filename: LICENSE
// */
//package com.here.naksha.lib.core;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertSame;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.here.naksha.lib.core.util.json.Json;
//import com.here.naksha.lib.core.util.json.JsonSerializable;
//import com.here.naksha.lib.core.view.ViewDeserialize.All;
//import java.io.IOException;
//
//import naksha.model.NakshaError;
//import naksha.model.NakshaErrorCode;
//import naksha.model.NakshaFeatureProxy;
//import naksha.model.request.ErrorResponse;
//import org.junit.jupiter.api.Test;
//
//@SuppressWarnings("unused")
//public class JsonMappingTest {
//
//  @Test
//  public void testDeserializeFeature() throws Exception {
//    final String json =
//        "{\"type\":\"Feature\", \"id\": \"xyz123\", \"properties\":{\"x\":5}, \"otherProperty\": \"123\"}";
//    final NakshaFeatureProxy obj = new ObjectMapper().readValue(json, NakshaFeatureProxy.class);
//    assertNotNull(obj);
//
//    assertEquals(5, (int) obj.getProperties().get("x"));
//    assertEquals("123", obj.get("otherProperty"));
//  }
//
//  @Test
//  public void testSerializeFeature() throws Exception {
//    try (final Json json = Json.get()) {
//      final String raw = "{\"type\":\"Feature\", \"id\": \"xyz123\", \"properties\":{\"x\":5}}";
//      final NakshaFeatureProxy obj = json.reader(All.class).readValue(raw, NakshaFeatureProxy.class);
//      assertNotNull(obj);
//
//      obj.getProperties().put("y", 7);
//      //noinspection DataFlowIssue
//      obj.getProperties().setXyz(null);
//      String result = obj.toString();
//
//      final String expected = "{\"type\":\"Feature\",\"id\":\"xyz123\",\"properties\":{\"x\":5,\"y\":7}}";
//      assertTrue(jsonCompare(expected, result));
//    }
//  }
//
//  private boolean jsonCompare(@SuppressWarnings("SameParameterValue") String string1, String string2)
//      throws IOException {
//    ObjectMapper mapper = new ObjectMapper();
//    JsonNode tree1 = mapper.readTree(string1);
//    JsonNode tree2 = mapper.readTree(string2);
//    return tree1.equals(tree2);
//  }
//
//  @Test
//  public void testResponseParsing() throws Exception {
//    final String json =
//        "{\"type\":\"ErrorResponse\",\"error\":\"NotImplemented\",\"errorMessage\":\"Hello World!\"}";
//    final ErrorResponse obj = new ObjectMapper().readValue(json, ErrorResponse.class);
//    assertNotNull(obj);
//    assertSame(NakshaErrorCode.NOT_IMPLEMENTED, obj.error.code);
//    assertEquals("Hello World!", obj.error.message);
//  }
//
//  @Test
//  public void testNativeAWSLambdaErrorMessage() throws Exception {
//    final String json =
//        "{\"errorMessage\":\"2018-09-15T07:12:25.013Z a368c0ea-b8b6-11e8-b894-eb5a7755e998 Task timed out after 25.01 seconds\"}";
//    ErrorResponse obj = new ErrorResponse(new NakshaError(NakshaErrorCode.EXCEPTION,"",null,null));
//    obj = new ObjectMapper().readerForUpdating(obj).readValue(json);
//    assertNotNull(obj);
//    obj = JsonSerializable.fixAWSLambdaResponse(obj);
//    assertSame(NakshaErrorCode.TIMEOUT, obj.error.code);
//    assertEquals(
//        "2018-09-15T07:12:25.013Z a368c0ea-b8b6-11e8-b894-eb5a7755e998 Task timed out after 25.01 seconds",
//        obj.error.message);
//  }
//}
