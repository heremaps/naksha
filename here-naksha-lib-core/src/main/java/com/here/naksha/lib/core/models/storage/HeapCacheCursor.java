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

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;

/**
 * HeapCacheCursor reads all data (up to provided limit) ahead of time to memory, and that allows to go back and forth.
 * It's not thread-safe in any way, {@link SeekableCursor} is designed to jump to any position of the cursor, so it doesn't really make sense to use it in parallel.
 * Use {@link HeapCacheCursor} only when you need to walk through results multiple times, if you need to read data only once - please use {@link ForwardCursor} as it consumes much less memory.
 *
 * @param <FEATURE>
 * @param <CODEC>
 */
public class HeapCacheCursor<FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>>
    extends SeekableCursor<FEATURE, CODEC> {

  protected static final long BEFORE_FIRST_POSITION = -1;

  protected final ForwardCursor<FEATURE, CODEC> originalCursor;

  protected final Map<Long, ForwardCursor<FEATURE, CODEC>.Row> inMemoryData = new HashMap<>();

  protected final long limit;
  protected final boolean reOrder;
  protected long positionOfLastElement = BEFORE_FIRST_POSITION;

  public HeapCacheCursor(
      @NotNull FeatureCodecFactory<FEATURE, CODEC> codecFactory,
      long limit,
      boolean reOrder,
      @NotNull ForwardCursor<FEATURE, CODEC> originalCursor) {
    super(codecFactory);
    this.originalCursor = originalCursor;
    this.position = originalCursor.position;
    this.limit = limit;
    // TODO FIXME re-order elements if needed
    this.reOrder = reOrder;

    final boolean isReadAllRequested = limit == -1;

    long count = 0;
    while (originalCursor.hasNext() && (isReadAllRequested || count < limit)) {
      originalCursor.next();
      inMemoryData.put(originalCursor.position, new Row(originalCursor.currentRow));
      positionOfLastElement = originalCursor.position;
      count++;
    }
  }

  @Override
  public @NotNull FEATURE getFeature() throws NoSuchElementException {
    if (this.position > this.positionOfLastElement) {
      throw new NoSuchElementException("Position is over last element");
    }
    if (this.positionOfLastElement == BEFORE_FIRST_POSITION) {
      throw new NoSuchElementException("Empty rs");
    }
    this.currentRow = inMemoryData.get(this.position);
    return super.getFeature();
  }

  @Override
  public boolean next() {
    if (hasNext()) {
      this.position++;
      return true;
    }
    return false;
  }

  @Override
  public boolean hasNext() {
    return this.position < this.positionOfLastElement;
  }

  @Override
  protected boolean loadNextRow(ForwardCursor<FEATURE, CODEC>.@NotNull Row row) {
    throw new RuntimeException("Should not be implemented, in cache cursor use only hasNext() and next()");
  }

  @Override
  public void close() {
    originalCursor.close();
  }

  @Override
  public boolean previous() {
    if (this.position > BEFORE_FIRST_POSITION) {
      this.position--;
      return this.position > BEFORE_FIRST_POSITION;
    }
    return false;
  }

  @Override
  public void beforeFirst() {
    this.position = -1;
  }

  @Override
  public boolean first() {
    if (positionOfLastElement > -1) {
      this.position = 0;
      return true;
    }
    return false;
  }

  @Override
  public void afterLast() {
    this.position = positionOfLastElement + 1;
  }

  @Override
  public boolean last() {
    this.position = this.positionOfLastElement;
    return this.position > BEFORE_FIRST_POSITION;
  }

  @Override
  public boolean relative(long amount) {
    return absolute(this.position + amount);
  }

  @Override
  public boolean absolute(long position) {
    if (position > this.positionOfLastElement) {
      afterLast();
      return false;
    }
    if (position <= BEFORE_FIRST_POSITION) {
      beforeFirst();
      return false;
    }
    this.position = position;
    return true;
  }
}
