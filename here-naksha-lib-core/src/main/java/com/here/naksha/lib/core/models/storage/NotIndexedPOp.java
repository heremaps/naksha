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

import static com.here.naksha.lib.core.models.storage.POpType.CONTAINS;
import static com.here.naksha.lib.core.models.storage.POpType.EQ;
import static com.here.naksha.lib.core.models.storage.POpType.EXISTS;
import static com.here.naksha.lib.core.models.storage.POpType.GT;
import static com.here.naksha.lib.core.models.storage.POpType.GTE;
import static com.here.naksha.lib.core.models.storage.POpType.LT;
import static com.here.naksha.lib.core.models.storage.POpType.LTE;
import static com.here.naksha.lib.core.models.storage.POpType.NOT_NULL;
import static com.here.naksha.lib.core.models.storage.POpType.NULL;
import static com.here.naksha.lib.core.models.storage.POpType.STARTS_WITH;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NotIndexedPOp extends Op<NotIndexedPOp> {

  private final @Nullable NotIndexedPRef propertyRef;
  private final @Nullable Object value;

  NotIndexedPOp(@NotNull OpType op, @Nullable NotIndexedPRef propertyRef, @Nullable Object value) {
    super(op);
    this.propertyRef = propertyRef;
    this.value = value;
  }

  public NotIndexedPRef getPropertyRef() {
    return propertyRef;
  }

  public Object getValue() {
    return value;
  }

  public static @NotNull NotIndexedPOp exists(@NotNull NotIndexedPRef propertyRef) {
    return new NotIndexedPOp(EXISTS, propertyRef, null);
  }

  public static @NotNull NotIndexedPOp startsWith(@NotNull NotIndexedPRef propertyRef, @NotNull String prefix) {
    return new NotIndexedPOp(STARTS_WITH, propertyRef, prefix);
  }

  public static @NotNull NotIndexedPOp eq(@NotNull NotIndexedPRef propertyRef, @NotNull String value) {
    return new NotIndexedPOp(EQ, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp eq(@NotNull NotIndexedPRef propertyRef, @NotNull Number value) {
    return new NotIndexedPOp(EQ, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp eq(@NotNull NotIndexedPRef propertyRef, @NotNull Boolean value) {
    return new NotIndexedPOp(EQ, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp gt(@NotNull NotIndexedPRef propertyRef, @NotNull Number value) {
    return new NotIndexedPOp(GT, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp gte(@NotNull NotIndexedPRef propertyRef, @NotNull Number value) {
    return new NotIndexedPOp(GTE, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp lt(@NotNull NotIndexedPRef propertyRef, @NotNull Number value) {
    return new NotIndexedPOp(LT, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp lte(@NotNull NotIndexedPRef propertyRef, @NotNull Number value) {
    return new NotIndexedPOp(LTE, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp isNull(@NotNull NotIndexedPRef propertyRef) {
    return new NotIndexedPOp(NULL, propertyRef, null);
  }

  public static @NotNull NotIndexedPOp isNotNull(@NotNull NotIndexedPRef propertyRef) {
    return new NotIndexedPOp(NOT_NULL, propertyRef, null);
  }

  public static @NotNull NotIndexedPOp contains(@NotNull NotIndexedPRef propertyRef, @NotNull Number value) {
    return new NotIndexedPOp(CONTAINS, propertyRef, value);
  }

  public static @NotNull NotIndexedPOp contains(@NotNull NotIndexedPRef propertyRef, @NotNull String value) {
    return new NotIndexedPOp(CONTAINS, propertyRef, value);
  }
}
