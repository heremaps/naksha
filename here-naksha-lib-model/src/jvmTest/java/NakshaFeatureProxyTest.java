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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kotlin.reflect.full.IllegalCallableAccessException;
import naksha.base.Platform;
import naksha.geo.PointCoord;
import naksha.geo.SpPoint;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Test;

class NakshaFeatureProxyTest {

  @Test
  void shouldAllowProxyingFeature() {
    // Given:
    NakshaFeature nakshaFeature = new NakshaFeature();
    nakshaFeature.setId("my_id");
    nakshaFeature.setGeometry(new SpPoint(new PointCoord(10, 20)));

    // When:
    CustomFeature proxiedFeature = nakshaFeature.proxy(Platform.klassOf(CustomFeature.class));

    // Then:
    assertEquals(nakshaFeature.getId(), proxiedFeature.getId());
    assertEquals(nakshaFeature.getGeometry(), proxiedFeature.getGeometry());
  }

  @Test
  void shouldFailForProxyWithoutNonArgConstructor() {
    // Given:
    NakshaFeature nakshaFeature = new NakshaFeature();

    // Then:
    assertThrows(IllegalArgumentException.class, () -> {
      nakshaFeature.proxy(Platform.klassOf(CustomFeatureWithoutNonArgConstructor.class));
    });
  }

  @Test
  void shouldFailForNonPublicProxy() {
    // Given:
    NakshaFeature nakshaFeature = new NakshaFeature();

    // Then:
    assertThrows(IllegalCallableAccessException.class, () -> {
      nakshaFeature.proxy(Platform.klassOf(NonPublicCustomFeature.class));
    });
  }

  public static class CustomFeature extends NakshaFeature {

    public CustomFeature() {}
  }

  public static class CustomFeatureWithoutNonArgConstructor extends NakshaFeature {

    public CustomFeatureWithoutNonArgConstructor(String unusedParam) {}
  }

  static class NonPublicCustomFeature extends NakshaFeature {}
}
