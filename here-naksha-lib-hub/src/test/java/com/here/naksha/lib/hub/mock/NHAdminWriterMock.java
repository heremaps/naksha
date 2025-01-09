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
package com.here.naksha.lib.hub.mock;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NHAdminWriterMock extends NHAdminReaderMock implements IWriteSession {

  public NHAdminWriterMock(final @NotNull Map<String, TreeMap<String, NakshaFeature>> mockCollection) {
    super(mockCollection);
  }

  @Override
  public @NotNull Response execute(@NotNull Request request) {
    if (request instanceof WriteRequest wr) {
      for (Write write : wr.getWrites()) {
        if (Naksha.VIRT_COLLECTIONS.equals(write.getCollectionId())) {
          executeWriteCollection(write);
        } else {
          executeWriteFeature(write);
        }
      }
      return new SuccessResponse();
    } else {
      return new ErrorResponse(
          new NakshaError(NakshaError.UNSUPPORTED_OPERATION,
              "WriteRequest type " + request.getClass().getName() + " not supported"));
    }
  }

  private void executeWriteCollection(Write write) {
    String collectionId = write.getFeatureId();
    WriteOp op = write.getOp();
    if (op.equals(WriteOp.CREATE)) {
      mockCollection.putIfAbsent(collectionId, new TreeMap<>());
    } else if (op.equals(WriteOp.DELETE)) {
      mockCollection.remove(collectionId);
    } else {
      throw new NakshaException(new NakshaError(NakshaError.UNSUPPORTED_OPERATION, "Mock can only CREATE and DELETE collection"));
    }
  }

  private void executeWriteFeature(Write write) {
    String collectionId = write.getCollectionId();
    if (!mockCollection.containsKey(collectionId)) {
      throw new NakshaException(new NakshaError(
          NakshaError.COLLECTION_NOT_FOUND,
          "Collection " + write.getCollectionId() + " doesn't exist."
      ));
    }

    WriteOp op = write.getOp();
    NakshaFeature feature = write.getFeature();
    if (op.equals(WriteOp.CREATE)) {
      insertFeature(collectionId, feature);
    } else if (op.equals(WriteOp.UPDATE)) {
      updateFeature(collectionId, feature);
    } else if (op.equals(WriteOp.UPSERT)) {
      upsertFeature(collectionId, feature);
    } else if (op.equals(WriteOp.DELETE)) {
      deleteFeature(collectionId, feature.getId());
    } else if (op.equals(WriteOp.PURGE)) {
      throw new NakshaException(new NakshaError(NakshaError.UNSUPPORTED_OPERATION, "PurgeFeature not mocked yet"));
    } else {
      throw new NakshaException(new NakshaError(NakshaError.UNSUPPORTED_OPERATION, op.getValue() + " not mocked yet"));
    }
  }

  private void insertFeature(
      final @NotNull String collectionId,
      final @NotNull NakshaFeature feature
  ) {
    if (mockCollection.get(collectionId).putIfAbsent(feature.getId(), setUuidFor(feature)) != null) {
      throw new NakshaException(new NakshaError(NakshaError.CONFLICT, "Feature already exists: " + feature.getId()));
    }
  }

  private void updateFeature(
      final @NotNull String collectionId,
      final @NotNull NakshaFeature feature
  ) {
    final AtomicReference<NakshaException> exception = new AtomicReference<>();

    mockCollection.get(collectionId).compute(feature.getId(), (fId, oldF) -> {
      // no existing feature to update
      if (oldF == null) {
        exception.set(new NakshaException(new NakshaError(NakshaError.NOT_FOUND, "No feature found for id " + fId)));
        return oldF;
      }
      // update if UUID matches (or overwrite if new uuid is missing)
      if ((Objects.equals(uuidOf(oldF), uuidOf(feature)) && uuidOf(feature) != null) || uuidOf(feature) == null) {
        return setUuidFor(feature);
      } else {
        // throw error if UUID mismatches
        exception.set(new NakshaException(new NakshaError(NakshaError.ILLEGAL_STATE, "Uuid " + uuidOf(oldF) + " mismatch for id " + fId)));
        return oldF;
      }
    });
    if (exception.get() != null) {
      throw exception.get();
    }
  }

  private void upsertFeature(
      final @NotNull String collectionId,
      final @NotNull NakshaFeature feature
  ) {
    final AtomicReference<NakshaFeature> result = new AtomicReference<>();
    final AtomicReference<NakshaException> exception = new AtomicReference<>();

    mockCollection.get(collectionId).compute(feature.getId(), (fId, oldF) -> {
      // insert if missing
      if (oldF == null) {
        if (uuidOf(feature) == null) {
          setUuidFor(feature);
        }
        result.set(feature);
        return feature;
      }
      // update if UUID matches (or overwrite if new uuid is missing)
      if ((Objects.equals(uuidOf(oldF), uuidOf(feature)) && uuidOf(feature) != null) || uuidOf(feature) == null) {
        result.set(feature);
        return setUuidFor(feature);
      } else {
        // throw error if UUID mismatches
        exception.set(
            new NakshaException(new NakshaError(NakshaError.CONFLICT, "Uuid " + uuidOf(oldF) + " mismatch for id " + feature.getId())));
        return oldF;
      }
    });
    if (exception.get() != null) {
      throw exception.get();
    }
  }

  private void deleteFeature(
      final @NotNull String collectionId,
      final @NotNull String id,
      final @Nullable String uuid
  ) {
    final AtomicReference<NakshaException> exception = new AtomicReference<>();

    mockCollection.get(collectionId).compute(id, (fId, oldF) -> {
      // nothing to delete if it is already absent
      if (oldF == null) {
//        result.set(featureCodec(id, EExecutedOp.RETAINED)); TODO?
        return oldF;
      }
      // delete if UUID matches
      if ((Objects.equals(uuidOf(oldF), uuid)) || (uuid == null)) {
//        result.set(oldF); TODO
        return null;
      } else {
        // throw error if UUID mismatches
        exception.set(new NakshaException(new NakshaError(NakshaError.ILLEGAL_ARGUMENT,
            "Uuid " + uuidOf(oldF) + " mismatch for id " + id)));
        return oldF;
      }
    });
    if (exception.get() != null) {
      throw exception.get();
    }
  }

  private void deleteFeature(
      final @NotNull String collectionId,
      final @Nullable String featureId
  ) {
    if (featureId == null) {
      throw new NakshaException(new NakshaError(NakshaError.ILLEGAL_ARGUMENT, "Can't delete feature without id"));
    }
    mockCollection.get(collectionId).remove(collectionId);
  }

  private @Nullable String uuidOf(final @NotNull NakshaFeature feature) {
    return feature.getProperties().getXyz().getUuid();
  }

  private NakshaFeature setUuidFor(final @NotNull NakshaFeature feature) {
    feature.getProperties().getXyz().setRaw("uuid", UUID.randomUUID());
    return feature;
  }

  @Override
  public void commit() {
    // do nothing
  }

  @Override
  public void rollback() {
    // do nothing
  }
}
