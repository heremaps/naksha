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
package com.here.naksha.lib.core.util.storage;

import com.here.naksha.lib.core.models.storage.*;
import java.util.List;
import naksha.geo.MultiPointCoord;
import naksha.geo.PointCoord;
import naksha.geo.ProxyGeoUtil;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Geometry;

@AvailableSince(NakshaVersion.v2_0_7)
public class RequestHelper {

  /**
   * Helper method to create ReadFeatures request for reading feature by given Id from given storage collection name.
   *
   * @param collectionName name of the storage collection
   * @param featureId      id to fetch matching feature
   * @return ReadFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static @NotNull ReadFeatures readFeaturesByIdRequest(
      final @NotNull String collectionName, final @NotNull String featureId) {
    final ReadFeatures readFeatures = new ReadFeatures(collectionName);
    readFeatures.getFeatureIds().add(featureId);
    return readFeatures;
  }

  /**
   * Helper method to create ReadFeatures request for reading feature by given Ids from given storage collection name.
   *
   * @param collectionName name of the storage collection
   * @param featureIds     list of ids to fetch matching features
   * @return ReadFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static @NotNull ReadFeatures readFeaturesByIdsRequest(
      final @NotNull String collectionName, final @NotNull List<@NotNull String> featureIds) {
    final ReadFeatures readFeatures = new ReadFeatures(collectionName);
    readFeatures.getFeatureIds().addAll(featureIds);
    return readFeatures;
  }

  /**
   * Helper method to create WriteFeatures request with given feature. If silentIfExists is true, function internally sets IfExists.RETAIN
   * and IfConflict.RETAIN (to silently ignoring create operation, if feature already exists). If set to false, both flags will be set to
   * FAIL, which will ensure that feature doesn't get overwritten in storage, if already exists.
   *
   * @param collectionName name of the storage collection
   * @param feature        feature object to be created
   * @param silentIfExists flag to turn on/off silent create operation
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest createFeatureRequest(
      final @NotNull String collectionName, final @NotNull FEATURE feature, final boolean silentIfExists) {
    if (silentIfExists) {
      return createFeaturesRequest(collectionName, List.of(feature), IfExists.RETAIN, IfConflict.RETAIN);
    } else {
      return createFeaturesRequest(collectionName, List.of(feature), IfExists.FAIL, IfConflict.FAIL);
    }
  }

  /**
   * Helper method to create WriteFeatures request with given feature. Function internally sets flags IfExists.FAIL and IfConflict.FAIL,
   * which will ensure that feature doesn't get overwritten in storage, if already exists.
   *
   * @param collectionName name of the storage collection
   * @param feature        feature object to be created
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest createFeatureRequest(
      final @NotNull String collectionName, final @NotNull FEATURE feature) {
    return createFeaturesRequest(collectionName, List.of(feature), IfExists.FAIL, IfConflict.FAIL);
  }
  /**
   * Helper method to create WriteFeatures request for updating given feature.
   *
   * @param collectionName name of the storage collection
   * @param feature        feature object to be updated
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest updateFeatureRequest(
      final @NotNull String collectionName, final @NotNull FEATURE feature) {
    final Write write = new Write().updateFeature(null, collectionName, feature, false);
    return new WriteRequest().add(write);
  }

  /**
   * Helper method to create WriteFeatures request for updating multiple features.
   *
   * @param collectionName name of the storage collection
   * @param features       feature object array to be updated
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull <FEATURE extends NakshaFeature> WriteRequest updateFeaturesRequest(
      final @NotNull String collectionName, final @NotNull List<FEATURE> features) {
    final WriteRequest request = new WriteRequest();
    final Write write = new Write();
    for (FEATURE feature : features) {
      write.updateFeature(null, collectionName, feature, true);
      request.add(write);
    }
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for upserting multiple features.
   *
   * @param collectionName name of the storage collection
   * @param features       feature object array to be updated
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull <FEATURE extends NakshaFeature> WriteRequest upsertFeaturesRequest(
      final @NotNull String collectionName, final @NotNull List<FEATURE> features) {
    final WriteRequest request = new WriteRequest();
    final Write write = new Write();
    for (FEATURE feature : features) {
      write.upsertFeature(null, collectionName, feature);
      request.add(write);
    }
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for deleting multiple features.
   *
   * @param collectionName name of the storage collection
   * @param ids       feature object array to be deleted
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull WriteRequest deleteFeaturesByIdsRequest(
      final @NotNull String collectionName, final @NotNull List<String> ids) {
    final WriteRequest request = new WriteRequest();
    final Write write = new Write();
    for (String id : ids) {
      write.deleteFeatureById(null, collectionName, id, null);
      request.add(write);
    }
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for deleting given feature.
   *
   * @param collectionName name of the storage collection
   * @param id        feature object to be deleted
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull WriteRequest deleteFeatureByIdRequest(
      final @NotNull String collectionName, final @NotNull String id) {
    final Write write = new Write().deleteFeatureById(null, collectionName, id, null);
    return new WriteRequest().add(write);
  }

  /**
   * Helper method to create WriteFeatures request with given list of features. If silentIfExists is true, function internally sets
   * IfExists.RETAIN and IfConflict.RETAIN (to silently ignoring create operation, if feature already exists). If set to false, both flags
   * will be set to FAIL, which will ensure that feature doesn't get overwritten in storage, if already exists.
   *
   * @param collectionName name of the storage collection
   * @param featureList    list of features to be created
   * @param silentIfExists flag to turn on/off silent create operation
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest createFeatureRequest(
      final @NotNull String collectionName,
      final @NotNull List<FEATURE> featureList,
      final boolean silentIfExists) {
    if (silentIfExists) {
      return createFeaturesRequest(collectionName, featureList, IfExists.RETAIN, IfConflict.RETAIN);
    } else {
      return createFeaturesRequest(collectionName, featureList, IfExists.FAIL, IfConflict.FAIL);
    }
  }

  /**
   * Helper method to create WriteFeatures request with given list of features. Function internally sets flags IfExists.FAIL and
   * IfConflict.FAIL, which will ensure that feature doesn't get overwritten in storage, if already exists.
   *
   * @param collectionName name of the storage collection
   * @param featureList    list of feature objects to be created
   * @param <FEATURE>      any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest createFeaturesRequest(
      final @NotNull String collectionName, final @NotNull List<FEATURE> featureList) {
    return createFeaturesRequest(collectionName, featureList, IfExists.FAIL, IfConflict.FAIL);
  }

  /**
   * Helper method to create WriteFeatures request with given feature.
   *
   * @param collectionName   name of the storage collection
   * @param feature          feature object to be created
   * @param ifExistsAction   flag to indicate what to do if feature already found existing in database
   * @param ifConflictAction flag to indicate what to do if feature version in database conflicts with given feature version
   * @param <FEATURE>        any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest createFeatureRequest(
      final @NotNull String collectionName,
      final @NotNull FEATURE feature,
      final @NotNull IfExists ifExistsAction,
      final @NotNull IfConflict ifConflictAction) {
    return createFeaturesRequest(collectionName, List.of(feature), ifExistsAction, ifConflictAction);
  }

  /**
   * Helper method to create WriteFeatures request with given list of features.
   *
   * @param collectionName   name of the storage collection
   * @param featureList      list of feature objects to be created
   * @param ifExistsAction   flag to indicate what to do if feature already found existing in database
   * @param ifConflictAction flag to indicate what to do if feature version in database conflicts with given feature version
   * @return WriteFeatures request that can be used against IStorage methods
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static @NotNull WriteRequest createFeaturesRequest(
      final @NotNull String collectionName,
      final @NotNull List<? extends NakshaFeature> featureList,
      final @NotNull IfExists ifExistsAction,
      final @NotNull IfConflict ifConflictAction) {
    final WriteRequest request = new WriteRequest();
    final Write write = new Write();
    for (final NakshaFeature feature : featureList) {
      assert feature != null;
      request.add(write.createFeature(null, collectionName, feature));
    }
    return request;
  }

  public static @NotNull WriteRequest createWriteCollectionsRequest(final @NotNull NakshaCollection collection) {
    return createWriteCollectionsRequest(List.of(collection));
  }

  public static @NotNull WriteRequest createWriteCollectionsRequest(
      final @NotNull List<@NotNull NakshaCollection> collections) {
    final WriteRequest writeXyzCollections = new WriteRequest();
    final Write write = new Write();
    for (final NakshaCollection collection : collections) {
      writeXyzCollections.add(write.createCollection(null, collection));
    }
    return writeXyzCollections;
  }

  /**
   * Helper function that returns Geometry representing BoundingBox for the co-ordinates
   * supplied as arguments.
   *
   * @param west west co-ordinate
   * @param south south co-ordinate
   * @param east east co-ordinate
   * @param north north co-ordinate
   * @return Geometry representing BBox envelope
   */
  public static @NotNull Geometry createBBoxEnvelope(
      final double west, final double south, final double east, final double north) {
    MultiPointCoord multiPoint = new MultiPointCoord();
    multiPoint.add(new PointCoord(west, south));
    multiPoint.add(new PointCoord(east, north));
    return ProxyGeoUtil.toJtsMultiPoint(multiPoint);
  }

