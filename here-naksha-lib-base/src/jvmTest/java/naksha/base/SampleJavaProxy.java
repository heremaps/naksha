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
package naksha.base;

import java.util.UUID;

class SampleJavaProxy extends PAnyMap {

  static final UUID DEFAULT_VERSION = UUID.randomUUID();

  private static final NotNullProperty<SampleJavaProxy, String> ID =
      JvmPropertyUtil.notNullProperty(String.class, "id");
  private static final NullableProperty<SampleJavaProxy, String> TITLE =
      JvmPropertyUtil.nullableProperty(String.class, "title");
  private static final NotNullProperty<SampleJavaProxy, UUID> VERSION =
      JvmPropertyUtil.notNullProperty(UUID.class, "version", (proxy, name) -> DEFAULT_VERSION);

  public String getId() {
    return ID.getValue(this);
  }

  public void setId(String id) {
    ID.setValue(this, id);
  }

  public String getTitle() {
    return TITLE.getValue(this);
  }

  public void setTitle(String title) {
    TITLE.setValue(this, title);
  }

  public UUID getVersion() {
    return VERSION.getValue(this);
  }

  public void setVersion(UUID version) {
    VERSION.setValue(this, version);
  }
}
