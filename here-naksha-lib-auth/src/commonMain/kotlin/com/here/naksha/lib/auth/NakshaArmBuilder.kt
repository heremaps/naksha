package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.AccessAction
import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.XyzFeatureAttributes
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix

/**
 * Thin wrapper around ARM to allow building matrix for single service - 'naksha'
 */
class NakshaArmBuilder {

    private val nakshaService = AccessServiceMatrix()

    fun withAction(action: AccessAction<*>): NakshaArmBuilder =
        apply { nakshaService.withAction(action) }

    fun buildArm(): AccessRightsMatrix =
        AccessRightsMatrix().withService(NAKSHA_SERVICE_NAME, nakshaService)

    companion object {
        const val NAKSHA_SERVICE_NAME = "naksha"
    }
}

fun main() {
    val urm: UserRightsMatrix = TODO()
    val arm = NakshaArmBuilder()
        .withAction(
            ReadFeatures()
                .withAttributes(
                    XyzFeatureAttributes()
                        .storageId("some_storga")
                        .storageTags(listOf("t1"))
                        .id("f1"),
                    XyzFeatureAttributes()
                        .storageId("some_storga_23")
                        .storageTags(listOf("t1"))
                        .id("f1")
                        .customAttribute("2", 1)
                )
        )
//    urm.matches(arm.getArm())
    /* TODO (Hiren call)
    2) suagr functions for core types & attributes (Storage, etc)
    3)
    storage: Storage // core
    storageAttrs = attributesFor(storage)
        .withSpaceId(spaceId)


     */

}
