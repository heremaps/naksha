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

class ActivityHistoryRequestTransformUtil {

  private ActivityHistoryRequestTransformUtil() {}

  static void translateIdPropertyToFeatureUuid(ReadFeatures readFeatures) {
    POp pOp = readFeatures.getPropertyOp();
    if (pOp != null) {
      if (hasChildren(pOp)) {
        PropertyOperationUtil.transformPropertyInPropertyOperationTree(
            pOp, ActivityHistoryRequestTransformUtil::translateIfApplicable);
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
    return pOp.op().equals(POpType.EQ) && PRef.id().equals(pOp.getPropertyRef());
  }

  private static boolean isSingleActivityLogIdEqualityQuery(POp pOp) {
    return pOp.op().equals(POpType.EQ) && PRef.activityLogId().equals(pOp.getPropertyRef());
  }

  private static POp uuidMustMatch(String desiredUuid) {
    return POp.eq(PRef.uuid(), desiredUuid);
  }

  private static POp idMustMatch(String desiredId) {
    return POp.eq(PRef.id(), desiredId);
  }
}
