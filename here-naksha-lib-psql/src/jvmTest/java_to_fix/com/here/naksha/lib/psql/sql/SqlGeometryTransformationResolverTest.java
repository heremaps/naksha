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
package com.here.naksha.lib.psql.sql;

import static com.here.naksha.lib.psql.sql.SqlGeometryTransformationResolver.addTransformation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.here.naksha.lib.core.models.storage.transformation.BufferTransformation;
import com.here.naksha.lib.core.models.storage.transformation.GeographyTransformation;
import com.here.naksha.lib.core.models.storage.transformation.GeometryTransformation;
import com.here.naksha.lib.psql.SQL;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SqlGeometryTransformationResolverTest {

  @Test
  void testNoTransformation() {
    // given
    String variablePlaceholder = "?";

    // when
    SQL sql = addTransformation(null, variablePlaceholder);

    // then
    assertEquals("?", sql.toString());
  }

  @Test
  void testBufferTransformation() {
    // given
    String variablePlaceholder = "?";
    GeometryTransformation bufferTrans = new BufferTransformation(112.21, null, null);

    // when
    SQL sql = addTransformation(bufferTrans, variablePlaceholder);

    // then
    assertEquals(" ST_Buffer(?,112.21,E'') ", sql.toString());
  }

  @Test
  void testGeographyTransformation() {
    // given
    String variablePlaceholder = "?";
    GeometryTransformation geographyTransformation = new GeographyTransformation();

    // when
    SQL sql = addTransformation(geographyTransformation, variablePlaceholder);

    // then
    assertEquals("?::geography ", sql.toString());
  }

  @Test
  void testCombinedTransformation() {
    // given
    String variablePlaceholder = "ST_Force3D(?)";
    GeometryTransformation geographyTransformation = new GeographyTransformation();
    GeometryTransformation combinedTransformation =
        new BufferTransformation(112.21, "quad_segs=8", geographyTransformation);

    // when
    SQL sql = addTransformation(combinedTransformation, variablePlaceholder);

    // then
    assertEquals(" ST_Buffer(ST_Force3D(?)::geography ,112.21,E'quad_segs=8') ", sql.toString());
  }

  @Test
  void testUnknownTransformation() {
    // given
    String variablePlaceholder = "?";
    GeometryTransformation unknownTransformation = Mockito.mock(GeometryTransformation.class);

    // expect
    assertThrows(
        UnsupportedOperationException.class,
        () -> addTransformation(unknownTransformation, variablePlaceholder));
  }
}
