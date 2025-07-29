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
import static java.util.stream.Collectors.toSet;
import static naksha.model.objects.NakshaFeature.PROPERTIES_KEY;
import static naksha.model.objects.NakshaProperties.XYZ_ACTIVITY_LOG_NS;
import static naksha.model.objects.NakshaProperties.XYZ_KEY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import naksha.base.StringList;
import naksha.model.Guid;
import naksha.model.GuidList;
import naksha.model.Version;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;

class ActivityLogRequestTranslationUtil {

  static final String UUID = "uuid";
  static final String PUUID = "puuid";
  static final String ACTION = "action";
  static final String CREATED_AT = "createdAt";
  static final String UPDATED_AT = "updatedAt";

  private static final String[] ACTIVITY_LOG_ID_PATH = new String[]{PROPERTIES_KEY, XYZ_ACTIVITY_LOG_NS, ID};
  private static final String[] UUID_PATH = new String[]{XYZ_KEY, UUID};
  static final Property PROPERTY_ACTIVITY_LOG_ID = new Property(ACTIVITY_LOG_ID_PATH);
  static final Property PROPERTY_UUID = new Property(UUID_PATH);

  private ActivityLogRequestTranslationUtil() {
  }

  /**
   * Mutates [ReadFeatures] so that it is aligned with Acitvity Log needs, more specifically - we query history - we query for all available
   * versions - we assume the query will always hit the collection with Space's id - if singular featureId is defined, we assume its value
   * holds guuid which will be used to extract actual featureId - if no feature ids are defined, we expect activityLogId defined, that is
   * then disabled and used as regular featureId
   *
   * @param readFeatures ReadFeatures bearing potential POp to be translated (request will be mutated after this operation!)
   */
  static void transformOriginalRequest(ReadFeatures readFeatures, String spaceId) {
    readFeatures.setQueryHistory(true);
    readFeatures.setVersions(Integer.MAX_VALUE);
    readFeatures.setCollectionIds(StringList.of(spaceId));

    // extract UUIDs from featureIds, reset featureIds
    StringList rawGuids = readFeatures.getFeatureIds();
    if(!rawGuids.isEmpty()){
      GuidList guids = new GuidList();
      rawGuids.forEach(rawGuid -> guids.add(Guid.fromString(rawGuid)));
      readFeatures.setGuids(guids);
    }
    StringList finalFeatureIds = new StringList();

    // extractFeatureIds from activityLogId - populate featureIds
    Set<PQuery> disabledActivityLogPOps = disablePQueriesInRequest(
        readFeatures.getQuery(),
        ActivityLogRequestTranslationUtil::isSingleActivityLogIdEqualityQuery
    );
    if (!disabledActivityLogPOps.isEmpty()) {
      disabledActivityLogPOps.forEach(activityLogOp -> finalFeatureIds.add(activityLogOp.getValue().toString()));
      readFeatures.refreshPropertyFilter();
    }
    readFeatures.setFeatureIds(finalFeatureIds);
  }

