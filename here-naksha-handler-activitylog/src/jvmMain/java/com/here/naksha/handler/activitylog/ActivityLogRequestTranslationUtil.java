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
import static naksha.model.objects.NakshaProperties.XYZ_ACTIVITY_LOG_NS;
import static naksha.model.objects.NakshaProperties.XYZ_KEY;

import com.here.naksha.lib.handlers.util.PropertyOperationUtil;
import java.util.Optional;

import naksha.base.StringList;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.IPropertyQuery;
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

  private static final String[] ACTIVITY_LOG_ID_PATH = new String[] {XYZ_ACTIVITY_LOG_NS, ID};
  private static final String[] UUID_PATH = new String[] {XYZ_KEY, UUID};
  static final Property PROPERTY_ACTIVITY_LOG_ID = new Property(ACTIVITY_LOG_ID_PATH);
  static final Property PROPERTY_UUID = new Property(UUID_PATH);

  private ActivityLogRequestTranslationUtil() {}

  /**
   * Mutates given ReadFeatures request by translating equality Property Operations for specific property refs.
   * Translation is about moving source equality Property Operation to target one.
   * After translation is applied the target PRef exists with source POp value and the source POp is removed.
   * <br>
   * Translation applies to given source-target pairs:
   * <ul>
   * <li>'id' => 'properties.@ns:com:here:xyz.uuid' </li>
   * <li>'properties.@ns:com:here:xyz:log.id' => 'id' </li>
   * </ul>
   * Translation is required because the ReadRequest that reach {{@link ActivityLogHandler}} are being delegated to HistoryHandler
   *
   * @param readFeatures ReadFeatures bearing potential POp to be translated (request will be mutated after this operation!)
   */
  static void translatePropertyOperation(ReadFeatures readFeatures) {
    IPropertyQuery propertyQuery = readFeatures.getQuery().getProperties();
    if (propertyQuery != null) {
      PropertyOperationUtil.transformPropertyInPropertyOperationTree(
          propertyQuery, ActivityLogRequestTranslationUtil::translateIfApplicable);
    }
  }

  private static Optional<PQuery> translateIfApplicable(PQuery pQuery) {
    if (isSingleIdEqualityQuery(pQuery)) {
      String featureUuid = (String) pQuery.getValue();
      return Optional.of(uuidMustMatch(featureUuid));
    } else if (isSingleActivityLogIdEqualityQuery(pQuery)) {
      String activityLogId = (String) pQuery.getValue();
      return Optional.of(idMustMatch(activityLogId));
    }
    return Optional.empty();
  }

  private static boolean isSingleIdEqualityQuery(@NotNull PQuery pQuery) {
    final StringList path = pQuery.getProperty().getPath();
    return StringOp.EQUALS.equals(pQuery.getOp()) && path.size() == 1
        && Property.ID.equals(path.get(0));
  }

  private static boolean isSingleActivityLogIdEqualityQuery(PQuery pQuery) {
    return StringOp.EQUALS.equals(pQuery.getOp()) && PROPERTY_ACTIVITY_LOG_ID.equals(pQuery.getProperty());
  }

  private static PQuery uuidMustMatch(String desiredUuid) {
    final PQuery pQuery = new PQuery();
    pQuery.setOp(StringOp.EQUALS);
    pQuery.setValue(desiredUuid);
    pQuery.setProperty(PROPERTY_UUID);
    return pQuery;
  }

  private static PQuery idMustMatch(String desiredId) {
    final PQuery pQuery = new PQuery();
    pQuery.setOp(StringOp.EQUALS);
    pQuery.setValue(desiredId);
    pQuery.setProperty(new Property(ID));
    return pQuery;
  }
}
