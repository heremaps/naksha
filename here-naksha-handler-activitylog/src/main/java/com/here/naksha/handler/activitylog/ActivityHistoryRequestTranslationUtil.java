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
package com.here.naksha.handler.activitylog;

import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.handlers.util.PropertyOperationUtil;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ActivityHistoryRequestTranslationUtil {

  private ActivityHistoryRequestTranslationUtil() {}

  /**
   * Mutates given ReadFeatures request by translating equality Property Operations for specific property refs.
   * Translation is about moving source equality Property Operation to target one.
   * After translation is applied the target PRef exists with source POp value and the source POp is removed.
   * <br>
   * Translation applies to given source-target pairs:
   * <ul>
   * <li>'id' ({{@link PRef#id()}} => 'properties.@ns:com:here:xyz.uuid' ({{@link PRef#uuid()}})</li>
   * <li>'properties.@ns:com:here:xyz:log.id' ({{@link PRef#activityLogId()}}) => 'id' ({{@link PRef#id()}})</li>
   * </ul>
   * Translation is required because the ReadRequest that reach {{@link ActivityHistoryHandler}} are being delegated to HistoryHandler
   *
   * @param readFeatures ReadFeatures bearing potential POp to be translated (request will be mutated after this operation!)
   */
  static void translatePropertyOperation(ReadFeatures readFeatures) {
    POp pOp = readFeatures.getPropertyOp();
    if (pOp != null) {
      if (hasChildren(pOp)) {
        PropertyOperationUtil.transformPropertyInPropertyOperationTree(
            pOp, ActivityHistoryRequestTranslationUtil::translateIfApplicable);
      } else {
        translateIfApplicable(pOp).ifPresent(readFeatures::setPropertyOp);
      }
    }
  }

  private static Optional<POp> translateIfApplicable(POp pOp) {
    if (isSingleIdEqualityQuery(pOp)) {
      String featureUuid = (String) pOp.getValue();
      return Optional.of(uuidMustMatch(featureUuid));
    } else if (isSingleActivityLogIdEqualityQuery(pOp)) {
      String activityLogId = (String) pOp.getValue();
      return Optional.of(idMustMatch(activityLogId));
    }
    return Optional.empty();
  }

  private static boolean hasChildren(POp pOp) {
    List<POp> maybeChildren = pOp.children();
    return maybeChildren != null && !maybeChildren.isEmpty();
  }

  private static boolean isSingleIdEqualityQuery(@NotNull POp pOp) {
    return pOp.op().equals(POpType.EQ) && sameRefs(PRef.id(), pOp.getPropertyRef());
  }

  private static boolean isSingleActivityLogIdEqualityQuery(POp pOp) {
    return pOp.op().equals(POpType.EQ) && sameRefs(PRef.activityLogId(), pOp.getPropertyRef());
  }

  private static boolean sameRefs(@NotNull PRef expected, @Nullable PRef actual) {
    return actual != null && expected.getPath().equals(actual.getPath());
  }

  private static POp uuidMustMatch(String desiredUuid) {
    return POp.eq(PRef.uuid(), desiredUuid);
  }

  private static POp idMustMatch(String desiredId) {
    return POp.eq(PRef.id(), desiredId);
  }
}
