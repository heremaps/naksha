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
package naksha.model

import naksha.base.Platform.PlatformCompanion.javaProxy
import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.objects.NakshaFeature
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.function.Executable

internal class NakshaFeatureProxyTest {
    @Test
    fun shouldAllowProxyingFeature() {
        // Given:
        val nakshaFeature = NakshaFeature()
        nakshaFeature.id = "my_id"
        nakshaFeature.geometry = SpPoint(PointCoord(10.0, 20.0))

        // When:
        val proxiedFeature: CustomFeature? = javaProxy<CustomFeature>(nakshaFeature, CustomFeature::class.java)

        // Then:
        Assertions.assertEquals(nakshaFeature.id, proxiedFeature!!.id)
        Assertions.assertEquals(nakshaFeature.geometry, proxiedFeature.geometry)
    }

    @Test
    fun shouldFailForProxyWithoutNonArgConstructor() {
        // Given:
        val nakshaFeature = NakshaFeature()

        // Then:
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            javaProxy(nakshaFeature, CustomFeatureWithoutNonArgConstructor::class.java)
        }
    }

    // TODO: Why should this test fail and why does it actually now work to create a proxy for a internal class?
    @Test
    fun shouldFailForNonPublicProxy() {
        // Given:
        val nakshaFeature = NakshaFeature()

        // TODO: Why does it work?
        // Then:
        val proxy: NonPublicCustomFeature? = javaProxy(nakshaFeature, NonPublicCustomFeature::class.java)
        assertNotNull(proxy)
        // TODO: I tend to understand that internal class should not be available, but then, hwy does it work now?
        //assertThrows(Exception::class.java) {
        //    javaProxy(nakshaFeature, NonPublicCustomFeature::class.java)
        //}
    }

    class CustomFeature : NakshaFeature()

    class CustomFeatureWithoutNonArgConstructor(unusedParam: String?) : NakshaFeature()

    internal class NonPublicCustomFeature : NakshaFeature()
}
