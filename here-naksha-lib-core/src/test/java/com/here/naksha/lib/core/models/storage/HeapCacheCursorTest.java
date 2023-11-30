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
package com.here.naksha.lib.core.models.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import org.junit.jupiter.api.Test;

public class HeapCacheCursorTest {

  @Test
  void testAlreadyUsedFrowardCursorConversion() {
    // given
    ForwardCursor<XyzFeature, XyzFeatureCodec> cursor = infiniteForwardCursor();

    // when
    cursor.next();

    // expect
    assertThrows(
        IllegalStateException.class,
        () -> cursor.asSeekableCursor(10, false),
        "Cannot create seekable cursor after reading rows from ForwardCursor");
  }

  @Test
  void testLimitCacheElements() {
    // given
    long limit = 100;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // when
    long count = 0;
    for (XyzFeature row : cursor) {
      assertNotNull(row.getId());
      count++;
    }

    // then
    assertEquals(limit, count);
    assertFalse(cursor.hasNext());
  }

  @Test
  void testMoveCursorBeforeFirst() {
    // given
    long limit = 50;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // when
    cursor.next();
    XyzFeature firstFeature = cursor.getFeature();
    cursor.next();
    XyzFeature secondFeature = cursor.getFeature();
    cursor.beforeFirst();
    cursor.next();
    XyzFeature featureAfterBackToTop = cursor.getFeature();

    // then
    assertEquals(firstFeature, featureAfterBackToTop);
    assertNotEquals(firstFeature, secondFeature);
    assertEquals(0, cursor.position);
  }

  @Test
  void testBackToFirstElement() {
    long limit = 5;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // when
    cursor.next();
    XyzFeature firstFeature = cursor.getFeature();
    cursor.next();
    XyzFeature secondFeature = cursor.getFeature();
    cursor.first();
    XyzFeature rewindedFirst = cursor.getFeature();

    // then
    assertEquals(firstFeature, rewindedFirst);
    assertNotEquals(firstFeature, secondFeature);
    assertEquals(0, cursor.position);
  }

  @Test
  void testGoToLast() {
    long limit = 5;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // when
    cursor.last();
    XyzFeature lastFeature = cursor.getFeature();
    cursor.beforeFirst();

    XyzFeature lastByIterator = null;
    while (cursor.next()) {
      lastByIterator = cursor.getFeature();
    }

    // then
    assertEquals(lastFeature, lastByIterator);
    assertEquals(4, cursor.position);
  }

  @Test
  void testAfterLast() {
    // given
    long limit = 5;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // when
    cursor.afterLast();

    // then
    assertFalse(cursor.hasNext());
    assertEquals(limit, cursor.position);
  }

  @Test
  void testRelativeMove() {
    // given
    long limit = 5;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // expect
    assertEquals(-1, cursor.position);

    assertTrue(cursor.next());
    assertEquals(0, cursor.position);

    assertTrue(cursor.relative(2));
    assertEquals(2, cursor.position);

    assertTrue(cursor.relative(-1));
    assertEquals(1, cursor.position);

    assertFalse(cursor.relative(-5));
    assertEquals(-1, cursor.position);

    assertFalse(cursor.relative(10));
    assertEquals(limit, cursor.position);
  }

  @Test
  void testAbsoluteMove() {
    // given
    long limit = 5;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // expect
    assertEquals(-1, cursor.position);

    assertTrue(cursor.next());
    assertEquals(0, cursor.position);

    assertTrue(cursor.absolute(2));
    assertEquals(2, cursor.position);

    assertTrue(cursor.absolute(1));
    assertEquals(1, cursor.position);

    assertFalse(cursor.absolute(-5));
    assertEquals(-1, cursor.position);

    assertFalse(cursor.absolute(10));
    assertEquals(limit, cursor.position);
  }

  @Test
  void testPrevious() {
    // given
    long limit = 5;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // expect
    assertTrue(cursor.next());
    assertTrue(cursor.next());
    assertEquals(1, cursor.position);

    assertTrue(cursor.previous());
    assertEquals(0, cursor.position);

    assertFalse(cursor.previous());
    assertEquals(-1, cursor.position);
  }

  @Test
  void testLimit0() {
    // given
    long limit = 0;
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
        infiniteForwardCursor().asSeekableCursor(limit, false);

    // expect
    assertFalse(cursor.hasNext());
  }

  @Test
  void testEmptyRs() {
    // given
    EmptyForwardCursor<XyzFeature, XyzFeatureCodec> emptyForwardCursor =
        new EmptyForwardCursor<>(XyzFeatureCodecFactory.get());

    // when
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor = emptyForwardCursor.asSeekableCursor(5, false);

    // then
    assertFalse(cursor.hasNext());
  }

  @Test
  void testCacheAllAvailableRows() {
    // given
    long rsSize = 10;
    LimitedForwardCursor<XyzFeature, XyzFeatureCodec> limitedForwardCursor =
        new LimitedForwardCursor<>(XyzFeatureCodecFactory.get(), rsSize);

    // when
    SeekableCursor<XyzFeature, XyzFeatureCodec> cursor = limitedForwardCursor.asSeekableCursor(-1, false);
    cursor.afterLast();

    // then
    assertEquals(rsSize, cursor.position);
  }

  private InfiniteForwardCursor<XyzFeature, XyzFeatureCodec> infiniteForwardCursor() {
    return new InfiniteForwardCursor<>(XyzFeatureCodecFactory.get());
  }
}
