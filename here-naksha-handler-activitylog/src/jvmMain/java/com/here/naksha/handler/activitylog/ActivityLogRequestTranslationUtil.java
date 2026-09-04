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
import static com.here.naksha.lib.handlers.util.PropertyOperationUtil.disablePQueriesInRequest;
import static naksha.model.objects.NakshaFeature.PROPERTIES_KEY;
import static naksha.model.objects.NakshaProperties.XYZ_ACTIVITY_LOG_NS;

import java.util.Set;

import naksha.base.StringList;
import naksha.base.Guid;
import naksha.base.TupleNumber;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzMembers;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ops.*;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;

class ActivityLogRequestTranslationUtil {

  static final String UUID = "uuid";
  /**
   * `puuid` is no longer populated by Naksha {@code lib-psql}, it will just be a custom JSON attribute assigned by users.
   * @deprecated since 3.0.0-beta.41
   */
  @Deprecated(since = "3.0.0-beta.41")
  static final String PUUID = "puuid";
  static final String ACTION = "action";
  static final String CREATED_AT = "createdAt";
  static final String UPDATED_AT = "updatedAt";

  private static final String[] ACTIVITY_LOG_ID_PATH = new String[]{PROPERTIES_KEY, XYZ_ACTIVITY_LOG_NS, ID};
  static final Property PROPERTY_ACTIVITY_LOG_ID = new Property(ACTIVITY_LOG_ID_PATH);

  private ActivityLogRequestTranslationUtil() {
  }

  /**
   * Mutates [ReadFeatures] so that it is aligned with Activity Log needs, more specifically - we query history - we query for all available
   * versions - we assume the query will always hit the collection with Space's id - if singular featureId is defined, we assume its value
   * holds guuid which will be used to extract actual featureId - if no feature ids are defined, we expect activityLogId defined, that is
   * then disabled and used as regular featureId
   *
   * @param readFeatures ReadFeatures bearing potential POp to be translated (request will be mutated after this operation!)
   */
  static void transformOriginalRequest(ReadFeatures readFeatures, String spaceId) {
    readFeatures.setQueryHistory(true);
    readFeatures.setQueryDeleted(true);
    readFeatures.setVersions(Integer.MAX_VALUE);
    readFeatures.setCollectionId(spaceId);

    // extract UUIDs from featureIds, reset featureIds
    StringList rawGuids = readFeatures.getFeatureIds();
    if (!rawGuids.isEmpty()) {
      Or or = new Or();
      OpList orClauses = or.getChildren();
      for (int i=0;i<rawGuids.getSize();i++) {
        String rawGuid = rawGuids.get(i);
        if (rawGuid != null) {
          final TupleNumber tupleNumber = Guid.fromString(rawGuid).tupleNumber;
          //TODO we prefer ISession.loadTuples(), but that does not make sense for now, because we are disabling cache, and we anyway have to support other form of read requests beside by UUID
          //TODO and this is less efficient than the deprecated ReadFeatures.setGuids()
          orClauses.add(
                  new And(
                          new Equals(StandardMembers.FeatureVersion.getName(), tupleNumber.version),
                          new Equals(StandardMembers.FeatureNumber.getName(), tupleNumber.featureNumber)
                  )
          );
        }
      }
      readFeatures.setQueryMembers(or);
    }
    // extractFeatureIds from activityLogId - populate featureIds
    Set<PQuery> disabledActivityLogPOps = disablePQueriesInRequest(
        readFeatures.getQuery(),
        ActivityLogRequestTranslationUtil::isSingleActivityLogIdEqualityQuery
    );

    final StringList featureIds = new StringList();
    if (!disabledActivityLogPOps.isEmpty()) {
      disabledActivityLogPOps.forEach(activityLogOp -> featureIds.add(String.valueOf(activityLogOp.getValue())));
      readFeatures.refreshPropertyFilter();
    }
    // Translated from old syntax into new members query, because we do not allow to mix old and new syntax!
    // TODO: Why do we need this?
    if (!featureIds.isEmpty()) {
      final IsAnyOf searchFeaturesByIdOp = new IsAnyOf(XyzMembers.XyzId);
      for (var id : featureIds) searchFeaturesByIdOp.getItems().add(id);

      final Op existing = readFeatures.getQueryMembers();
      if (existing == null)
        readFeatures.setQueryMembers(searchFeaturesByIdOp);
      else
        readFeatures.setQueryMembers(new And(existing, searchFeaturesByIdOp));
    }
    readFeatures.setFeatureIds(new StringList());
  }

  private static boolean isSingleActivityLogIdEqualityQuery(PQuery pQuery) {
    return StringOp.EQUALS.equals(pQuery.getOp())
            && java.util.Arrays.equals(pQuery.getProperty().getPath().toArray(), ACTIVITY_LOG_ID_PATH);
  }
}
