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
package com.here.naksha.lib.core.util.modify;

import static com.here.naksha.lib.core.models.storage.IfExists.REPLACE;
import static naksha.diff.Patcher.calculateDifferenceOfPartialUpdate;

import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.storage.IfExists;
import com.here.naksha.lib.core.models.storage.IfNotExists;
import java.util.HashMap;
import java.util.Objects;
import naksha.diff.ConflictResolution;
import naksha.diff.Difference;
import naksha.diff.MergeConflictException;
import naksha.diff.Patcher;
import naksha.model.EXyzAction;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An entry for a feature.
 *
 * @param <FEATURE> the feature-type.
 */
public class FeatureModificationEntry<FEATURE extends NakshaFeature> {

  /**
   * The input state of the caller.
   */
  @NotNull
  FEATURE input;

  /**
   * The latest state the feature currently has in the data storage.
   */
  @Nullable
  FEATURE head;

  /**
   * The latest state the feature currently has in the data storage.
   */
  public @Nullable FEATURE head() {
    return head;
  }

  /**
   * The state onto which the caller made the modifications. This is the state against which we may perform a three-way merge, if the
   * current head state has base is a common ancestor.
   */
  @Nullable
  FEATURE base;

  /**
   * The state onto which the caller made the modifications. This is the state against which we may perform a three-way merge, if the
   * current head state has base is a common ancestor.
   */
  public @Nullable FEATURE base() {
    return base;
  }

  /**
   * The resulting target state, which should go to the data storage. May be {@code null}, either if the feature should be deleted or if the
   * no change needed.
   */
  @Nullable
  FEATURE result;

  /**
   * The resulting target state, which should go to the data storage. May be {@code null}, either if the feature should be deleted or if the
   * no change needed.
   */
  public @Nullable FEATURE result() {
    return result;
  }

  /**
   * The action to perform; {@code null} if nothing is need to be done, for example an existing feature should simply be retained.
   */
  @Nullable
  EXyzAction action;

  /**
   * The action to perform; {@code null} if nothing is need to be done, for example an existing feature should simply be retained.
   */
  public @Nullable EXyzAction action() {
    return action;
  }

  /**
   * The action to take, if the feature does have a valid head state, so this is a replacement, or a patch (input parameter).
   */
  public final @NotNull IfExists ifExists;

  /**
   * The action to take, if the feature does not have any head state, or the head state is deleted (input parameter).
   */
  public final @NotNull IfNotExists ifNotExists;

  /**
   * The conflict resolution strategy.
   */
  public final @NotNull ConflictResolution cr;

  /**
   * Tests if this entry represents an UPSERT, in this special case we do not need to load any existing features, we can simply override
   * whatever exists in the database.
   *
   * @return {@code true} if the entry represents an upsert; {@code false} otherwise.
   */
  public boolean isUpsert() {
    return ifNotExists == IfNotExists.CREATE && ifExists == REPLACE;
  }

  /**
   * When creating patches and testing for differences, we should ignore these properties from the {@link XyzNamespace}.
   */
  protected static final HashMap<@NotNull String, @NotNull Boolean> xyzNamespaceIgnore = new HashMap<>() {
    {
      put(XyzNamespace.UUID, true);
      put(XyzNamespace.PUUID, true);
      put(XyzNamespace.CREATED_AT, true);
      put(XyzNamespace.UPDATED_AT, true);
      put(XyzNamespace.RT_UTS, true);
      put(XyzNamespace.TXN, true);
      put(XyzNamespace.TXN_NEXT, true);
      put(XyzNamespace.TXN_UUID, true);
      put(XyzNamespace.ACTION, true);
      put(XyzNamespace.VERSION, true);
    }
  };

  /**
   * Wrap the user input into a modification entry.
   *
   * @param input       The user input, which can be a partial patch, or a full new state.
   * @param ifNotExists The behavior, when the feature does not yet exist.
   * @param ifExists    The behavior, when the feature does exist already.
   * @param cr          The conflict resolution to apply.
   */
  public FeatureModificationEntry(
      @NotNull FEATURE input,
      @NotNull IfNotExists ifNotExists,
      @NotNull IfExists ifExists,
      @NotNull ConflictResolution cr) {
    this.input = input;
    this.cr = cr;
    this.ifExists = ifExists;
    this.ifNotExists = ifNotExists;
  }

