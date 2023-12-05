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

import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.vividsolutions.jts.geom.Geometry;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HeapCacheCursor reads all data (up to provided limit) ahead of time to memory, and that allows to go back and forth.
 * It's not thread-safe in any way, {@link SeekableCursor} is designed to jump to any position of the cursor, so it doesn't really make sense to use it in parallel.
 * Use {@link HeapCacheCursor} only when you need to walk through results multiple times, if you need to read data only once - please use {@link ForwardCursor} as it consumes much less memory.
 *
 * @param <FEATURE>
 * @param <CODEC>
 */
public class HeapCacheCursor<FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>>
    extends MutableCursor<FEATURE, CODEC> {

  protected static final long BEFORE_FIRST_POSITION = -1;
  protected static final int INITIAL_ARRAY_SIZE_FOR_UNLIMITED_CURSOR = 126;

  protected List<FeatureCodec<FEATURE, ?>> inMemoryData;

  protected final boolean reOrder;

  public HeapCacheCursor(
      @NotNull FeatureCodecFactory<FEATURE, CODEC> codecFactory,
      long limit,
      boolean reOrder,
      @NotNull ForwardCursor<FEATURE, CODEC> originalCursor) {
    super(codecFactory);
    this.position = BEFORE_FIRST_POSITION;
    // TODO FIXME restore-order elements if needed
    this.reOrder = reOrder;

    this.inMemoryData = new ArrayList<>(initialSize(limit));

    final boolean isReadAllRequested = limit == -1;

    long count = 0;
    while (originalCursor.hasNext() && (isReadAllRequested || count < limit)) {
      originalCursor.next();
      inMemoryData.add(originalCursor.currentRow.codec);
      count++;
    }
  }

  private int initialSize(long limit) {
    if (limit > Integer.MAX_VALUE - 2) {
      throw new UnsupportedOperationException(
          format("Current implementation does not support %s cache size", limit));
    }
    return limit == -1 ? INITIAL_ARRAY_SIZE_FOR_UNLIMITED_CURSOR : toIntExact(limit);
  }

  @Override
  public @Nullable FEATURE getFeature() throws NoSuchElementException {
    if (this.position > positionOfLastElement()) {
      throw new NoSuchElementException("Position is over last element");
    }
    if (positionOfLastElement() == BEFORE_FIRST_POSITION) {
      throw new NoSuchElementException("Empty rs");
    }
    FeatureCodec<FEATURE, ?> currentPositionCodec = inMemoryData.get(toIntExact(this.position));
    return currentPositionCodec.encodeFeature(false).getFeature();
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
    return this.position < positionOfLastElement();
  }

  @Override
  protected boolean loadNextRow(ForwardCursor<FEATURE, CODEC>.@NotNull Row row) {
    throw new RuntimeException("Should not be implemented, in cache cursor use only hasNext() and next()");
  }

  @Override
  public void close() {}

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
    if (positionOfLastElement() > -1) {
      this.position = 0;
      return true;
    }
    return false;
  }

  @Override
  public void afterLast() {
    this.position = positionOfLastElement() + 1;
  }

  @Override
  public boolean last() {
    this.position = positionOfLastElement();
    return this.position > BEFORE_FIRST_POSITION;
  }

  @Override
  public boolean relative(long amount) {
    return absolute(this.position + amount);
  }

  @Override
  public boolean absolute(long position) {
    if (position > positionOfLastElement()) {
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

  @Override
  public @NotNull EExecutedOp getOp() throws NoSuchElementException {
    return EExecutedOp.get(currentPositionCodec().getOp());
  }

  @Override
  public @NotNull String getFeatureType() throws NoSuchElementException {
    return requireNonNull(currentPositionCodec().getFeatureType());
  }

  @Override
  public @Nullable String getPropertiesType() throws NoSuchElementException {
    return currentPositionCodec().getPropertiesType();
  }

  @Override
  public @NotNull String getId() throws NoSuchElementException {
    return requireNonNull(currentPositionCodec().getId());
  }

  @Override
  public @NotNull String getUuid() throws NoSuchElementException {
    return requireNonNull(currentPositionCodec().getUuid());
  }

  @Override
  public @NotNull String getJson() throws NoSuchElementException {
    return requireNonNull(currentPositionCodec().getJson());
  }

  @Override
  public byte @Nullable [] getWkb() throws NoSuchElementException {
    return currentPositionCodec().getWkb();
  }

  @Override
  public @Nullable Geometry getGeometry() throws NoSuchElementException {
    return currentPositionCodec().getGeometry();
  }

  @Override
  public boolean hasError() {
    return currentPositionCodec().hasError();
  }

  @Override
  public @Nullable CodecError getError() {
    return currentPositionCodec().getError();
  }

  @Override
  public @NotNull FEATURE addFeature(@NotNull FEATURE feature) {
    inMemoryData.add(createCodec(feature));
    return feature;
  }

  @Override
  public @Nullable FEATURE setFeature(long position, @NotNull FEATURE feature) {
    int idx = toIdx(position);
    FeatureCodec<FEATURE, ?> currentPositionCodec = inMemoryData.get(idx);
    inMemoryData.set(toIdx(position), createCodec(feature));
    return requireNonNull(currentPositionCodec.encodeFeature(false).getFeature());
  }

  @Override
  public @Nullable FEATURE setFeature(@NotNull FEATURE feature) {
    return setFeature(this.position, feature);
  }

  @Override
  public @Nullable FEATURE removeFeature() {
    return requireNonNull(removeFeature(this.position));
  }

  @Override
  public @Nullable FEATURE removeFeature(long position) {
    int idx = toIdx(position);
    FeatureCodec<FEATURE, ?> featureToRemove = inMemoryData.get(idx);
    inMemoryData.remove(idx);
    return featureToRemove.encodeFeature(false).getFeature();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <NF, NC extends FeatureCodec<NF, NC>, N_CUR extends ForwardCursor<NF, NC>> @NotNull N_CUR withCodecFactory(
      @NotNull FeatureCodecFactory<NF, NC> codecFactory, boolean reEncode) {
    for (int i = 0; i < inMemoryData.size(); i++) {
      FeatureCodec<?, ?> currentCodec = inMemoryData.get(i);
      FeatureCodec<NF, NC> newCodec = codecFactory.newInstance();
      newCodec.withParts(currentCodec);
      if (reEncode) {
        newCodec.encodeFeature(true);
      }
      inMemoryData.set(i, (FeatureCodec<FEATURE, ?>) newCodec);
    }
    return (N_CUR) this;
  }

  private int toIdx(long position) {
    if (position > positionOfLastElement()) {
      throw new NoSuchElementException("Position is over last element");
    }
    if (position <= BEFORE_FIRST_POSITION) {
      throw new NoSuchElementException("Position is before first element");
    }
    return toIntExact(position);
  }

  private int positionOfLastElement() {
    return this.inMemoryData.size() - 1;
  }

  private CODEC createCodec(FEATURE feature) {
    CODEC codec = codecFactory.newInstance();
    codec.setFeature(feature);
    return codec;
  }

  private FeatureCodec currentPositionCodec() {
    return inMemoryData.get(toIntExact(this.position));
  }
}
