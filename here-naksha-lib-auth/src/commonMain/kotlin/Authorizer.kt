package com.here.naksha.lib.base

object Authorizer {

    fun canAccess(
        urm: UserRightsMatrix,
        arm: AccessRightsMatrix,
        service: String,
        action: String // do we want it here, or do we expect URM to have single action?
    ): Boolean {
        val userAttributes = urm.getAccessMatrixForService(service)
            ?.let { serviceMatrix -> serviceMatrix.getAttributesForAction(action) }
            ?: throw IllegalArgumentException("Undefined matrix for service / action")
        val resourceAttributes = arm.getAttributesForAction(action) ?: throw IllegalArgumentException("Undefined resource matrix")
        return userAttributes.any { userAttributeMap ->
            resourceAttributes.any { resourceAttributeMap ->
                resourceAttributeMap.matches(userAttributeMap)
            }
        }
    }

    private fun AccessAttributeMap.matches(userAttributeMap: UserAttributeMap): Boolean = TODO()
}