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
package com.here.naksha.lib.view;

import naksha.model.IWriteSession;
import naksha.model.SessionOptions;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * It writes same value to all storages, the result must not be combined, to let clients know if operation succeeded in
 * each storage or not.
 */
public class ViewWriteSession extends ViewReadSession implements IWriteSession {

  IWriteSession session;
  ViewLayer writeLayer;
  SessionOptions options;

  public ViewWriteSession(@NotNull View viewRef, @Nullable SessionOptions options) {
    super(viewRef, options);
    this.options = options;
  }

  public ViewWriteSession withWriteLayer(ViewLayer viewLayer) {
    if (this.session != null)
      throw new RuntimeException("Write session initiated with " + this.writeLayer.getCollectionId());
    this.writeLayer = viewLayer;
    return this;
  }

  public ViewWriteSession init() {
    if (writeLayer == null) writeLayer = viewRef.getViewCollection().getTopPriorityLayer();
    this.session = writeLayer.getStorage().newWriteSession(options);
    return this;
  }
  /**
   * Executes write.
   *
   * @param writeRequest
   * @return
   */
  @Override
  public @NotNull Response execute(@NotNull Request writeRequest) {
    if (writeRequest instanceof WriteRequest) {
      for (Write write : ((WriteRequest) writeRequest).getWrites()) {
        write.setCollectionId(writeLayer.getCollectionId());
      }
    }
    return this.session.execute(writeRequest);
  }

  public void commit() {
    getSession().commit();
  }

  public void rollback() {
    getSession().rollback();
  }

  public void close() {
    super.close();
    getSession().close();
  }

  private IWriteSession getSession() {
    if (this.session == null) init();
    return this.session;
  }
}