  private static void propagateFeatureIdAndVersion(ReadFeatures readFeatures) {
//    Set<PQuery> disabledActivityLogPOps = disablePQueriesInRequest(
//        readFeatures.getQuery(),
//        ActivityLogRequestTranslationUtil::isSingleActivityLogIdEqualityQuery
//    );
//    Set<String> activityLogBasedIds = disabledActivityLogPOps.stream().map(pQuery -> pQuery.getValue().toString()).collect(toSet())();
//    Set<String> guidBasedIds = readFeatures.getFeatureIds().stream().map(guuid -> Guid.fromString(guuid).id).collect(Collectors.toSet());
//    if(guidBasedIds.isEmpty()){
//      // no GUID -
//
//    } else {
//      if(guidBasedIds.containsAll(activityLogBasedIds)){
//        // interscetion - we should only consider ids that come both from GUIDs and ActivityLogNS
//        guidBasedIds.retainAll(activityLogBasedIds);
//
//      } else {
//        // there are ids in ActivityLogNS that are not present in specified GUIDs - logically, no feature can be returned
//        // TODO
//      }
//    }
//
//
//
//    if (readFeatures.isEmpty()) {
//      translateActivityLogIdBasedRequest(readFeatures);
//    } else {
//      translatedGuidBasedRequest(readFeatures);
//    }
//
    StringList translatedFeatureIds = new StringList();
    // by default we don't set max version and fetch all - this will be overridden later if possible
    readFeatures.setVersion(null);
    // handle potential activity log ids placed in property query
    Set<PQuery> disabledActivityLogPOps = disablePQueriesInRequest(
        readFeatures.getQuery(),
        ActivityLogRequestTranslationUtil::isSingleActivityLogIdEqualityQuery
    );
    if (!disabledActivityLogPOps.isEmpty()) {
      disabledActivityLogPOps.forEach(activityLogOp -> translatedFeatureIds.add(activityLogOp.getValue().toString()));
      readFeatures.refreshPropertyFilter();
    }
    // handle featureIds from original request after activityLog ones
    StringList requestFeatureIds = readFeatures.getFeatureIds();
    if (requestFeatureIds.size() == 1 && translatedFeatureIds.isEmpty()) {
      Guid guid = Guid.fromString(requestFeatureIds.get(0));
      translatedFeatureIds.add(guid.id);
      // single guid gives us possibility to establish specific version
      readFeatures.setVersion(guid.tupleNumber.version);
    } else {
      // multiple tuple numbers (guids) provided OR we have single guid and at least one id from activityLogNs
      Map<String, Version> maxVersionsPerFeatureId = new HashMap<>();
      requestFeatureIds.forEach(rawGuid -> {
        Guid guid = Guid.fromString(rawGuid);
        translatedFeatureIds.add(guid.id);
        maxVersionsPerFeatureId.put(guid.id, guid.tupleNumber.version);
      });
      // unable to pick single version from N guids (fetching all versions by default)
      // utilizing post-processing to filter out unnecessary ones
      // TODO: review potential improvement as part of CASL-1107
      readFeatures.getResultFilters().add((new MaxVersionResultFilter(maxVersionsPerFeatureId)));
    }
    readFeatures.setFeatureIds(translatedFeatureIds);
  }

  private static void applyTwoMostRecentVersions(ReadFeatures readFeatures) {

  }

  private static void applyFullHistorySearch(ReadFeatures readFeatures, Set<String> featureIds) {
    StringList finalFeatureIds = new StringList();
    finalFeatureIds.addAll(featureIds);
    readFeatures.setFeatureIds(finalFeatureIds);
    readFeatures.setVersion(null); // no max version, we want everything, starting from HEAD
    readFeatures.setVersions(Integer.MAX_VALUE);
  }

  /*
   * GUID-based requests expect that there will be single hsitory entry per every feature id
   */
  private static void translatedGuidBasedRequest(ReadFeatures readFeatures) {
    // `versions=2` with no max version -> we will fetch 2 most recent versions
    readFeatures.setVersions(2);
    readFeatures.setVersion(null);
    // extract featureIds from supplied GUIDs
    StringList guidBasedFeatureIds = new StringList();
    readFeatures.getFeatureIds().forEach(rawGuid -> guidBasedFeatureIds.add(Guid.fromString(rawGuid).id));
    // remove potential Activity Log Id queries - they will be obsolete if guids are provided (they would get canceled by AND
  }

  private static List<String> disableActivityLogQueriesAndReturnIds(ReadFeatures readFeatures) {
    return disablePQueriesInRequest(
        readFeatures.getQuery(),
        ActivityLogRequestTranslationUtil::isSingleActivityLogIdEqualityQuery
    ).stream().map(pQuery -> pQuery.getValue().toString()).toList();
  }

  public static void translateActivityLogIdBasedRequest(ReadFeatures readFeatures) {

  }

  private static boolean isSingleActivityLogIdEqualityQuery(PQuery pQuery) {
    return StringOp.EQUALS.equals(pQuery.getOp()) && pQuery.getProperty().getPath().containsStringsInOrder(ACTIVITY_LOG_ID_PATH);
  }
}
