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

public class NonIndexedPOp extends Op<NonIndexedPOp> {

  private final @Nullable NonIndexedPRef propertyRef;
  private final @Nullable Object value;

  NonIndexedPOp(@NotNull OpType op, @Nullable NonIndexedPRef propertyRef, @Nullable Object value) {
    super(op);
    this.propertyRef = propertyRef;
    this.value = value;
  }

  public NonIndexedPRef getPropertyRef() {
    return propertyRef;
  }

  public Object getValue() {
    return value;
  }

  public static @NotNull NonIndexedPOp exists(@NotNull NonIndexedPRef propertyRef) {
    return new NonIndexedPOp(EXISTS, propertyRef, null);
  }

  public static @NotNull NonIndexedPOp startsWith(@NotNull NonIndexedPRef propertyRef, @NotNull String prefix) {
    return new NonIndexedPOp(STARTS_WITH, propertyRef, prefix);
  }

  public static @NotNull NonIndexedPOp eq(@NotNull NonIndexedPRef propertyRef, @NotNull String value) {
    return new NonIndexedPOp(EQ, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp eq(@NotNull NonIndexedPRef propertyRef, @NotNull Number value) {
    return new NonIndexedPOp(EQ, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp eq(@NotNull NonIndexedPRef propertyRef, @NotNull Boolean value) {
    return new NonIndexedPOp(EQ, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp gt(@NotNull NonIndexedPRef propertyRef, @NotNull Number value) {
    return new NonIndexedPOp(GT, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp gte(@NotNull NonIndexedPRef propertyRef, @NotNull Number value) {
    return new NonIndexedPOp(GTE, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp lt(@NotNull NonIndexedPRef propertyRef, @NotNull Number value) {
    return new NonIndexedPOp(LT, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp lte(@NotNull NonIndexedPRef propertyRef, @NotNull Number value) {
    return new NonIndexedPOp(LTE, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp isNull(@NotNull NonIndexedPRef propertyRef) {
    return new NonIndexedPOp(NULL, propertyRef, null);
  }

  public static @NotNull NonIndexedPOp isNotNull(@NotNull NonIndexedPRef propertyRef) {
    return new NonIndexedPOp(NOT_NULL, propertyRef, null);
  }

  public static @NotNull NonIndexedPOp contains(@NotNull NonIndexedPRef propertyRef, @NotNull Number value) {
    return new NonIndexedPOp(CONTAINS, propertyRef, value);
  }

  public static @NotNull NonIndexedPOp contains(@NotNull NonIndexedPRef propertyRef, @NotNull String value) {
    return new NonIndexedPOp(CONTAINS, propertyRef, value);
  }
}
