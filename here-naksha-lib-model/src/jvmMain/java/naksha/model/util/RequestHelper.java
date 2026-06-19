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
package naksha.model.util;

import java.util.List;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestHelper {

  private RequestHelper() {
  }

  /**
   * Helper method to create ReadFeatures request for reading feature by given Id from given storage collection name.
   *
   * @param collectionName name of the storage collection
   * @param featureId      id to fetch matching feature
   * @return ReadFeatures request that can be used against IStorage methods
   */
  public static @NotNull ReadFeatures readFeaturesByIdRequest(
      final @Nullable String mapId,
      final @Nullable String collectionName,
      final @NotNull String featureId
  ) {
    final ReadFeatures readFeatures = new ReadFeatures().addCollectionId(collectionName);
    readFeatures.setCatalogId(mapId);
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
  public static @NotNull ReadFeatures readFeaturesByIdsRequest(
      final @Nullable String mapId,
      final @Nullable String collectionName,
      final @NotNull List<String> featureIds
  ) {
    final ReadFeatures readFeatures = new ReadFeatures().addCollectionId(collectionName);
    readFeatures.setCatalogId(mapId);
    readFeatures.getFeatureIds().addAll(featureIds);
    return readFeatures;
  }

  /**
   * Helper method to create WriteFeatures request with given feature. Function internally sets flags IfExists.FAIL and IfConflict.FAIL,
   * which will ensure that feature doesn't get overwritten in storage, if already exists.
   *
   * @param collection the storage collection
   * @param feature    feature object to be created
   * @param <FEATURE>  any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest createFeatureRequest(
      final @Nullable String mapId,
      final @Nullable String collection,
      final @NotNull FEATURE feature
  ) {
    return createFeaturesRequest(mapId, collection, List.of(feature));
  }

  /**
   * Helper method to create WriteFeatures request for updating given feature.
   *
   * @param collection the storage collection
   * @param feature    feature object to be updated
   * @param <FEATURE>  any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest nonAtomicUpdateFeatureRequest(
      final @NotNull NakshaCollection collection,
      final @NotNull FEATURE feature
  ) {
    final Write write = new Write().updateFeature(collection, feature, false);
    return new WriteRequest().add(write);
  }

  /**
   * Helper method to create WriteFeatures request for updating given feature. The update will not be atomic.
   *
   * @param mapId        the map where collection is defined
   * @param collectionId the storage collection
   * @param feature      feature object to be updated
   * @param <FEATURE>    any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest nonAtomicUpdateFeatureRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      final @NotNull FEATURE feature
  ) {
    final Write write = new Write().updateFeature(mapId, collectionId, feature, false);
    return new WriteRequest().add(write);
  }

  /**
   * Helper method to create WriteFeatures request for updating given feature. The update will be atomic.
   *
   * @param mapId        the map where collection is defined
   * @param collectionId the storage collection
   * @param feature      feature object to be updated
   * @param <FEATURE>    any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static <FEATURE extends NakshaFeature> @NotNull WriteRequest atomicUpdateFeatureRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      final @NotNull FEATURE feature
  ) {
    final Write write = new Write().updateFeature(mapId, collectionId, feature, true);
    return new WriteRequest().add(write);
  }

  /**
   * Helper method to create WriteFeatures request for updating multiple features.
   *
   * @param collection the storage collection
   * @param features   feature object array to be updated
   * @param <FEATURE>  any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull <FEATURE extends NakshaFeature> WriteRequest atomicUpdateFeaturesRequest(
      final @NotNull NakshaCollection collection, final @NotNull List<FEATURE> features) {
    final WriteRequest request = new WriteRequest();
    for (FEATURE feature : features) {
      request.add(new Write().updateFeature(collection, feature, true));
    }
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for updating multiple features.
   *
   * @param mapId        Id of the map where the collection is defined
   * @param collectionId name of the storage collection
   * @param feature      feature object be upsert
   * @param <FEATURE>    any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull <FEATURE extends NakshaFeature> WriteRequest upsertFeaturesRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      FEATURE feature) {
    final WriteRequest request = new WriteRequest();
    request.add(new Write().upsertFeature(mapId, collectionId, feature));
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for upserting multiple features.
   *
   * @param collectionId name of the storage collection
   * @param features     feature object array to be updated
   * @param <FEATURE>    any object extending XyzFeature
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull <FEATURE extends NakshaFeature> WriteRequest upsertFeaturesRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      final @NotNull List<FEATURE> features
  ) {
    final WriteRequest request = new WriteRequest();
    for (FEATURE feature : features) {
      request.add(new Write().upsertFeature(mapId, collectionId, feature));
    }
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for deleting multiple features.
   *
   * @param collectionId the id of storage collection
   * @param ids          feature object array to be deleted
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull WriteRequest deleteFeaturesByIdsRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      final @NotNull List<String> ids) {
    return deleteFeaturesByIdsRequest(new NakshaCollection(collectionId, mapId), ids);
  }

  /**
   * Helper method to create WriteFeatures request for deleting multiple features.
   *
   * @param collection the storage collection
   * @param ids        feature object array to be deleted
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull WriteRequest deleteFeaturesByIdsRequest(
      final @NotNull NakshaCollection collection,
      final @NotNull List<String> ids
  ) {
    final WriteRequest request = new WriteRequest();
    for (String id : ids) {
      request.add(new Write().deleteFeatureById(collection, id));
    }
    return request;
  }

  /**
   * Helper method to create WriteFeatures request for deleting given feature.
   *
   * @param collectionId the storage collection
   * @param id           feature object to be deleted
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull WriteRequest deleteFeatureByIdRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      final @NotNull String id
  ) {
    final Write write = new Write().deleteFeatureById(mapId, collectionId, id);
    return new WriteRequest().add(write);
  }

  /**
   * Helper method to create WriteFeatures request with given list of features.
   *
   * @param collectionId the storage collection
   * @param featureList  list of feature objects to be created
   * @return WriteFeatures request that can be used against IStorage methods
   */
  public static @NotNull WriteRequest createFeaturesRequest(
      final @Nullable String mapId,
      final @Nullable String collectionId,
      final @NotNull List<? extends NakshaFeature> featureList
  ) {
    final WriteRequest request = new WriteRequest();
    for (final NakshaFeature feature : featureList) {
      assert feature != null;
      request.add(new Write().createFeature(mapId, collectionId, feature));
    }
    return request;
  }

  public static @NotNull WriteRequest createWriteCollectionsRequest(final @NotNull NakshaCollection collection) {
    return createWriteCollectionsRequest(List.of(collection));
  }

  public static @NotNull WriteRequest createWriteCollectionsRequest(final @NotNull List<NakshaCollection> collections) {
    final WriteRequest writeRequest = new WriteRequest();
    for (final NakshaCollection collection : collections) {
      writeRequest.add(new Write().createCollection(collection));
    }
    return writeRequest;
  }

  // TODO: cleanup as part of CASL-784
  //
  //  /**
  //   * Helper function that returns instance of PRef or NonIndexedPRef depending on
  //   * whether the propPath provided matches with standard (indexed) property search or not.
  //   *
  //   * @param propPath the JSON path to be used for property search
  //   * @return PRef instance of PRef or NonIndexedPRef
  //   */
//    public static @NotNull PRef pRefFromPropPath(final @NotNull String[] propPath) {
//      // check if we can use standard PRef (on indexed properties)
//      for (final String[] path : pRefPathMap().keySet()) {
//        if (Arrays.equals(path, propPath)) {
//          return pRefPathMap().get(path);
//        }
//      }
//      // fallback to non-standard PRef (non-indexed properties)
//      return PRef.nonIndexedPref(propPath);
//    }
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
