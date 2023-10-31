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
package com.here.naksha.lib.hub2.admin;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.util.storage.RequestHelper.createFeatureRequest;

import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.StorageCollection;
import com.here.naksha.lib.core.models.storage.WriteCollections;
import com.here.naksha.lib.core.models.storage.WriteOp;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.storage.IWriteSession;
import java.util.ArrayList;
import java.util.List;

/**
 * IStorage responsible for Admin operations in Naksha
 */
public abstract class AdminStorage implements IStorage {

  private final IStorage physicalAdminStorage;

  protected AdminStorage(IStorage physicalAdminStorage, NakshaContext nakshaContext) {
    this.physicalAdminStorage = physicalAdminStorage;
    nakshaContext.attachToCurrentThread();
    createAdminCollections(nakshaContext);
    ensureHistoryPartitionsAvailability();
    ensureDefaultStorageImplementationPresent(nakshaContext);
  }

  @Override
  public void initStorage() {
    this.physicalAdminStorage.initStorage();
  }

  @Override
  public void startMaintainer() {
    this.physicalAdminStorage.startMaintainer();
  }

  @Override
  public void maintainNow() {
    this.physicalAdminStorage.maintainNow();
  }

  @Override
  public void stopMaintainer() {
    this.physicalAdminStorage.stopMaintainer();
  }

  protected abstract Storage fetchDefaultStorage();

  private void createAdminCollections(NakshaContext nakshaContext) {
    try (final IWriteSession admin = physicalAdminStorage.newWriteSession(nakshaContext, true)) {
      final List<WriteOp<StorageCollection>> collectionList = new ArrayList<>();
      for (final String name : NakshaAdminCollection.ALL) {
        final StorageCollection collection = new StorageCollection(name);
        final WriteOp<StorageCollection> writeOp = new WriteOp<>(EWriteOp.INSERT, collection, false);
        collectionList.add(writeOp);
      }
      final Result wrResult = admin.execute(new WriteCollections<>(collectionList));
      if (wrResult == null) {
        admin.rollback();
        throw unchecked(new Exception("Unable to create Admin collections in Admin DB. Null result!"));
      } else if (wrResult instanceof ErrorResult er) {
        admin.rollback();
        throw unchecked(new Exception(
            "Unable to create Admin collections in Admin DB. " + er.toString(), er.exception));
      }
      admin.commit();
    }
  }

  private void ensureHistoryPartitionsAvailability() {
    physicalAdminStorage.maintainNow();
  }

  private void ensureDefaultStorageImplementationPresent(NakshaContext nakshaContext) {
    final Storage defaultStorage = fetchDefaultStorage();
    try (final IWriteSession admin = physicalAdminStorage.newWriteSession(nakshaContext, true)) {
      // persist in Admin DB (if not already exists)
      final Result writeDefaultStorageResult =
          admin.execute(createFeatureRequest(NakshaAdminCollection.STORAGES, defaultStorage, true));
      if (writeDefaultStorageResult == null) {
        admin.rollback();
        throw unchecked(new Exception("Unable to add default storage in Admin DB. Null result!"));
      } else if (writeDefaultStorageResult instanceof ErrorResult er) {
        admin.rollback();
        throw unchecked(
            new Exception("Unable to add default storage in Admin DB. " + er.toString(), er.exception));
      }
      admin.commit();
    }
  }
}
