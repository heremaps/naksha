package com.here.naksha.lib.auth.attribute

class StorageAttributes(vararg args: Any) : CommonAttributes<StorageAttributes>(*args) {

    constructor(className: String) : this(*arrayOf(CLASS_NAME_KEY, className))

    constructor(className: String, spaceId: String) : this(CLASS_NAME_KEY, className, SPACE_ID_KEY, spaceId)

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