  /**
   * The method to test if a key should be ignored.
   *
   * @param key           The key to test for.
   * @param source        The source object, can be a child object from within the feature.
   * @param targetOrPatch The target object, or the patch object.
   * @return {@code true} if the feature should be ignored for difference and patching; {@code false} to take the key into consideration.
   */
  protected boolean ignore(@NotNull Object key, @NotNull Object source, @NotNull Object targetOrPatch) {
    if (key instanceof String && source instanceof XyzNamespace) {
      return xyzNamespaceIgnore.containsKey(key);
    }
    return false;
  }

  /**
   * Calling this method requires that all states filled correctly, so {@link #input}, {@link #head} and {@link #base}. It will generate the
   * {@link #result}, which then can be sent to the storage. The {@link XyzNamespace#getAction()} can be used to detect, which action must
   * be done.
   *
   * @return the action to perform.
   * @throws MergeConflictException If a merge failed.
   * @throws ModificationException  If any error occurred.
   */
  final @Nullable EXyzAction apply() throws ModificationException, MergeConflictException {
    if (head == null) { // IF NOT EXISTS
      if (ifNotExists == IfNotExists.RETAIN) {
        result = null;
        return action = null;
      }
      if (ifNotExists == IfNotExists.CREATE) {
        result = input;
        return action = EXyzAction.CREATE;
      }
      if (ifNotExists == IfNotExists.FAIL) {
        result = null;
        action = null;
        throw new ModificationException("The feature {" + input.getId() + "} does not exist.");
      }
      throw new ModificationException("The provided operation for ifNotExists is unknown: " + ifNotExists);
    }
    // IF EXISTS
    if (ifExists == IfExists.RETAIN) {
      result = null;
      return action = null;
    }
    if (ifExists == IfExists.REPLACE) {
      result = input;
      return action = EXyzAction.UPDATE;
    }
    if (ifExists == IfExists.DELETE) {
      result = null;
      return action = EXyzAction.DELETE;
    }
    if (ifExists == IfExists.PATCH) {
      result = patch();
      return action = result == null ? null : EXyzAction.UPDATE;
    }
    if (ifExists == IfExists.MERGE) {
      result = merge();
      return action = result == null ? null : EXyzAction.UPDATE;
    }
    if (ifExists == IfExists.FAIL) {
      result = null;
      action = null;
      throw new ModificationException("The feature {" + input.getId() + "} exists.");
    }
    throw new ModificationException("The provided operation for ifExists is unknown: " + ifExists);
  }

  private @Nullable FEATURE patch() {
    assert head != null;
    final Difference diff = calculateDifferenceOfPartialUpdate(head, input, this::ignore, true);
    if (diff == null) {
      return null;
    }
    final FEATURE result = head.copy();
    Patcher.patch(result, diff);
    return result;
  }

  private @Nullable FEATURE merge() throws MergeConflictException {
    assert head != null;

    // If the base and head are the same, we perform a replacement (this is a simple update).
    if (base == null
        || base == head
        || Objects.equals(
            base.getProperties().getXyz().getUuid(),
            input.getProperties().getXyz().getUuid())) {
      return input;
    }

    final Difference baseToHeadDiff = Patcher.getDifference(base, head);
    if (baseToHeadDiff == null) {
      // This is totally unexpected, base and head are logically the same, but have different uuids.
      // Eventually this means, that we can just treat the input as a direct modification of the
      // head.
      input.getProperties().getXyz().setUuid(head.getProperties().getXyz().getUuid());
      return input;
    }

    // Perform a three-way-merge.
    final Difference baseToInputDiff = Patcher.getDifference(base, input);
    if (baseToInputDiff == null) {
      // Eventually nothing changed, so basically the changes effectively resulting in the same head
      // state.
      return null;
    }
    final Difference mergedDiff = Patcher.mergeDifferences(baseToHeadDiff, baseToInputDiff, cr);
    final FEATURE result = base.copy();
    Patcher.patch(result, mergedDiff);
    return result;
  }
}
