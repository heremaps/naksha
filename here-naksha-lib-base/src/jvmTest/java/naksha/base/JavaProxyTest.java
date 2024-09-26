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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kotlin.reflect.full.IllegalCallableAccessException;
import org.junit.jupiter.api.Test;

class JavaProxyTest {

  @Test
  void shouldAllowProxyingInJava() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // When:
    var child = parent.proxy(Platform.klassOf(ProxyChild.class));

    // Then:
    assertNotNull(child);
    assertInstanceOf(ProxyChild.class, child);
  }

  @Test
  void shouldFailForProxyWithoutNonArgConstructor() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // Then:
    assertThrows(IllegalArgumentException.class, () -> {
      parent.proxy(Platform.klassOf(ProxyChildWithoutNonArgConstructor.class));
    });
  }

  @Test
  void shouldFailForProxyWithUnsifficientVisibility() {
    // Given:
    ProxyParent parent = new ProxyParent();

    // Then:
    assertThrows(IllegalCallableAccessException.class, () -> {
      parent.proxy(Platform.klassOf(ProxyChildWithoutPublicConstructor.class));
    });
  }

  static class ProxyParent extends AnyObject {}

  public static class ProxyChild extends ProxyParent {}

  public static class ProxyChildWithoutPublicConstructor extends ProxyParent {
    ProxyChildWithoutPublicConstructor() {
      // hello from package-private
    }
  }

  static class ProxyChildWithoutNonArgConstructor extends ProxyParent {
    ProxyChildWithoutNonArgConstructor(String unusedParam) {}
  }
}
