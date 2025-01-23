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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static naksha.model.util.RequestHelper.createFeatureRequest;

import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.hub.NakshaHubConfig;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import naksha.base.Int64;
import naksha.model.ILock;
import naksha.model.IMap;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.Tuple;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NHAdminMock implements IStorage {

  protected static @NotNull Map<String, TreeMap<String, NakshaFeature>> mockCollection;
  protected static @NotNull NakshaHubConfig nakshaHubConfig;

  public NHAdminMock(final @NotNull Storage storage) {
    // this constructor is only to support IStorage instantiation
    if (this.mockCollection == null) {
      this.mockCollection = new ConcurrentHashMap<>();
      this.initStorage(null);
    }
  }

  public NHAdminMock(
      final @NotNull Map<String, TreeMap<String, Object>> mockCollection,
      final @NotNull NakshaHubConfig customCfg) {
    throw new UnsupportedOperationException(
        "NHAdminMock storage should not be used"); // comment to use mock in local env
    /*this.mockCollection = mockCollection;
    this.nakshaHubConfig = customCfg;
    this.initStorage();
    this.setupConfig();*/
  }

  private void setupConfig() {
    // Add custom config in naksha:configs
    final NakshaContext ctx = NakshaContext.newInstance("naksha_mock");
    ctx.attachToCurrentThread();

    try (final IWriteSession admin = newWriteSession(SessionOptions.from(ctx, true))) {
      final Response response = admin.execute(createFeatureRequest(NakshaAdminCollection.CONFIGS, nakshaHubConfig));
      if (response instanceof ErrorResponse errorResponse) {
        admin.rollback();
        throw unchecked(
            new Exception("Unable to add custom config in Mock storage (code: " + errorResponse.getError().getCode() + " )",
                errorResponse.getError().getCause()));
      }
      admin.commit();
    }
  }

  @NotNull
  @Override
  public String getId() {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @NotNull
  @Override
  public SessionOptions getAdminOptions() {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Override
  public int getHardCap() {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Override
  public void setHardCap(int i) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Override
  public boolean isInitialized() {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Override
  public void initStorage(@Nullable Map<String, ?> params) {
    final NakshaContext ctx = NakshaContext.newInstance("naksha_mock");
    ctx.attachToCurrentThread();

    // Create all admin collections
    try (final IWriteSession admin = newWriteSession(SessionOptions.from(ctx, true))) {
      WriteRequest writeAdminCollections = new WriteRequest();
      for (final String name : NakshaAdminCollection.ALL) {
        Write write = new Write().createCollection(null, new NakshaCollection(name));
        writeAdminCollections.add(write);
      }
      final Response response = admin.execute(writeAdminCollections);
      if (response instanceof ErrorResponse errorResponse) {
        admin.rollback();
        NakshaError error = errorResponse.getError();
        throw unchecked(new Exception("Unable to create Admin collections in Mock storage (code: " + error.getCode() + " )", error.getCause()));
      }
      admin.commit();
    }
  }

  @NotNull
  @Override
  public IMap getDefaultMap() {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @NotNull
  @Override
  public IMap get(@NotNull String mapId) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Nullable
  @Override
  public IMap get(int mapNumber) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Override
  public boolean contains(@NotNull String mapId) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @Nullable
  @Override
  public String getMapId(int mapNumber) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @NotNull
  @Override
  public NakshaFeature tupleToFeature(@NotNull Tuple tuple) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @NotNull
  @Override
  public Tuple featureToTuple(@NotNull NakshaFeature feature) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @NotNull
  @Override
  public IWriteSession newWriteSession(@Nullable SessionOptions options) {
    return new NHAdminWriterMock(mockCollection);
  }

  @NotNull
  @Override
  public IReadSession newReadSession(@Nullable SessionOptions options) {
    return new NHAdminReaderMock(mockCollection);
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }

  @NotNull
  @Override
  public ILock enterLock(@NotNull String id, @NotNull Int64 waitMillis) {
    throw new UnsupportedOperationException("Not yet supported by NHAdminMock");
  }
}
