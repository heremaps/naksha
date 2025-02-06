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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @see SampleJavaProxy definition
 */
class JavaProxyTest {

  @Test
  void shouldProperlyReadAndWriteFields() {
    // Given
    String id = "sample_id";
    String title = "random_title";
    UUID version = UUID.randomUUID();

    // When
    SampleJavaProxy javaProxy = new SampleJavaProxy();
    javaProxy.setId(id);
    javaProxy.setTitle(title);
    javaProxy.setVersion(version);

    // Then
    assertEquals(id, javaProxy.getId());
    assertEquals(title, javaProxy.getTitle());
    assertEquals(version, javaProxy.getVersion());
  }

  @Test
  void shouldReturnDefaultObjectForMissingNotNullableProperty() {
    // Given: proxy without id set
    SampleJavaProxy javaProxy = new SampleJavaProxy();

    // When: obtaining id
    String id = javaProxy.getId();

    // Then: default value for String type ("") is returned
    assertEquals("", id);
  }

  @Test
  void shouldReturnCalculatedValueByDefault() {
    // Given: proxy without uuid set (which has own initializer)
    SampleJavaProxy javaProxy = new SampleJavaProxy();

    // When: obtaining uuid
    UUID version = javaProxy.getVersion();

    // Then
    assertEquals(SampleJavaProxy.DEFAULT_VERSION, version);
  }
}