  //  /**
  //   * Helper function that returns instance of PRef or NonIndexedPRef depending on
  //   * whether the propPath provided matches with standard (indexed) property search or not.
  //   *
  //   * @param propPath the JSON path to be used for property search
  //   * @return PRef instance of PRef or NonIndexedPRef
  //   */
  //  public static @NotNull PRef pRefFromPropPath(final @NotNull String[] propPath) {
  //    // check if we can use standard PRef (on indexed properties)
  //    for (final String[] path : pRefPathMap().keySet()) {
  //      if (Arrays.equals(path, propPath)) {
  //        return pRefPathMap().get(path);
  //      }
  //    }
  //    // fallback to non-standard PRef (non-indexed properties)
  //    return PRef.nonIndexedPref(propPath);
  //  }
  //
  //  public static void combineOperationsForRequestAs(
  //      final @NotNull ReadFeatures request, final OpType opType, @Nullable Op... operations) {
  //    if (operations == null) return;
  //    List<Op> opList = null;
  //    for (final Op crtOp : operations) {
  //      if (crtOp == null) continue;
  //      if (request.op == null) {
  //        request.withOp(crtOp); // set operation directly if this was the only one operation
  //        continue;
  //      } else if (opList == null) {
  //        opList = new ArrayList<>(); // we have more than one operation
  //        opList.add(request.op); // save previously added operation
  //      }
  //      opList.add(crtOp); // keep appending every operation that is to be added to the request
  //    }
  //    if (opList == null) return;
  //    // Add combined operations to request
  //    if (opType == LOpType.AND) {
  //      request.withOp(LOp.and(opList.toArray(Op[]::new)));
  //    } else {
  //      request.withOp(LOp.or(opList.toArray(Op[]::new)));
  //    }
  //  }
}
