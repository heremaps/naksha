package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.FeatureAttributes
import com.here.naksha.lib.base.Platform
import com.here.naksha.lib.base.PlatformMap
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class AccessServiceMatrixTest {

    @Test
    fun shouldAddAttributesToAnAction() {
        val raw = Platform.fromJSON(
            """{
  "naksha": {
    "readFeatures": [
      {"id":"id_123"},
      {"storageId":"storage_1"}
    ]
  }
}"""
        )
        val urm = (raw as PlatformMap).proxy(UserRightsMatrix::class)

        // Then:
        val arm = AccessRightsMatrix()
        val nakshaService = arm.naksha()
        assertSame(nakshaService, arm.naksha())
        val readFeatures = nakshaService.readFeatures()
        readFeatures.add(FeatureAttributes().id("id_123").storageId("storage_1"))
        urm.matches(arm)
    }

    @Test
    fun shouldMergeActionsProperly() {
        // Given:
        val leftService = AccessServiceMatrix()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        FeatureAttributes()
                            .storageId("s_1")
                    )
            )

        // And:
        val rightService = AccessServiceMatrix()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        FeatureAttributes().storageId("s_2").collectionId("c_2")
                    )
            )

        // When:
        leftService.mergeActionsFrom(rightService)

        // Then:
        assertNotNull(leftService)
    }
}