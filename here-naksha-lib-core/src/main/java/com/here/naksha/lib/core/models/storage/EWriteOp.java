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

import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.util.json.JsonEnum;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The write operations that should be performed.
 */
@SuppressWarnings("unused")
@AvailableSince(NakshaVersion.v2_0_7)
public class EWriteOp extends JsonEnum {

  /**
   * Returns the write operation that matches the given character sequence.
   *
   * @param chars The character sequence to translate.
   * @return The write operation.
   */
  public static @NotNull EWriteOp get(@Nullable CharSequence chars) {
    return get(EWriteOp.class, chars);
  }

  /**
   * A helper to detect {@code null} values, which are not allowed.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static final EWriteOp NULL = def(EWriteOp.class, null);

  /**
   * Create a new feature. Requires that the {@link FeatureCodec#feature} is provided as parameter. Before being executed by the storage,
   * the storage will invoke {@link FeatureCodec#decodeParts(boolean)} to disassemble the feature into its parts.
   * <p>
   * If no {@link FeatureCodec#id} is decoded, generates a random identifier for the feature. If a feature with this {@code id} exists,
   * results if an error.
   */
  public static final @NotNull EWriteOp CREATE = def(EWriteOp.class, "CREATE");

  /**
   * Update an existing feature. Requires that the {@link FeatureCodec#feature} is provided as parameter. Before being executed by the
   * storage, the storage will invoke {@link FeatureCodec#decodeParts(boolean)} to disassemble the feature into its parts.
   *
   * <p>Requires that an {@link FeatureCodec#id} is decoded, otherwise an error will be the result. If a {@link FeatureCodec#uuid} is
   * decoded form the {@link FeatureCodec#feature}, then the operation becomes atomic. That means, it requires that the current version is
   * exactly the on referred to by the given {@link FeatureCodec#uuid}. If this is not the case, it will fail. If {@link FeatureCodec#uuid}
   * is {@code null}, then the operation only requires that the features exist, no matter in what state, it will be overridden by the new
   * version.
   */
  public static final @NotNull EWriteOp UPDATE = def(EWriteOp.class, "UPDATE");

  /**
   * Create or update a feature. Requires that the {@link FeatureCodec#feature} is provided as parameter. Before being executed by the
   * storage, the storage will invoke {@link FeatureCodec#decodeParts(boolean)} to disassemble the feature into its parts.
   *
   * <p>If no {@link FeatureCodec#id} is decoded, a random {@code id} will be generated. This operation will first try to create the
   * feature, if this fails, because a feature with the same {@code id} exist already, it will try to update the existing feature. In this
   * case, it will exactly behave like described in {@link #UPDATE}. So only in this case, the {@link FeatureCodec#uuid} is taken into
   * consideration.
   */
  public static final @NotNull EWriteOp PUT = def(EWriteOp.class, "PUT");

  /**
   * Delete a feature. If a {@link FeatureCodec#feature} is provided as parameter, then before being executed by the storage, the storage
   * will invoke {@link FeatureCodec#decodeParts(boolean)} to disassemble the feature into its parts. However, if no
   * {@link FeatureCodec#feature} is provided (so being {@code null}), the operation requires that at least an {@link FeatureCodec#id} is
   * provided. If additionally an {@link FeatureCodec#uuid} is provided, it makes the operation atomic.
   *
   * <p>If not being atomic, so no {@link FeatureCodec#uuid} ({@code null}) given, the feature with the given {@link FeatureCodec#id} will
   * be deleted, no matter in which version it exists. If being atomic, so a {@link FeatureCodec#uuid} was given, then the feature with
   * the given {@link FeatureCodec#id} will only be deleted, if it exists in the requested version.
   *
   * <p>The operation is generally treated as successful, when the outcome of the operation is that the feature is eventually deleted. So,
   * if the feature did not exist (was already deleted), the operation will return as the executed operation {@link EExecutedOp#RETAINED}
   * with the {@link FeatureCodec#feature} returned being {@code null}. If the features existed, then two outcomes are possible. Either the
   * operation succeeds, returning the executed operation {@link EExecutedOp#DELETED} with the version of the feature that was deleted
   * {@link FeatureCodec#feature}, or it returns {@link EExecutedOp#ERROR} with the current version of the feature being returned. This may
   * actually only happen, when the operation is atomic and the given expected {@link FeatureCodec#uuid} does not match the one of the
   * current version stored in the storage.
   */
  public static final @NotNull EWriteOp DELETE = def(EWriteOp.class, "DELETE");

  /**
   * Delete a feature in a way that makes it impossible to restore it.
   */
  public static final @NotNull EWriteOp PURGE = def(EWriteOp.class, "PURGE");

  /**
   * Restore a deleted collection. This operation is only allowed in an {@link WriteCollections} request and fails, if the collection is not
   * deleted or if applied in a {@link WriteFeatures} request.
   */
  public static final @NotNull EWriteOp RESTORE = def(EWriteOp.class, "RESTORE");

  @Override
  protected void init() {
    register(EWriteOp.class);
  }
}
