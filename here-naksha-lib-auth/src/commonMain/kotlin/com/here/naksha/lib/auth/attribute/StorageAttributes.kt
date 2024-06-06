package com.here.naksha.lib.auth.attribute

class StorageAttributes() : NakshaAttributes<StorageAttributes>() {

    constructor(className: String) : this() {
        set(CLASS_NAME_KEY, className)
    }

    constructor(className: String, spaceId: String) : this() {
        set(CLASS_NAME_KEY, className)
        set(SPACE_ID_KEY, spaceId)
    }

    fun className(className: String) = apply { set(CLASS_NAME_KEY, className) }

    fun spaceId(spaceId: String) = apply { set(SPACE_ID_KEY, spaceId) }

    companion object {
        const val CLASS_NAME_KEY = "className"
        const val SPACE_ID_KEY = "spaceId"
    }
}

/**

 val storage: Storage // from core
 val attributeMap = StorageAttributes.for(storage)

 conclusion:
 - let it be in Storage (same for other T:XyzFeature)
 - lib-auth does not depend on core, the other way around

 */

