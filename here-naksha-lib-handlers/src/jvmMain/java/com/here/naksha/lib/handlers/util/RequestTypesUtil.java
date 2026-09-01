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
package com.here.naksha.lib.handlers.util;

import naksha.model.Naksha;
import naksha.model.request.Request;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RequestTypesUtil {

  private RequestTypesUtil() {}

  /**
   * Check if the given {@link WriteRequest} only includes {@link naksha.model.request.Write} operation on {@link naksha.model.objects.NakshaFeature}.
   */
  public static boolean isOnlyWriteFeatures(@NotNull Request request) {
    if (!(request instanceof WriteRequest)) return false;
    for (Write write : ((WriteRequest) request).getWrites()) {
      // A Write operation onto the virtual "naksha~collections" means that it is a write request for
      // NakshaCollection
      if (Naksha.COLLECTIONS_COL_ID.equals(write.getCollectionId())) return false;
    }
    return true;
  }

  /**
   * Check if the given {@link WriteRequest} only includes {@link naksha.model.request.Write} operation on {@link naksha.model.objects.NakshaCollection}.
   */
  public static boolean isOnlyWriteCollections(Request request) {
    if (!(request instanceof WriteRequest)) return false;
    final List<Write> writes = ((WriteRequest) request).getWrites();
    if (writes.isEmpty()) return false;
    for (Write write : writes) {
      // A Write operation onto the virtual "naksha~collections" means that it is a write request for
      // NakshaCollection
      if (!Naksha.COLLECTIONS_COL_ID.equals(write.getCollectionId())) return false;
    }
    return true;
  }
}
