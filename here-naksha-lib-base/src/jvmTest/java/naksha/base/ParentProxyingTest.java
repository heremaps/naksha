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

import static naksha.base.Platform.forClass;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ParentProxyingTest {

  static class ProxyParent extends AnyObject {}

  public static class WithPublicConstructor extends ProxyParent {}

  public static class WithPrivateConstructor extends ProxyParent {
    private WithPrivateConstructor() {
      // hello from package-private
    }
  }

  public static class WithInternalConstructor extends ProxyParent {
    WithInternalConstructor() {
      // hello from package-private
    }
  }

  static class WithoutNonArgConstructor extends ProxyParent {
    WithoutNonArgConstructor(String unusedParam) {}
  }

  @Test
  void allow_WithPublicConstructor() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // When:
    var child = parent.proxy(forClass(WithPublicConstructor.class));

    // Then:
    assertNotNull(child);
    assertInstanceOf(WithPublicConstructor.class, child);
  }

  @Test
  void fail_WithoutNonArgConstructor() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // Then:
    assertThrows(NakshaException.class, () -> {
      parent.proxy(forClass(WithoutNonArgConstructor.class));
    });
  }

  @Test
  void fail_WithPrivateConstructor() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // Then:
    assertThrows(NakshaException.class, () -> {
      parent.proxy(forClass(WithPrivateConstructor.class));
    });
  }

  @Test
  void allow_WithInternalConstructor() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // When:
    var child = parent.proxy(forClass(WithInternalConstructor.class));

    // Then:
    assertNotNull(child);
    assertInstanceOf(WithInternalConstructor.class, child);
  }
}
