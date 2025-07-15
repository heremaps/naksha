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
package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.NakshaActivityLog.ID;
import static naksha.model.objects.NakshaFeature.PROPERTIES_KEY;
import static naksha.model.objects.NakshaProperties.XYZ_ACTIVITY_LOG_NS;
import static naksha.model.objects.NakshaProperties.XYZ_KEY;

import com.here.naksha.lib.handlers.util.PropertyOperationUtil;
import java.util.Optional;

import java.util.Set;
import naksha.base.StringList;
import naksha.model.Guid;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.jetbrains.annotations.NotNull;

class ActivityLogRequestTranslationUtil {

  static final String UUID = "uuid";
  static final String PUUID = "puuid";
  static final String ACTION = "action";
  static final String CREATED_AT = "createdAt";
  static final String UPDATED_AT = "updatedAt";

  private static final String[] ACTIVITY_LOG_ID_PATH = new String[] {PROPERTIES_KEY, XYZ_ACTIVITY_LOG_NS, ID};
  private static final String[] UUID_PATH = new String[] {XYZ_KEY, UUID};
  static final Property PROPERTY_ACTIVITY_LOG_ID = new Property(ACTIVITY_LOG_ID_PATH);
  static final Property PROPERTY_UUID = new Property(UUID_PATH);

  private ActivityLogRequestTranslationUtil() {}

  /**
   * Mutates [ReadFeatures] so that it is aligned with Acitvity Log needs, more specifically
   * - we query history
   * - we query for all available versions
   * - we assume the query will always hit the collection with Space's id
   * - if singular featureId is defined, we assume its value holds guuid which will be used to extract actual featureId
   * - if no feature ids are defined, we expect activityLogId defined, that is then disabled and used as regular featureId
   *
   * @param readFeatures ReadFeatures bearing potential POp to be translated (request will be mutated after this operation!)
   */
  static void transformOriginalRequest(ReadFeatures readFeatures, String spaceId) {
    readFeatures.setQueryHistory(true);
    readFeatures.setVersions(Integer.MAX_VALUE);
    readFeatures.setCollectionIds(StringList.of(spaceId));
    propagateFeatureIdAndVersion(readFeatures);
  }

  private static void propagateFeatureIdAndVersion(ReadFeatures readFeatures){
    StringList featureIds = readFeatures.getFeatureIds();
    if(featureIds.size() == 1){
      Guid guid = Guid.fromString(featureIds.get(0));
      readFeatures.setFeatureIds(StringList.of(guid.id));
      readFeatures.setVersion(guid.tupleNumber.version);
    } else if(featureIds.isEmpty()) {
      Set<PQuery> disabledActivityLogPOps = PropertyOperationUtil.disablePQueriesInRequest(
          readFeatures.getQuery(),
          ActivityLogRequestTranslationUtil::isSingleActivityLogIdEqualityQuery
      );
      // TODO: wrong count
      if(disabledActivityLogPOps.size() == 1){
        PQuery activityLogIdProp = disabledActivityLogPOps.iterator().next();
        String idFromActivityLogNs = activityLogIdProp.getValue().toString();
        readFeatures.setFeatureIds(StringList.of(idFromActivityLogNs));
      }
    }
  }
  private static boolean isSingleActivityLogIdEqualityQuery(PQuery pQuery) {
    return StringOp.EQUALS.equals(pQuery.getOp()) && pQuery.getProperty().getPath().containsStringsInOrder(ACTIVITY_LOG_ID_PATH);
  }
}
